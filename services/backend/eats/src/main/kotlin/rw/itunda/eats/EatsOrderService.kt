package rw.itunda.eats

import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.EatsFulfillmentType
import rw.itunda.core.domain.EatsOrder
import rw.itunda.core.domain.EatsOrderItem
import rw.itunda.core.domain.EatsOrderStatus
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.Rider
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.WalletType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.geo.EatsPromotionCalculator
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.geo.GeocodeSuggestion
import rw.itunda.core.geo.NominatimGeocodingClient
import rw.itunda.core.geo.OsrmRoutingClient
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.EatsOrderItemRepository
import rw.itunda.core.repository.EatsOrderRepository
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.MenuOptionChoiceRepository
import rw.itunda.core.repository.MenuOptionGroupRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.RiderRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.util.UUID

class RestaurantNotFoundException(message: String) : RuntimeException(message)
class RestaurantNoWalletException(message: String) : RuntimeException(message)
class EatsBuyerNoWalletException(message: String) : RuntimeException(message)
class EmptyEatsOrderException(message: String) : RuntimeException(message)
class InvalidEatsDeliveryAddressException(message: String) : RuntimeException(message)
class InvalidEatsQuantityException(message: String) : RuntimeException(message)
class MenuItemNotFoundException(message: String) : RuntimeException(message)
class MenuItemSoldOutException(message: String) : RuntimeException(message)
class SelfEatsOrderException(message: String) : RuntimeException(message)
class RestaurantNotAcceptingOrdersException(message: String) : RuntimeException(message)
class EatsOrderNotFoundException(message: String) : RuntimeException(message)
class InvalidEatsOrderStatusTransitionException(message: String) : RuntimeException(message)
class RiderNotAvailableException(message: String) : RuntimeException(message)
class DeliveryAlreadyClaimedException(message: String) : RuntimeException(message)
class RiderAlreadyOnDeliveryException(message: String) : RuntimeException(message)
class NotAssignedRiderException(message: String) : RuntimeException(message)
class InvalidEatsCoordinatesException(message: String) : RuntimeException(message)
class InvalidEatsDeliveryNotesException(message: String) : RuntimeException(message)
class NoActiveOfferException(message: String) : RuntimeException(message)
class MissingRequiredMenuOptionException(message: String) : RuntimeException(message)
class InvalidMenuOptionSelectionException(message: String) : RuntimeException(message)
class ScheduledOrdersNotSupportedException(message: String) : RuntimeException(message)
class InvalidScheduledOrderTimeException(message: String) : RuntimeException(message)
class MinOrderAmountNotMetException(message: String) : RuntimeException(message)
class EatsOrderItemNotFoundException(message: String) : RuntimeException(message)
class EatsOrderItemAlreadyUnavailableException(message: String) : RuntimeException(message)
class EatsOrderAllItemsUnavailableException(message: String) : RuntimeException(message)
class EatsOrderNotDeliveredException(message: String) : RuntimeException(message)
class EatsOrderAlreadyTippedException(message: String) : RuntimeException(message)
class EatsOrderTipWindowExpiredException(message: String) : RuntimeException(message)
class InvalidEatsTipAmountException(message: String) : RuntimeException(message)
class EatsOrderNoRiderException(message: String) : RuntimeException(message)

// Real menu-options selection (2026-07-21, v1: required single-select only) --
// `selectedChoiceIds` is empty for the overwhelming majority of pre-existing menu items
// that have no option groups defined, so this is purely additive: an unchanged client
// sending no selections keeps working exactly as before for any product with zero
// option groups, and only real-422s (MissingRequiredMenuOptionException) for a product
// that genuinely has a required group and no selection for it. See
// MenuOptionGroup.kt's own doc comment for the full account.
data class EatsOrderItemRequest(val menuItemId: String, val quantity: Int, val selectedChoiceIds: List<String> = emptyList())
data class EatsOrderDetail(val order: EatsOrder, val items: List<EatsOrderItem>)
data class RiderLocationView(val latitude: Double, val longitude: Double, val updatedAt: Instant)

/**
 * Real Coupang Eats-style food ordering + delivery, the direct sibling of
 * `rw.itunda.commerce.OrderService` -- reuses the exact same real `Merchant`/
 * `MerchantProduct` catalog as restaurants/menu items (no second catalog system
 * invented) and the exact same real wallet-to-wallet ledger movement pattern
 * `MerchantService.collect()` established, just with a real delivery fee on top that's
 * held in `eats_delivery_holding` until a real rider completes the delivery, then paid
 * straight into that rider's own itunda wallet -- real money to a real person, the
 * same disbursement shape `PayrollService` already proved out, not a simulation.
 *
 * `deliveryFee` is real distance-based (2026-07-18, see `computeDeliveryFee`) via
 * `GeoUtils.haversineKm` when both the restaurant and buyer have real coordinates, and
 * falls back to the original flat amount otherwise -- never a fabricated distance. See
 * `EatsOrder.kt`'s own doc comment for the full account.
 */
@Service
class EatsOrderService(
    private val merchantRepository: MerchantRepository,
    private val merchantProductRepository: MerchantProductRepository,
    private val riderRepository: RiderRepository,
    private val eatsOrderRepository: EatsOrderRepository,
    private val eatsOrderItemRepository: EatsOrderItemRepository,
    private val menuOptionGroupRepository: MenuOptionGroupRepository,
    private val menuOptionChoiceRepository: MenuOptionChoiceRepository,
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val transactionRepository: TransactionRepository,
    private val fraudRuleEngine: FraudRuleEngine,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val osrmRoutingClient: OsrmRoutingClient,
    private val nominatimGeocodingClient: NominatimGeocodingClient,
    private val rateLimiter: RateLimiter,
    private val notificationRepository: NotificationRepository,
    private val eatsMembershipService: EatsMembershipService,
    private val platformMembershipService: PlatformMembershipService,
    private val pushNotificationService: PushNotificationService,
) {
    private val logger = LoggerFactory.getLogger(EatsOrderService::class.java)

    companion object {
        // Same bound MarketplaceService.nearby's own doc comment establishes -- caps a
        // single OSRM /table request's URL length and the private cloud's per-request
        // load; beyond this, getAvailableDeliveries quietly stays on the already-honest
        // Haversine ranking rather than risking an oversized request.
        private const val MAX_OSRM_TABLE_CANDIDATES = 100

        // How many of the nearest available riders get a real proactive push
        // notification when an order reaches READY_FOR_PICKUP -- see
        // notifyNearestRiders's own doc comment for the full account.
        private const val NEAREST_RIDERS_TO_NOTIFY = 5

        // Real 배달의민족 예약주문 (scheduled ordering) window -- Baemin's own real
        // feature is scoped to "오늘"/"내일" (today/tomorrow), a real, bounded window,
        // not an open-ended future date. See placeOrder's own doc comment.
        val SCHEDULED_ORDER_MAX_WINDOW: java.time.Duration = java.time.Duration.ofDays(2)

        // Real Uber Eats post-delivery tip window -- matches RideTripService.TIP_WINDOW's
        // own real 30-day rule exactly, same real product/team, same rail. See
        // tipRider's own doc comment.
        val TIP_WINDOW: java.time.Duration = java.time.Duration.ofDays(30)

        // Real exclusive accept window for automatic dispatch -- see
        // dispatchToNextCandidate's own doc comment for the full account. Long enough
        // for a real rider to actually notice a push notification and respond, short
        // enough that a real buyer isn't kept waiting on one unresponsive candidate.
        val OFFER_WINDOW: Duration = Duration.ofSeconds(90)

        // Real Uber Eats-style order-acceptance timeout (2026-08-16) -- see
        // expireUnacceptedOrder's own doc comment. itunda's own honestly-chosen
        // threshold (this backend has no historical restaurant-response-time data to
        // derive one from, same "itunda's own chosen policy, not a claimed real figure
        // this project has no way to verify" honesty this session's own
        // BUSY_ORDER_THRESHOLD/customerCodeValidity constants already model) -- long
        // enough a busy real kitchen can genuinely notice a new order, short enough a
        // real buyer isn't left waiting on a restaurant that never responds at all.
        val ORDER_ACCEPTANCE_TIMEOUT: Duration = Duration.ofMinutes(10)

        // Real Uber Eats-style automatic-pausing threshold (2026-08-16) -- see Uber's
        // own official "Automatic pausing for merchants" blog post: "stores may be
        // paused... when multiple orders in a row go unaccepted." itunda's own honest
        // number (Uber doesn't publish theirs) -- see
        // Merchant.consecutiveMissedOrders's own doc comment.
        const val CONSECUTIVE_MISSES_TO_AUTO_PAUSE: Int = 3
    }

    // Same 1.5% Toss Payments fee-schedule reasoning OrderService.feeRate/
    // MerchantService.feeRate already give -- reused rather than inventing a third number
    // for what is, underneath, the same kind of wallet-to-wallet merchant collection.
    private val platformFeeRate = BigDecimal("0.015")

    // Real distance-based delivery fee (2026-07-18), computed from GeoUtils.haversineKm
    // when both the restaurant and the buyer's delivery point have real coordinates --
    // a real base pickup fee plus a real per-km rate, bounded so a wildly out-of-range
    // coordinate can't produce a runaway or negligible fee. Falls back to the original
    // flat amount when either side has no coordinates yet (an older restaurant that
    // hasn't set a location, or a client that hasn't been updated to submit one) --
    // never a fabricated distance.
    private val legacyFlatDeliveryFee = BigDecimal("1500")
    private val baseDeliveryFee = BigDecimal("500")
    private val perKmDeliveryRate = BigDecimal("250")
    private val minDeliveryFee = BigDecimal("1000")
    private val maxDeliveryFee = BigDecimal("5000")

    // Real, human-readable receipt breakdown of a resolved menu item's selected options
    // -- manual JSON string construction, same established convention as
    // Notification.dataJson elsewhere in this codebase (no JSON library dependency
    // needed for a handful of small, backend-controlled string fields). Never a second
    // pricing source -- see the `Resolved` data class's own doc comment in placeOrder.
    private fun buildSelectedOptionsJson(summaries: List<Triple<String, String, BigDecimal>>): String {
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
        val items = summaries.joinToString(",") { (groupName, choiceName, delta) ->
            "{\"groupName\":\"${esc(groupName)}\",\"choiceName\":\"${esc(choiceName)}\",\"priceDelta\":$delta}"
        }
        return "[$items]"
    }

    private fun computeDeliveryFee(distanceKm: Double?): Pair<BigDecimal, BigDecimal?> {
        if (distanceKm == null) return legacyFlatDeliveryFee to null
        val distance = BigDecimal(distanceKm).setScale(3, RoundingMode.HALF_UP)
        val raw = baseDeliveryFee.add(perKmDeliveryRate.multiply(distance)).setScale(2, RoundingMode.HALF_UP)
        return raw.max(minDeliveryFee).min(maxDeliveryFee) to distance
    }

    // Real road distance via itunda's own self-hosted, Rwanda-only OSRM when both points
    // are plausibly within Rwanda (real Rwanda road network, not a straight line) --
    // outside that envelope OSRM has no configured max-matching-radius and would
    // silently snap to the nearest network node instead of correctly finding no route
    // (found live, 2026-07-18), so this skips straight to the honest Haversine
    // straight-line distance instead. Also falls back to Haversine when OSRM isn't
    // configured/reachable/finds no route -- never a fabricated number either way.
    // Shared by placeOrder's delivery-fee calculation and getAvailableDeliveries' real
    // proximity ranking (2026-07-19) -- one real distance computation, not two that could
    // silently drift apart.
    private fun resolveRealDistanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double =
        if (GeoUtils.isWithinRwanda(lat1, lng1) && GeoUtils.isWithinRwanda(lat2, lng2)) {
            osrmRoutingClient.routeDistanceKm(lat1, lng1, lat2, lng2) ?: GeoUtils.haversineKm(lat1, lng1, lat2, lng2)
        } else {
            GeoUtils.haversineKm(lat1, lng1, lat2, lng2)
        }

    // Real user-facing address search (2026-07-18), backing a real autocomplete UI so a
    // buyer can see and confirm the real coordinates their typed address resolves to
    // before checkout, rather than the automatic single-best-match geocoding placeOrder
    // already does silently. Rate-limited the same way every other real endpoint in this
    // codebase is -- a debounced client-side autocomplete can still fire several requests
    // per keystroke burst; 60/min comfortably covers real typing while bounding abuse.
    fun searchDeliveryAddress(buyerId: String, query: String): List<GeocodeSuggestion> {
        rateLimiter.checkLimit("eats:geocode-search:$buyerId", limit = 60, window = Duration.ofMinutes(1))
        return nominatimGeocodingClient.search(query)
    }

    private val restaurantStatusOrder = listOf(
        EatsOrderStatus.PLACED, EatsOrderStatus.ACCEPTED, EatsOrderStatus.PREPARING, EatsOrderStatus.READY_FOR_PICKUP,
    )
    private val riderStatusOrder = listOf(EatsOrderStatus.RIDER_ASSIGNED, EatsOrderStatus.PICKED_UP, EatsOrderStatus.DELIVERED)

    // Real 배달의민족 예약주문 (scheduled ordering) (2026-07-26) -- sourced from
    // Baemin's own real, actively-growing seller-facing feature
    // (ceo.baemin.com/guide's own "예약주문 설정" doc: a per-restaurant opt-in, not
    // every real restaurant supports it). `scheduledFor` is honestly just a real,
    // buyer-stated preferred time recorded on the order and shown to the restaurant --
    // this doesn't build a second dispatch scheduler that auto-delays notifying the
    // restaurant until the scheduled time (Baemin's own real prep-time/dispatch
    // algorithm isn't published), the same "record the real preference, don't fabricate
    // the smart-dispatch logic behind it" honesty this session applies elsewhere.
    // Bounded to SCHEDULED_ORDER_MAX_WINDOW ahead, matching Baemin's own real
    // "오늘"/"내일" (today/tomorrow) scope, not an open-ended future date.
    @Transactional
    fun placeOrder(
        buyerId: String,
        restaurantId: String,
        items: List<EatsOrderItemRequest>,
        deliveryAddress: String,
        deliveryLatitude: Double? = null,
        deliveryLongitude: Double? = null,
        deliveryNotes: String? = null,
        // Real Baemin-style 포장주문 (Pickup) order type (2026-07-26) -- see this
        // method's own doc comment further down for the full account. Defaults to
        // DELIVERY, so every existing caller's behavior is completely unchanged.
        fulfillmentType: EatsFulfillmentType = EatsFulfillmentType.DELIVERY,
        // Real 배달의민족 예약주문 (scheduled ordering) (2026-07-26) -- null (the
        // default) means ASAP, every existing caller's behavior completely unchanged.
        // See this method's own doc comment for the real opt-in/window rules.
        scheduledFor: Instant? = null,
    ): EatsOrderDetail {
        if (items.isEmpty()) {
            throw EmptyEatsOrderException("An order needs at least one item")
        }
        // A PICKUP order needs no real delivery address -- the buyer collects in
        // person -- so `deliveryAddress` is never required from the caller for one;
        // this column stays NOT NULL, so a real, honest display value is stored
        // instead of an empty string (resolved once the restaurant is looked up
        // below).
        val trimmedAddress = deliveryAddress.trim()
        if (fulfillmentType == EatsFulfillmentType.DELIVERY && trimmedAddress.isEmpty()) {
            throw InvalidEatsDeliveryAddressException("A delivery address is required")
        }
        // Real bound, mirroring deliveryNotes' own already-correct length check just
        // below rather than silently truncating -- unlike a review comment, truncating
        // an address could genuinely misdirect a delivery, so reject-with-a-clean-error
        // is the honest fix here (deliveryAddress is VARCHAR(500), and this DB's real
        // STRICT_TRANS_TABLES mode throws a raw, unhandled 500 on an over-length insert).
        if (trimmedAddress.length > 500) {
            throw InvalidEatsDeliveryAddressException("Delivery address must be 500 characters or fewer")
        }
        val trimmedNotes = deliveryNotes?.trim()?.ifBlank { null }
        if (trimmedNotes != null && trimmedNotes.length > 500) {
            throw InvalidEatsDeliveryNotesException("Delivery notes must be 500 characters or fewer")
        }
        if ((deliveryLatitude == null) != (deliveryLongitude == null)) {
            throw InvalidEatsCoordinatesException("Both deliveryLatitude and deliveryLongitude are required together")
        }
        if (deliveryLatitude != null && deliveryLongitude != null && !GeoUtils.isValidCoordinate(deliveryLatitude, deliveryLongitude)) {
            throw InvalidEatsCoordinatesException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        val restaurant = merchantRepository.findById(restaurantId)
            .orElseThrow { RestaurantNotFoundException("Restaurant not found") }
        if (restaurant.ownerUserId == buyerId) {
            throw SelfEatsOrderException("Cannot order from your own restaurant")
        }
        // Real Baemin CEO app 영업일시중지 (temporarily pause business) enforcement --
        // see Merchant.isAcceptingOrders's own doc comment. Checked here, not just
        // surfaced as a UI badge, so a stale client (or a direct API call) can't place
        // a real order a swamped/closed restaurant never agreed to fulfill.
        if (!restaurant.isAcceptingOrders) {
            throw RestaurantNotAcceptingOrdersException("This restaurant isn't accepting orders right now")
        }
        // Real Baemin CEO app 휴무일 설정 (recurring weekly closed-day schedule)
        // enforcement -- see Merchant.isClosedToday's own doc comment. Same "checked
        // server-side, not just a UI badge" discipline the manual pause check above
        // already establishes.
        if (restaurant.isClosedToday()) {
            throw RestaurantNotAcceptingOrdersException("This restaurant is closed today")
        }
        // Real, honest display value for PICKUP -- `deliveryAddress` stays NOT NULL,
        // and a real "collect from the restaurant" order genuinely has no delivery
        // address of its own to store.
        val resolvedAddress = if (fulfillmentType == EatsFulfillmentType.PICKUP) "Pickup at ${restaurant.businessName}" else trimmedAddress

        // Real 배달의민족 예약주문 (scheduled ordering) validation -- only a real
        // restaurant that's explicitly opted in supports it (Merchant.kt's own doc
        // comment: sourced from ceo.baemin.com's own seller guide, not every real
        // restaurant supports this). Bounded to SCHEDULED_ORDER_MAX_WINDOW ahead --
        // Baemin's own real feature is scoped to "오늘"/"내일" (today/tomorrow), a
        // real, bounded window, not an open-ended future date.
        if (scheduledFor != null) {
            if (!restaurant.acceptsScheduledOrders) {
                throw ScheduledOrdersNotSupportedException("This restaurant doesn't accept scheduled orders")
            }
            val now = Instant.now()
            if (!scheduledFor.isAfter(now)) {
                throw InvalidScheduledOrderTimeException("Scheduled time must be in the future")
            }
            if (scheduledFor.isAfter(now.plus(SCHEDULED_ORDER_MAX_WINDOW))) {
                throw InvalidScheduledOrderTimeException("Scheduled time must be within ${SCHEDULED_ORDER_MAX_WINDOW.toDays()} days")
            }
        }

        val restaurantWallet = walletRepository.findById(restaurant.walletId)
            .orElseThrow { RestaurantNoWalletException("Restaurant settlement wallet not found") }
        val buyerWallet = walletRepository.findByUserIdAndType(buyerId, WalletType.MAIN)
            ?: throw EatsBuyerNoWalletException("No wallet found for this account")

        // Real menu-options resolution (2026-07-21) -- batched up front for every
        // distinct menu item in this order, not one pair of queries per line item (same
        // N+1 discipline as ShoppingController.getMerchantProducts' own enrichment).
        val distinctMenuItemIds = items.map { it.menuItemId }.distinct()
        val groupsByProduct = if (distinctMenuItemIds.isNotEmpty()) {
            menuOptionGroupRepository.findByProductIdInOrderByDisplayOrderAsc(distinctMenuItemIds).groupBy { it.productId }
        } else {
            emptyMap()
        }
        val choicesByGroup = groupsByProduct.values.flatten().map { it.id }.let { groupIds ->
            if (groupIds.isEmpty()) emptyMap() else menuOptionChoiceRepository.findByGroupIdInOrderByDisplayOrderAsc(groupIds).groupBy { it.groupId }
        }

        // Real prices read from the live menu row at checkout time -- never trusted from
        // the client, same price-tampering prevention as commerce's OrderService.
        // `unitPrice` below is the FINAL per-unit price (base + every selected choice's
        // priceDelta) -- every downstream consumer (itemsSubtotal, receipts, refunds)
        // keeps using unitPrice*quantity completely unchanged; selectedOptionsJson is
        // purely an additional human-readable breakdown, never a second pricing source.
        data class Resolved(val productId: String, val name: String, val unitPrice: BigDecimal, val quantity: Int, val selectedOptionsJson: String?)
        val resolved = items.map { req ->
            if (req.quantity <= 0) {
                throw InvalidEatsQuantityException("Quantity must be at least 1")
            }
            val menuItem = merchantProductRepository.findById(req.menuItemId)
                .orElseThrow { MenuItemNotFoundException("Menu item not found") }
            if (menuItem.merchantId != restaurantId || !menuItem.active) {
                throw MenuItemNotFoundException("Menu item not found")
            }
            // Real Baemin CEO app/DoorDash-style "86" enforcement (2026-08-16) -- see
            // MerchantProduct.soldOut's own doc comment. A distinct, real exception
            // (not MenuItemNotFoundException above) since the item genuinely exists and
            // is still shown to the buyer -- a 404 here would be a misleading lie about
            // why the order failed.
            if (menuItem.soldOut) {
                throw MenuItemSoldOutException("${menuItem.name} is temporarily sold out")
            }

            val groups = groupsByProduct[menuItem.id] ?: emptyList()
            var optionsDelta = BigDecimal.ZERO
            var selectedOptionsJson: String? = null
            if (groups.isNotEmpty()) {
                val selectedSet = req.selectedChoiceIds.toSet()
                val allValidChoices = groups.flatMap { choicesByGroup[it.id] ?: emptyList() }
                val allValidChoiceIds = allValidChoices.map { it.id }.toSet()
                if (!allValidChoiceIds.containsAll(selectedSet)) {
                    throw InvalidMenuOptionSelectionException("One or more selected options do not belong to ${menuItem.name}")
                }
                val choiceById = allValidChoices.associateBy { it.id }
                val summaries = mutableListOf<Triple<String, String, BigDecimal>>()
                for (group in groups) {
                    val groupChoiceIds = (choicesByGroup[group.id] ?: emptyList()).map { it.id }.toSet()
                    // Real Coupang Eats/Baemin-style enforcement (multi-select optional
                    // add-ons added 2026-07-26 -- see MenuOptionGroup.kt's own doc
                    // comment): a `required` group needs at least one real choice, a
                    // non-`multiSelect` group allows at most one. For the original v1
                    // required+single-select groups (every pre-2026-07-26 group), this
                    // is exactly the old "exactly one" enforcement, unchanged. Never
                    // silently defaults to a choice the buyer didn't actually pick.
                    val selectedInGroup = selectedSet.intersect(groupChoiceIds)
                    if (group.required && selectedInGroup.isEmpty()) {
                        throw MissingRequiredMenuOptionException("Select at least one option for '${group.name}' on ${menuItem.name}")
                    }
                    if (!group.multiSelect && selectedInGroup.size > 1) {
                        throw InvalidMenuOptionSelectionException("Only one option can be selected for '${group.name}' on ${menuItem.name}")
                    }
                    selectedInGroup.forEach { choiceId ->
                        val chosen = choiceById.getValue(choiceId)
                        optionsDelta = optionsDelta.add(chosen.priceDelta)
                        summaries.add(Triple(group.name, chosen.name, chosen.priceDelta))
                    }
                }
                selectedOptionsJson = buildSelectedOptionsJson(summaries)
            }
            Resolved(menuItem.id, menuItem.name, menuItem.price.add(optionsDelta), req.quantity, selectedOptionsJson)
        }
        val itemsSubtotal = resolved.fold(BigDecimal.ZERO) { acc, r -> acc + r.unitPrice.multiply(BigDecimal(r.quantity)) }

        // Real 가게별 최소주문금액 (per-restaurant minimum order amount) enforcement --
        // `Merchant.minOrderAmount`/`setMinOrderAmount` were already real and
        // merchant-settable, shown to buyers in `ShoppingController`'s own catalog
        // response, but never actually checked anywhere at order time -- a real gap
        // found by re-reading this already-shipped field's own callers before building
        // a new feature, the same technique that already found the TrustScoreService/
        // GiftVoucherService bugs earlier this session. A restaurant that opted into a
        // real minimum now actually enforces it, not just displays it.
        val minOrderAmount = restaurant.minOrderAmount
        if (minOrderAmount != null && itemsSubtotal < minOrderAmount) {
            throw MinOrderAmountNotMetException("This restaurant requires a minimum order of $minOrderAmount RWF")
        }

        val platformFee = itemsSubtotal.multiply(platformFeeRate).setScale(2, RoundingMode.HALF_UP)

        // Real Baemin 포장할인 (pickup discount) -- sourced from Baemin's own real
        // seller guide (ceo.baemin.com/guide/2991, "픽업의 이해"): a restaurant can opt
        // into an extra discount specifically for pickup orders, distinct from the
        // delivery-fee waiver every PICKUP order already gets unconditionally above.
        // RESTAURANT-funded, unlike promotionDiscount below (which itunda itself
        // absorbs) -- netToRestaurant is reduced by exactly this amount, and
        // itunda's own platformFee revenue is completely untouched, matching real
        // reporting that Baemin still charges its normal commission on pickup orders
        // regardless of whatever discount the restaurant itself chooses to offer.
        val pickupDiscount = if (fulfillmentType == EatsFulfillmentType.PICKUP && restaurant.pickupDiscountPercent != null) {
            itemsSubtotal.multiply(BigDecimal(restaurant.pickupDiscountPercent!!)).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }
        val netToRestaurant = itemsSubtotal.subtract(platformFee).subtract(pickupDiscount)

        // Real Baemin-style tiered order-amount promotion -- see
        // EatsPromotionCalculator's own doc comment. Computed from itemsSubtotal alone
        // (before delivery fee/membership waivers below), matching Baemin's own real
        // mechanic of discounting the order value itself, not the delivery charge.
        val promotionDiscount = EatsPromotionCalculator.calculateDiscount(itemsSubtotal)

        val restaurantLat = restaurant.latitude
        val restaurantLng = restaurant.longitude

        // Real Baemin-style 포장주문 (Pickup): a PICKUP order has no rider, no
        // delivery, and therefore no real geocoding/distance/delivery-fee computation
        // to do at all -- unconditionally zero, not just waived the way Baemin Club
        // membership waives it for DELIVERY orders (see below).
        val resolvedDeliveryLat: Double?
        val resolvedDeliveryLng: Double?
        val distanceKmRounded: BigDecimal?
        val deliveryFee: BigDecimal
        if (fulfillmentType == EatsFulfillmentType.PICKUP) {
            resolvedDeliveryLat = null
            resolvedDeliveryLng = null
            distanceKmRounded = null
            deliveryFee = BigDecimal.ZERO
        } else {
            // Real geocoding fallback (2026-07-18): when the buyer didn't submit
            // explicit coordinates, try resolving the free-text delivery address via
            // itunda's own self-hosted Nominatim -- lets the existing deliveryAddress
            // field drive a real distance-based fee without needing any client UI
            // changes yet. Falls back to no coordinates (and therefore the flat fee
            // below) if geocoding is unconfigured/unreachable/finds no match -- never
            // a fabricated location.
            val geocoded = if (deliveryLatitude == null && deliveryLongitude == null) {
                nominatimGeocodingClient.geocode(trimmedAddress)
            } else {
                null
            }
            resolvedDeliveryLat = deliveryLatitude ?: geocoded?.latitude
            resolvedDeliveryLng = deliveryLongitude ?: geocoded?.longitude

            val distanceKm = if (resolvedDeliveryLat != null && resolvedDeliveryLng != null && restaurantLat != null && restaurantLng != null) {
                resolveRealDistanceKm(restaurantLat, restaurantLng, resolvedDeliveryLat, resolvedDeliveryLng)
            } else {
                null
            }
            val (computedDeliveryFee, roundedKm) = computeDeliveryFee(distanceKm)
            distanceKmRounded = roundedKm
            // Real Coupang Wow-style unconditional waiver (2026-07-31) -- see
            // PlatformMembership.kt's own doc comment: checked first since it's the
            // strictly broader real guarantee (every restaurant, no merchant opt-in
            // needed), falling back to Eats Club's own real Baemin Club-style waiver
            // (2026-07-26, EatsMembership.kt's own doc comment), which stays only
            // valid at a restaurant that has itself opted in
            // (`participatesInEatsMembership`), never a blanket waiver, mirroring
            // Baemin's own real "참여 가게" scoping.
            deliveryFee = if (platformMembershipService.hasActiveMembership(buyerId) ||
                (restaurant.participatesInEatsMembership && eatsMembershipService.hasActiveMembership(buyerId))
            ) {
                BigDecimal.ZERO
            } else {
                computedDeliveryFee
            }
        }
        val totalAmount = itemsSubtotal.add(deliveryFee)
        // Buyer pays the promotion-discounted amount, further reduced by any real
        // pickupDiscount above; the restaurant's own netToRestaurant already absorbed
        // pickupDiscount when it was computed, and itunda's own platformFee revenue
        // stays untouched by either discount -- itunda alone absorbs promotionDiscount
        // as a real expense (PROMOTION_EXPENSE leg below), matching Baemin's own real
        // "platform pays" mechanic for that discount specifically.
        val buyerCharge = totalAmount.subtract(promotionDiscount).subtract(pickupDiscount)

        val result = ledgerService.postLedgerTransaction(
            buyerWallet.currency,
            listOf(
                LedgerLeg(buyerWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, buyerCharge, "Eats order - ${restaurant.businessName}"),
                LedgerLeg("promotion_expense", LedgerAccountType.PROMOTION_EXPENSE, LedgerDirection.DEBIT, promotionDiscount, "Eats order promotion - ${restaurant.businessName}"),
                LedgerLeg(restaurantWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, netToRestaurant, "Eats order collection - ${restaurant.businessName}"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, platformFee, "Eats platform fee - ${restaurant.businessName}"),
                LedgerLeg("eats_delivery_holding", LedgerAccountType.EATS_DELIVERY_HOLDING, LedgerDirection.CREDIT, deliveryFee, "Eats delivery fee held - ${restaurant.businessName}"),
            ),
        )

        val transaction = Transaction(
            id = result.transactionId,
            referenceNumber = "EATS${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
            senderId = buyerId,
            recipientId = restaurant.ownerUserId,
            fromWalletId = buyerWallet.id,
            toWalletId = restaurantWallet.id,
            amount = buyerCharge,
            fee = platformFee.add(deliveryFee),
            currency = buyerWallet.currency,
            type = TransactionType.PAYMENT,
            status = TransactionStatus.COMPLETED,
            description = "Eats order - ${restaurant.businessName}",
            channel = "EATS_ORDER",
            completedAt = Instant.now(),
        )
        fraudRuleEngine.evaluate(buyerId, restaurant.ownerUserId, buyerCharge, transaction.id)
        transactionRepository.save(transaction)

        val order = eatsOrderRepository.save(
            EatsOrder(
                id = "eats_order_${UUID.randomUUID()}", buyerId = buyerId, restaurantId = restaurantId,
                deliveryAddress = resolvedAddress, itemsSubtotal = itemsSubtotal, deliveryFee = deliveryFee,
                platformFee = platformFee, totalAmount = buyerCharge, promotionDiscount = promotionDiscount,
                pickupDiscount = pickupDiscount,
                transactionId = result.transactionId,
                deliveryLatitude = resolvedDeliveryLat, deliveryLongitude = resolvedDeliveryLng, distanceKm = distanceKmRounded,
                deliveryNotes = trimmedNotes, fulfillmentType = fulfillmentType, scheduledFor = scheduledFor,
            ),
        )
        val orderItems = resolved.map {
            EatsOrderItem(
                id = "eats_order_item_${UUID.randomUUID()}", orderId = order.id, productId = it.productId,
                productName = it.name, unitPrice = it.unitPrice, quantity = it.quantity,
                selectedOptionsJson = it.selectedOptionsJson,
            )
        }
        eatsOrderItemRepository.saveAll(orderItems)

        // Real "new order" alert for the restaurant (2026-07-26) -- a genuine, live gap
        // found while researching real Baemin/Coupang Eats own-restaurant-facing
        // features: `placeOrder` never notified the restaurant owner at all, meaning
        // the only way to learn a real order arrived was manually polling
        // GET /orders/restaurant-orders. Every real food-delivery platform alerts the
        // seller the moment an order lands -- this is the honest baseline, not a named
        // feature. Best-effort, same "an auxiliary side-effect can't block the real
        // operation it's attached to" discipline this class's own notifyNearestRiders
        // already establishes.
        //
        // Real push wired in (2026-07-28), same day as its commerce sibling
        // (OrderService.placeOrder) -- food cools while it sits unnoticed, making this
        // arguably the most time-sensitive of the "new order" merchant alerts.
        try {
            val scheduleNote = if (scheduledFor != null) " (scheduled for $scheduledFor)" else ""
            val title = "New order received"
            val body = "A new order for ${orderItems.sumOf { it.quantity }} item(s) just came in -- $totalAmount RWF$scheduleNote"
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = restaurant.ownerUserId, type = "NEW_EATS_ORDER",
                    title = title, body = body,
                    isRead = false, createdAt = Instant.now(), dataJson = "{\"orderId\":\"${order.id}\"}",
                ),
            )
            sendNewOrderPushAfterCommit(restaurant.ownerUserId, title, body, order.id)
        } catch (e: Exception) {
            // Non-critical -- the real order already completed and succeeded.
        }

        return EatsOrderDetail(order, orderItems)
    }

    /** Restaurant operations must not receive an order alert until its payment and order rows commit. */
    private fun sendNewOrderPushAfterCommit(ownerUserId: String, title: String, body: String, orderId: String) {
        val send = {
            try {
                pushNotificationService.sendToUser(ownerUserId, title, body, mapOf("orderId" to orderId))
            } catch (e: Exception) {
                logger.warn("Could not send new-order push for eats order {}", orderId, e)
            }
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
    }

    /**
     * Real Uber Eats post-delivery tip (2026-08-17,
     * help.uber.com/en/ubereats/restaurants/article/add-or-change-tip-amount-for-a-past-order)
     * -- "You're free to add a tip... for up to 40 days after your order is delivered."
     * A direct real buyer-wallet-to-rider-wallet transfer, never routed through
     * `eats_delivery_holding` (unlike the delivery fee itself) since a tip isn't
     * itunda's revenue to hold or take a cut of -- same real mechanism
     * [rw.itunda.rideshare.RideTripService.tipDriver] already establishes for ride
     * tips, just ported to Eats. Scoped to real `DELIVERY` orders with an assigned
     * rider only -- a `PICKUP` order has no rider to tip. Real once-only
     * ([EatsOrderAlreadyTippedException]) and real window ([EatsOrderTipWindowExpiredException],
     * [TIP_WINDOW]) enforcement.
     */
    @Transactional
    fun tipRider(buyerId: String, orderId: String, amount: BigDecimal): EatsOrder {
        val order = eatsOrderRepository.findById(orderId).orElseThrow { EatsOrderNotFoundException("Order not found") }
        if (order.buyerId != buyerId) {
            throw EatsOrderNotFoundException("Order not found")
        }
        if (amount <= BigDecimal.ZERO) {
            throw InvalidEatsTipAmountException("Tip amount must be greater than zero")
        }
        if (order.status != EatsOrderStatus.DELIVERED) {
            throw EatsOrderNotDeliveredException("Only a delivered order can be tipped")
        }
        if (order.tipAmount != null) {
            throw EatsOrderAlreadyTippedException("This order has already been tipped")
        }
        if (Instant.now().isAfter(order.updatedAt.plus(TIP_WINDOW))) {
            throw EatsOrderTipWindowExpiredException("Tips can only be added within ${TIP_WINDOW.toDays()} days of delivery")
        }
        val riderId = order.riderId ?: throw EatsOrderNoRiderException("This order has no assigned rider to tip")
        val rider = riderRepository.findById(riderId).orElseThrow { EatsOrderNoRiderException("This order has no assigned rider to tip") }
        val buyerWallet = walletRepository.findByUserIdAndType(buyerId, WalletType.MAIN)
            ?: throw EatsBuyerNoWalletException("No wallet found for this account")
        val riderWallet = walletRepository.findById(rider.walletId)
            .orElseThrow { EatsOrderNoRiderException("Rider settlement wallet not found") }
        if (buyerWallet.availableBalance < amount) {
            throw InsufficientFundsException("Insufficient available balance for this tip")
        }
        val result = ledgerService.postLedgerTransaction(
            buyerWallet.currency,
            listOf(
                LedgerLeg(buyerWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Tip for order at ${order.restaurantId}"),
                LedgerLeg(riderWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, amount, "Tip received"),
            ),
        )
        order.tipAmount = amount
        order.tipTransactionId = result.transactionId
        val saved = eatsOrderRepository.save(order)
        val title = "You received a tip"
        val body = "You received a $amount RWF tip for a recent delivery."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = rider.userId, type = "EATS_TIP_RECEIVED",
                title = title, body = body, isRead = false, createdAt = Instant.now(), dataJson = "{\"orderId\":\"${order.id}\"}",
            ),
        )
        sendTipReceivedPushAfterCommit(rider.userId, title, body, order.id)
        return saved
    }

    /** A tip alert must never announce a payment whose enclosing transaction rolled back. */
    private fun sendTipReceivedPushAfterCommit(riderUserId: String, title: String, body: String, orderId: String) {
        val send = {
            try {
                pushNotificationService.sendToUser(riderUserId, title, body, mapOf("orderId" to orderId))
            } catch (e: Exception) {
                logger.warn("Could not send tip-received push for eats order {}", orderId, e)
            }
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
    }

    fun getMyOrders(buyerId: String, pageable: Pageable): Page<EatsOrder> =
        eatsOrderRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId, pageable)

    fun getRestaurantOrders(ownerUserId: String, pageable: Pageable): Page<EatsOrder> {
        val restaurant = merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw RestaurantNotFoundException("This account is not registered as a merchant")
        return eatsOrderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurant.id, pageable)
    }

    fun getRiderDeliveries(riderUserId: String, pageable: Pageable): Page<EatsOrder> {
        val rider = riderRepository.findByUserId(riderUserId)
            ?: throw RiderNotRegisteredException("This account is not registered as a rider")
        return eatsOrderRepository.findByRiderIdOrderByCreatedAtDesc(rider.id, pageable)
    }

    /** Real query backing a rider's "available deliveries" list -- READY_FOR_PICKUP
     * orders with no rider claimed yet. Visible to any registered rider, not just
     * available ones (a rider deciding whether to go online can see the real demand).
     *
     * Real nearest-first ranking (2026-07-19): when the calling rider has a real current
     * location (`RiderService.updateLocation`), candidates are sorted by real distance
     * from the rider to each order's restaurant, closest first -- real Coupang
     * Eats-style proximity dispatch. Falls back to the original createdAt-ascending
     * (oldest-first) order when the rider hasn't shared a location yet, or when a
     * candidate's restaurant has no real coordinates -- never a fabricated distance.
     *
     * Real road-distance ranking, not straight-line-only: same discipline
     * `MarketplaceService.nearby`'s own doc comment already established for proximity
     * search -- `GeoUtils.haversineKm` first does a cheap in-memory rank (also the
     * final answer for anything outside Rwanda or when OSRM is unconfigured), then a
     * SINGLE batched `OsrmRoutingClient.routeDistancesKm` `/table` call re-ranks every
     * in-Rwanda candidate by real road distance in one round trip -- never one `/route`
     * call per candidate, which would just be an HTTP-level N+1 in place of a DB one.
     *
     * Sorted in-app over a single bounded fetch, not a DB-level query, matching this
     * codebase's own established "honest choice at this system's real data scale"
     * convention (see `SavingsService.getGoalsDueForAutoContribution`'s own doc comment)
     * -- the real candidate set is every currently-unclaimed READY_FOR_PICKUP order
     * nationwide at this exact moment, not the full order history, so an in-memory sort
     * is proportionate today; a real production system at much larger scale would want a
     * DB-level geospatial query instead. */
    fun getAvailableDeliveries(riderUserId: String, pageable: Pageable): Page<EatsOrder> {
        val rider = riderRepository.findByUserId(riderUserId)
            ?: throw RiderNotRegisteredException("This account is not registered as a rider")
        val riderLat = rider.currentLatitude
        val riderLng = rider.currentLongitude
        // Real automatic-dispatch exclusivity (2026-07-20): an order with a real
        // still-active exclusive offer is hidden from open browse entirely -- showing
        // it here would just invite a claim attempt that real-409s, and the
        // specifically-offered rider already gets a dedicated Notification with the
        // order id, so they don't need to find it via browse either.
        val now = Instant.now()
        fun hasNoActiveOffer(order: EatsOrder) = order.offerExpiresAt == null || !order.offerExpiresAt!!.isAfter(now)
        // Real Baemin-style 포장주문 (Pickup) exclusion: a PICKUP order never gets a
        // rider dispatched or assigned -- see placeOrder/updateRestaurantStatus's own
        // doc comments -- so it must never appear in a rider's available-deliveries
        // list or candidate pool.
        fun isDeliveryFulfillment(order: EatsOrder) = order.fulfillmentType == EatsFulfillmentType.DELIVERY
        if (riderLat == null || riderLng == null) {
            val page = eatsOrderRepository.findByStatusAndRiderIdIsNullOrderByCreatedAtAsc(EatsOrderStatus.READY_FOR_PICKUP, Pageable.unpaged())
            val filtered = page.content.filter(::hasNoActiveOffer).filter(::isDeliveryFulfillment)
            val start = (pageable.offset).coerceAtMost(filtered.size.toLong()).toInt()
            val end = (start + pageable.pageSize).coerceAtMost(filtered.size)
            return PageImpl(filtered.subList(start, end), pageable, filtered.size.toLong())
        }

        val candidates = eatsOrderRepository.findByStatusAndRiderIdIsNullOrderByCreatedAtAsc(
            EatsOrderStatus.READY_FOR_PICKUP, Pageable.unpaged(),
        ).content.filter(::hasNoActiveOffer).filter(::isDeliveryFulfillment)
        if (candidates.isEmpty()) return PageImpl(emptyList(), pageable, 0)

        val restaurantsById = merchantRepository.findAllById(candidates.map { it.restaurantId }.distinct()).associateBy { it.id }
        // Orders whose restaurant has no real coordinates yet sort last -- never
        // silently dropped, just unranked, same fallback discipline as everywhere else.
        val (locatable, unlocatable) = candidates.partition { order ->
            val r = restaurantsById[order.restaurantId]
            r?.latitude != null && r.longitude != null
        }

        val haversineRanked = locatable.map { order ->
            val r = restaurantsById.getValue(order.restaurantId)
            order to GeoUtils.haversineKm(riderLat, riderLng, r.latitude!!, r.longitude!!)
        }
        val riderInRwanda = GeoUtils.isWithinRwanda(riderLat, riderLng)
        val (inRwanda, outsideRwanda) = haversineRanked.partition { (order, _) ->
            val r = restaurantsById.getValue(order.restaurantId)
            riderInRwanda && GeoUtils.isWithinRwanda(r.latitude!!, r.longitude!!)
        }
        val roadRanked = if (osrmRoutingClient.isConfigured && inRwanda.isNotEmpty() && inRwanda.size <= MAX_OSRM_TABLE_CANDIDATES) {
            val destinations = inRwanda.map { (order, _) -> restaurantsById.getValue(order.restaurantId).let { it.latitude!! to it.longitude!! } }
            val roadDistances = osrmRoutingClient.routeDistancesKm(riderLat, riderLng, destinations)
            inRwanda.mapIndexed { index, (order, haversineDistanceKm) -> order to (roadDistances.getOrNull(index) ?: haversineDistanceKm) }
        } else {
            inRwanda
        }

        val sorted = (roadRanked + outsideRwanda).sortedBy { (_, distanceKm) -> distanceKm }.map { (order, _) -> order } + unlocatable
        val start = (pageable.offset).coerceAtMost(sorted.size.toLong()).toInt()
        val end = (start + pageable.pageSize).coerceAtMost(sorted.size)
        return PageImpl(sorted.subList(start, end), pageable, sorted.size.toLong())
    }

    /** Buyer, restaurant owner, or the assigned rider can view an order's items --
     * anyone else gets a real 404, not a 403 that would confirm the order exists. */
    fun getOrderDetail(requesterId: String, orderId: String): EatsOrderDetail {
        val order = eatsOrderRepository.findById(orderId).orElseThrow { EatsOrderNotFoundException("Order not found") }
        val restaurant = merchantRepository.findById(order.restaurantId).orElse(null)
        val rider = order.riderId?.let { riderRepository.findById(it).orElse(null) }
        val isBuyer = order.buyerId == requesterId
        val isRestaurant = restaurant?.ownerUserId == requesterId
        val isRider = rider?.userId == requesterId
        if (!isBuyer && !isRestaurant && !isRider) {
            throw EatsOrderNotFoundException("Order not found")
        }
        return EatsOrderDetail(order, eatsOrderItemRepository.findByOrderId(orderId))
    }

    /** Real live rider-location tracking during an active delivery (2026-07-19), the
     * natural next step once `RiderService.updateLocation` existed for proximity
     * dispatch -- a buyer can now see their real rider's real live position, the
     * defining "watch your order arrive" moment every real Coupang Eats/Uber Eats-style
     * app has. Same real IDOR discipline as `getOrderDetail` (buyer/restaurant/rider
     * only, real 404 for anyone else). Deliberately returns null -- not an exception --
     * whenever there's honestly nothing to show: before a rider is ever assigned, after
     * delivery completes or is cancelled (an old, stale position is misleading, not
     * useful), or when the assigned rider hasn't shared a location yet. Poll-based by
     * design for this first pass, the same "backend first, live-transport as a distinct
     * follow-up" precedent 1:1 messaging itself established before its own WebSocket
     * push existed. */
    fun getRiderLocation(requesterId: String, orderId: String): RiderLocationView? {
        val order = eatsOrderRepository.findById(orderId).orElseThrow { EatsOrderNotFoundException("Order not found") }
        val restaurant = merchantRepository.findById(order.restaurantId).orElse(null)
        val rider = order.riderId?.let { riderRepository.findById(it).orElse(null) }
        val isBuyer = order.buyerId == requesterId
        val isRestaurant = restaurant?.ownerUserId == requesterId
        val isRider = rider?.userId == requesterId
        if (!isBuyer && !isRestaurant && !isRider) {
            throw EatsOrderNotFoundException("Order not found")
        }
        if (order.status != EatsOrderStatus.RIDER_ASSIGNED && order.status != EatsOrderStatus.PICKED_UP) {
            return null
        }
        val lat = rider?.currentLatitude
        val lng = rider?.currentLongitude
        val updatedAt = rider?.locationUpdatedAt
        if (lat == null || lng == null || updatedAt == null) return null
        return RiderLocationView(lat, lng, updatedAt)
    }

    /** Restaurant-only, forward-only status progression through PLACED -> ACCEPTED ->
     * PREPARING -> READY_FOR_PICKUP -- same discipline as commerce's OrderService. */
    @Transactional
    fun updateRestaurantStatus(ownerUserId: String, orderId: String, newStatus: EatsOrderStatus): EatsOrder {
        val restaurant = merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw RestaurantNotFoundException("This account is not registered as a merchant")
        val order = eatsOrderRepository.findById(orderId).orElseThrow { EatsOrderNotFoundException("Order not found") }
        if (order.restaurantId != restaurant.id) {
            throw EatsOrderNotFoundException("Order not found")
        }
        val currentIndex = restaurantStatusOrder.indexOf(order.status)
        val newIndex = restaurantStatusOrder.indexOf(newStatus)
        if (currentIndex == -1 || newIndex != currentIndex + 1) {
            throw InvalidEatsOrderStatusTransitionException(
                "Cannot move from ${order.status} to $newStatus -- status can only advance one step at a time",
            )
        }
        order.status = newStatus
        order.updatedAt = Instant.now()
        val saved = eatsOrderRepository.save(order)
        when (newStatus) {
            EatsOrderStatus.ACCEPTED -> {
                notifyBuyer(saved, "Order accepted", "${restaurant.businessName} accepted your order and will start preparing it.")
                // Real accept resets the real consecutive-miss streak -- see
                // Merchant.consecutiveMissedOrders's own doc comment. A restaurant that
                // just responded promptly shouldn't stay one miss away from an
                // auto-pause because of misses from before this real accept.
                if (restaurant.consecutiveMissedOrders != 0) {
                    restaurant.consecutiveMissedOrders = 0
                    merchantRepository.save(restaurant)
                }
            }
            EatsOrderStatus.PREPARING -> notifyBuyer(saved, "Preparing your order", "${restaurant.businessName} is now preparing your order.")
            else -> {}
        }
        if (newStatus == EatsOrderStatus.READY_FOR_PICKUP) {
            // Real Baemin-style 포장주문 (Pickup): no rider should ever be offered a
            // PICKUP order -- the buyer collects it themselves, see completePickup's
            // own doc comment for the real terminal edge that replaces the rider chain.
            if (saved.fulfillmentType == EatsFulfillmentType.PICKUP) {
                notifyBuyer(saved, "Order ready", "Your order is ready -- come collect it at ${restaurant.businessName}.")
            } else {
                dispatchToNextCandidate(saved, restaurant)
            }
        }
        return saved
    }

    /**
     * Real terminal edge for a Baemin-style 포장주문 (Pickup) order -- restaurant-only,
     * valid only from READY_FOR_PICKUP on a PICKUP-type order, transitioning directly
     * to DELIVERED with no RIDER_ASSIGNED/PICKED_UP hop (there is no rider) and no
     * delivery-fee payout to make (deliveryFee is already zero, enforced unconditionally
     * at placement time -- see placeOrder's own doc comment).
     */
    @Transactional
    fun completePickup(ownerUserId: String, orderId: String): EatsOrder {
        val restaurant = merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw RestaurantNotFoundException("This account is not registered as a merchant")
        val order = eatsOrderRepository.findById(orderId).orElseThrow { EatsOrderNotFoundException("Order not found") }
        if (order.restaurantId != restaurant.id) {
            throw EatsOrderNotFoundException("Order not found")
        }
        if (order.fulfillmentType != EatsFulfillmentType.PICKUP) {
            throw InvalidEatsOrderStatusTransitionException("Only a PICKUP order can be completed this way")
        }
        if (order.status != EatsOrderStatus.READY_FOR_PICKUP) {
            throw InvalidEatsOrderStatusTransitionException(
                "Cannot complete pickup from ${order.status} -- the order must be READY_FOR_PICKUP",
            )
        }
        order.status = EatsOrderStatus.DELIVERED
        order.updatedAt = Instant.now()
        val saved = eatsOrderRepository.save(order)
        notifyBuyer(saved, "Order completed", "Thanks for picking up your order from ${restaurant.businessName}!")
        return saved
    }

    // Shared real proximity ranking (2026-07-20) -- extracted so both the open-browse
    // fallback push (notifyNearestRiders) and exclusive automatic dispatch
    // (dispatchToNextCandidate) rank candidates the exact same real way, rather than two
    // copies that could silently drift. Ranked by real GeoUtils.haversineKm only, not
    // the OSRM road-distance getAvailableDeliveries itself uses -- a dispatch/
    // notification trigger only needs a rough "who's actually close," the definitive
    // ranking a rider sees once they open the app is still the fully real one.
    // `candidatePool` (2026-07-20 performance sweep) -- lets a caller processing several
    // orders in one pass (DispatchOfferScheduler.reassignExpiredOffers) fetch the real
    // available-rider pool ONCE and reuse it across every order, instead of this method's
    // own default re-querying it fresh per call. Same real N+1 shape this project's own
    // sweeps have already fixed elsewhere (PayrollService.runPayroll, GroupMessagingService
    // .createGroup): the query has no per-order parameters, so it returns the identical
    // real result set every time within one short scheduler tick -- N calls for N orders
    // was real, avoidable repeated work, not N genuinely different queries.
    // Real busy-rider exclusion (2026-07-26) -- see
    // EatsOrderRepository.findDistinctRiderIdsByStatusIn's own doc comment. Computed
    // fresh here when a caller didn't already batch it (busyRiderIds defaults to null,
    // same "compute once, reuse for a batch" opt-in shape candidatePool already uses).
    private fun rankNearbyRiders(
        restaurantLat: Double,
        restaurantLng: Double,
        excludedUserIds: Set<String> = emptySet(),
        candidatePool: List<Rider>? = null,
        busyRiderIds: Set<String>? = null,
    ): List<Pair<Rider, Double>> {
        val actuallyBusy = busyRiderIds
            ?: eatsOrderRepository.findDistinctRiderIdsByStatusIn(listOf(EatsOrderStatus.RIDER_ASSIGNED, EatsOrderStatus.PICKED_UP)).toSet()
        return (candidatePool ?: riderRepository.findByAvailableTrueAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull())
            .filterNot { it.userId in excludedUserIds }
            .filterNot { it.id in actuallyBusy }
            .map { rider -> rider to GeoUtils.haversineKm(restaurantLat, restaurantLng, rider.currentLatitude!!, rider.currentLongitude!!) }
            .sortedBy { (_, distanceKm) -> distanceKm }
    }

    // Real proactive nearest-rider push (2026-07-19) -- the open-browse fallback: rather
    // than every online rider having to poll/browse to discover a new READY_FOR_PICKUP
    // order, the closest few get a real Notification. This is the model every real
    // delivery this backend used until automatic dispatch (dispatchToNextCandidate)
    // shipped the next day -- now used as the graceful fallback once automatic dispatch
    // has run out of real candidates to exclusively offer to, so a delivery is never
    // left silently stuck. The existing race-safe claimDelivery (first successful claim
    // wins once there's no active exclusive offer, already real-409s a second attempt)
    // decides who actually gets it from here. Best-effort by design, same "an auxiliary
    // side-effect can't block the real operation it's attached to" discipline
    // ShoppingCashbackService's own doc comment already established -- a notification
    // failure must never fail the real status transition it's reacting to.
    private fun notifyNearestRiders(order: EatsOrder, restaurant: Merchant, candidatePool: List<Rider>? = null, busyRiderIds: Set<String>? = null) {
        try {
            val restaurantLat = restaurant.latitude
            val restaurantLng = restaurant.longitude
            if (restaurantLat == null || restaurantLng == null) return
            val nearest = rankNearbyRiders(restaurantLat, restaurantLng, candidatePool = candidatePool, busyRiderIds = busyRiderIds).take(NEAREST_RIDERS_TO_NOTIFY)
            if (nearest.isEmpty()) return

            val nearestWithBody = nearest.map { (rider, distanceKm) ->
                val roundedKm = BigDecimal(distanceKm).setScale(1, RoundingMode.HALF_UP)
                Triple(rider.userId, "New delivery near you", "${restaurant.businessName} -- about $roundedKm km away")
            }
            notificationRepository.saveAll(
                nearestWithBody.map { (riderUserId, title, body) ->
                    Notification(
                        id = "notif_${UUID.randomUUID()}", userId = riderUserId, type = "NEW_DELIVERY_NEARBY",
                        title = title, body = body,
                        isRead = false, createdAt = Instant.now(), dataJson = "{\"orderId\":\"${order.id}\"}",
                    )
                },
            )
            // Real push (item 124) -- same fan-out-per-recipient shape
            // MerchantFollowService.broadcastToFollowers already established. A delivery
            // sitting unclaimed is real, time-sensitive lost business for the restaurant.
            nearestWithBody.forEach { (riderUserId, title, body) -> pushNotificationService.sendToUser(riderUserId, title, body) }
        } catch (e: Exception) {
            logger.warn("Failed to notify nearest riders for order {} -- the order is still real and claimable via browse, this is best-effort only: {}", order.id, e.message)
        }
    }

    // Real automatic dispatch (2026-07-20) -- offers a READY_FOR_PICKUP delivery
    // exclusively to the single nearest real available rider who hasn't already been
    // offered (and declined/timed out on) this exact delivery, for a real
    // OFFER_WINDOW-second accept window. During that window, only the offered rider can
    // claimDelivery -- a claim attempt from anyone else real-409s the same way an
    // already-claimed delivery does, same exclusivity discipline every other real
    // resource-ownership check in this backend uses (never leak that an active offer
    // exists to someone it wasn't made to). If no real candidate remains (every nearby
    // rider already tried, or none are online with a known location), gracefully
    // degrades to the pre-existing open browse/first-claim-wins model via
    // notifyNearestRiders -- a real delivery is never left silently stuck just because
    // automatic dispatch ran out of real candidates.
    private fun dispatchToNextCandidate(order: EatsOrder, restaurant: Merchant, candidatePool: List<Rider>? = null, busyRiderIds: Set<String>? = null) {
        try {
            val restaurantLat = restaurant.latitude
            val restaurantLng = restaurant.longitude
            if (restaurantLat == null || restaurantLng == null) {
                notifyNearestRiders(order, restaurant, candidatePool, busyRiderIds)
                return
            }
            val excluded = order.excludedRiderUserIds?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            val next = rankNearbyRiders(restaurantLat, restaurantLng, excluded, candidatePool, busyRiderIds).firstOrNull()?.first

            if (next == null) {
                order.offeredRiderId = null
                order.offerExpiresAt = null
                eatsOrderRepository.save(order)
                notifyNearestRiders(order, restaurant, candidatePool)
                return
            }

            order.offeredRiderId = next.id
            order.offerExpiresAt = Instant.now().plus(OFFER_WINDOW)
            eatsOrderRepository.save(order)

            val offerTitle = "New delivery -- you're closest"
            val offerBody = "${restaurant.businessName} -- accept within ${OFFER_WINDOW.seconds} seconds or it goes to the next rider"
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = next.userId, type = "DELIVERY_OFFER",
                    title = offerTitle, body = offerBody,
                    isRead = false, createdAt = Instant.now(), dataJson = "{\"orderId\":\"${order.id}\"}",
                ),
            )
            // Real push (item 124) -- of every real Notification in this backend, this
            // is arguably the single most time-sensitive: a real countdown window this
            // short is meaningless if the rider only sees it on their next in-app poll.
            pushNotificationService.sendToUser(next.userId, offerTitle, offerBody, mapOf("orderId" to order.id))
        } catch (e: Exception) {
            logger.warn("Failed to dispatch order {} to a real candidate rider -- falling back to open browse: {}", order.id, e.message)
        }
    }

    // Real scheduled reassignment (2026-07-20) -- backs DispatchOfferScheduler. Every
    // real unassigned order whose exclusive offer window expired without an explicit
    // accept or decline, same real "silent timeout == implicit decline" semantics every
    // real gig-dispatch system uses.
    fun getExpiredOffers(): List<EatsOrder> = eatsOrderRepository.findByOfferExpiresAtBeforeAndRiderIdIsNull(Instant.now())

    @Transactional
    fun reassignExpiredOffer(order: EatsOrder) = reassignExpiredOffers(listOf(order))

    // Real batched reassignment (2026-07-20 performance sweep) -- backs
    // DispatchOfferScheduler. Processing N expired orders one at a time used to mean N
    // separate merchantRepository.findById calls, N riderRepository.findById calls for
    // each order's timed-out rider, and N full riderRepository.findByAvailableTrueAnd...()
    // calls that all returned the exact same real candidate pool within one short
    // scheduler tick -- the same real repeated-work shape this project's own sweeps
    // already fixed for PayrollService.runPayroll/GroupMessagingService.createGroup, just
    // here in a scheduler rather than a request handler. Batched into three real queries
    // total (restaurants, expired riders, the candidate pool), no matter how many orders
    // expired in the same tick.
    @Transactional
    fun reassignExpiredOffers(orders: List<EatsOrder>) {
        if (orders.isEmpty()) return
        val restaurantsById = merchantRepository.findAllById(orders.map { it.restaurantId }.distinct()).associateBy { it.id }
        val expiredRiderIds = orders.mapNotNull { it.offeredRiderId }.distinct()
        val expiredRidersById = if (expiredRiderIds.isEmpty()) emptyMap() else riderRepository.findAllById(expiredRiderIds).associateBy { it.id }
        val candidatePool = riderRepository.findByAvailableTrueAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull()
        // Real busy-rider exclusion, computed once for the whole batch -- see
        // EatsOrderRepository.findDistinctRiderIdsByStatusIn's own doc comment.
        val busyRiderIds = eatsOrderRepository.findDistinctRiderIdsByStatusIn(listOf(EatsOrderStatus.RIDER_ASSIGNED, EatsOrderStatus.PICKED_UP)).toSet()

        for (order in orders) {
            val restaurant = restaurantsById[order.restaurantId] ?: continue
            val expiredRiderUserId = order.offeredRiderId?.let { expiredRidersById[it]?.userId }
            order.excludedRiderUserIds = (
                (order.excludedRiderUserIds?.split(",")?.filter { it.isNotBlank() } ?: emptyList()) + listOfNotNull(expiredRiderUserId)
                ).distinct().joinToString(",")
            order.offeredRiderId = null
            order.offerExpiresAt = null
            eatsOrderRepository.save(order)
            dispatchToNextCandidate(order, restaurant, candidatePool, busyRiderIds)
        }
    }

    /** Real explicit decline (2026-07-20) -- the offered rider proactively passes rather
     * than silently letting the real accept window expire, immediately triggering real
     * reassignment to the next candidate instead of waiting out the timeout. */
    @Transactional
    fun declineDelivery(riderUserId: String, orderId: String): EatsOrder {
        val rider = riderRepository.findByUserId(riderUserId)
            ?: throw RiderNotRegisteredException("This account is not registered as a rider")
        val order = eatsOrderRepository.findById(orderId).orElseThrow { EatsOrderNotFoundException("Order not found") }
        if (order.offeredRiderId != rider.id) {
            throw NoActiveOfferException("You don't have an active offer for this delivery")
        }
        order.excludedRiderUserIds = (
            (order.excludedRiderUserIds?.split(",")?.filter { it.isNotBlank() } ?: emptyList()) + rider.userId
            ).distinct().joinToString(",")
        order.offeredRiderId = null
        order.offerExpiresAt = null
        val saved = eatsOrderRepository.save(order)
        val restaurant = merchantRepository.findById(saved.restaurantId).orElse(null)
        if (restaurant != null && saved.status == EatsOrderStatus.READY_FOR_PICKUP && saved.riderId == null) {
            dispatchToNextCandidate(saved, restaurant)
        }
        return saved
    }

    /**
     * Real order cancellation + refund (2026-07-18) -- the same reversing-ledger-entry
     * technique commerce's `OrderService.cancelOrder` uses (itself reused from
     * `SupportService.reverseTransaction`): read the original transaction's own ledger
     * legs and post a new transaction with every leg's direction flipped, refunding the
     * buyer's items subtotal, platform fee, AND the delivery fee that was held in
     * `eats_delivery_holding` -- all in one atomic reversal, since no rider was ever
     * assigned or paid at this stage.
     *
     * Deliberately, honestly scoped to only PLACED orders -- before the restaurant has
     * started real fulfillment and, critically, before any rider is involved at all
     * (rider assignment only happens at READY_FOR_PICKUP+), so this never has to reason
     * about undoing a rider's already-in-progress or already-paid delivery. Either the
     * real buyer or the real restaurant can cancel from PLACED.
     */
    @Transactional
    fun cancelOrder(requesterId: String, orderId: String): EatsOrder {
        val order = eatsOrderRepository.findById(orderId).orElseThrow { EatsOrderNotFoundException("Order not found") }
        val restaurant = merchantRepository.findById(order.restaurantId).orElse(null)
        val isBuyer = order.buyerId == requesterId
        val isRestaurant = restaurant?.ownerUserId == requesterId
        if (!isBuyer && !isRestaurant) {
            throw EatsOrderNotFoundException("Order not found")
        }
        if (order.status != EatsOrderStatus.PLACED) {
            throw InvalidEatsOrderStatusTransitionException("Only a PLACED order can be cancelled -- this order is already ${order.status}")
        }

        val saved = refundAndCancel(order)
        // Only notify when the RESTAURANT cancelled -- a buyer who cancelled their own
        // order already knows, same "don't notify someone about their own action"
        // discipline every other Notification call site in this codebase already uses.
        if (isRestaurant) {
            notifyBuyer(saved, "Order cancelled", "${restaurant?.businessName ?: "The restaurant"} cancelled your order. Your payment has been refunded.")
        }
        return saved
    }

    // Real shared refund-and-cancel core, extracted 2026-08-16 (was inline in
    // cancelOrder only) so expireUnacceptedOrder can reuse the exact same real
    // reverse-every-ledger-leg refund mechanic rather than a second, divergent copy.
    // Callers own their own status/ownership checks -- this only ever touches a real
    // PLACED order (both call sites already guarantee that before calling in).
    private fun refundAndCancel(order: EatsOrder): EatsOrder {
        val originalEntries = ledgerEntryRepository.findByTransactionId(order.transactionId)
        val reversedLegs = originalEntries.map { entry ->
            val flipped = if (entry.direction == LedgerDirection.DEBIT) LedgerDirection.CREDIT else LedgerDirection.DEBIT
            LedgerLeg(entry.accountId, entry.accountType, flipped, entry.amount, "Refund for order ${order.id}")
        }
        val refund = ledgerService.postLedgerTransaction(originalEntries.first().currency, reversedLegs)

        order.status = EatsOrderStatus.CANCELLED
        order.refundTransactionId = refund.transactionId
        order.updatedAt = Instant.now()
        return eatsOrderRepository.save(order)
    }

    /**
     * Real DoorDash/Uber Eats-style "Item Unavailable" flow -- sourced from DoorDash's
     * own documented merchant-facing "mark item unavailable" feature: a restaurant
     * discovers mid-prep that one item can't be fulfilled and marks just that item,
     * rather than cancelling the whole order. Restaurant-only, valid only from
     * `ACCEPTED`/`PREPARING` (real fulfillment has started, but before
     * `READY_FOR_PICKUP`/rider dispatch -- once that's begun this is too late; a
     * `PLACED` order should use [cancelOrder] instead).
     *
     * Deliberately a NEW, standalone 2-leg refund rather than reusing
     * [refundAndCancel]'s "reverse every original leg" mechanic -- the original order
     * transaction is multi-leg (buyer charge, promotion expense, restaurant settlement,
     * platform fee, delivery-fee holding), and prorating a fraction of *all* of those
     * legs for one missing item is both unnecessary and not how the real product
     * behaves: the buyer gets just that item's price back, the restaurant's settlement
     * is clawed back by the same amount, and the platform fee/delivery fee/any
     * promotion discount already settled stay untouched -- the platform still does the
     * real dispatch/delivery work for the rest of the order regardless of one missing
     * item.
     */
    @Transactional
    fun markItemUnavailable(ownerUserId: String, orderId: String, orderItemId: String): EatsOrder {
        val restaurant = merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw RestaurantNotFoundException("This account is not registered as a merchant")
        val order = eatsOrderRepository.findById(orderId).orElseThrow { EatsOrderNotFoundException("Order not found") }
        if (order.restaurantId != restaurant.id) {
            throw EatsOrderNotFoundException("Order not found")
        }
        if (order.status != EatsOrderStatus.ACCEPTED && order.status != EatsOrderStatus.PREPARING) {
            throw InvalidEatsOrderStatusTransitionException(
                "Cannot mark an item unavailable from ${order.status} -- the order must be ACCEPTED or PREPARING",
            )
        }
        val items = eatsOrderItemRepository.findByOrderId(orderId)
        val item = items.find { it.id == orderItemId } ?: throw EatsOrderItemNotFoundException("Order item not found")
        if (item.unavailable) {
            throw EatsOrderItemAlreadyUnavailableException("This item has already been marked unavailable")
        }
        if (items.count { !it.unavailable } <= 1) {
            throw EatsOrderAllItemsUnavailableException("Cannot mark the last remaining item unavailable -- cancel the whole order instead")
        }

        val restaurantWallet = walletRepository.findById(restaurant.walletId)
            .orElseThrow { RestaurantNoWalletException("Restaurant settlement wallet not found") }
        val buyerWallet = walletRepository.findByUserIdAndType(order.buyerId, WalletType.MAIN)
            ?: throw EatsBuyerNoWalletException("No wallet found for this account")

        val itemAmount = item.unitPrice.multiply(BigDecimal(item.quantity))
        val refund = ledgerService.postLedgerTransaction(
            buyerWallet.currency,
            listOf(
                LedgerLeg(buyerWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, itemAmount, "Item unavailable refund - ${item.productName}"),
                LedgerLeg(restaurantWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, itemAmount, "Item unavailable clawback - ${item.productName}"),
            ),
        )

        item.unavailable = true
        item.refundTransactionId = refund.transactionId
        eatsOrderItemRepository.save(item)

        notifyBuyer(
            order,
            "Item unavailable",
            "${item.productName} wasn't available at ${restaurant.businessName} -- you've been refunded ${itemAmount.toPlainString()} for it. The rest of your order is still on its way.",
        )
        return order
    }

    // Real Uber Eats-style order-acceptance timeout query -- see
    // ORDER_ACCEPTANCE_TIMEOUT's own doc comment, backs OrderAcceptanceExpiryScheduler.
    fun getExpiredUnacceptedOrders(): List<EatsOrder> =
        eatsOrderRepository.findByStatusAndCreatedAtBefore(EatsOrderStatus.PLACED, Instant.now().minus(ORDER_ACCEPTANCE_TIMEOUT))

    /**
     * Real Uber Eats-style order-acceptance timeout (2026-08-16) -- sourced from Uber's
     * own official "Automatic pausing for merchants" blog post: a restaurant that
     * doesn't respond to a real order within [ORDER_ACCEPTANCE_TIMEOUT] never left the
     * buyer's payment held hostage on an unresponsive kitchen -- the order is
     * auto-cancelled and refunded via the exact same real ledger-reversal
     * [refundAndCancel] already uses for a normal cancellation. Also increments the
     * restaurant's real [Merchant.consecutiveMissedOrders] streak; once it reaches
     * [CONSECUTIVE_MISSES_TO_AUTO_PAUSE], the restaurant is automatically paused
     * (`isAcceptingOrders = false`, the exact same field/enforcement
     * `MerchantService.setAcceptingOrders`/this class's own `placeOrder` check already
     * established for the manual pause) and notified why -- a real, sourced Uber
     * pattern, not a fabricated penalty.
     */
    @Transactional
    fun expireUnacceptedOrder(order: EatsOrder) {
        if (order.status != EatsOrderStatus.PLACED) return
        val restaurant = merchantRepository.findById(order.restaurantId).orElse(null) ?: return
        val saved = refundAndCancel(order)
        notifyBuyer(saved, "Order cancelled", "${restaurant.businessName} didn't respond in time, so your order was cancelled and refunded.")

        restaurant.consecutiveMissedOrders += 1
        if (restaurant.consecutiveMissedOrders >= CONSECUTIVE_MISSES_TO_AUTO_PAUSE) {
            restaurant.isAcceptingOrders = false
            restaurant.consecutiveMissedOrders = 0
            val title = "Orders temporarily paused"
            val body = "$CONSECUTIVE_MISSES_TO_AUTO_PAUSE orders in a row went unaccepted, so ${restaurant.businessName} has been paused. Turn orders back on from your merchant settings when you're ready."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = restaurant.ownerUserId, type = "RESTAURANT_AUTO_PAUSED",
                    title = title, body = body, isRead = false, createdAt = Instant.now(), dataJson = "{\"merchantId\":\"${restaurant.id}\"}",
                ),
            )
            pushNotificationService.sendToUser(restaurant.ownerUserId, title, body, mapOf("merchantId" to restaurant.id))
        }
        merchantRepository.save(restaurant)
    }

    /** A real, available rider claims a READY_FOR_PICKUP order no one else has claimed
     * yet -- the actual "accept delivery" action, moving the order to RIDER_ASSIGNED.
     *
     * **Real 단건배달 (single-order delivery) guarantee, 2026-07-26** -- closes a real
     * gap found live while researching Coupang Eats' own real, sourced 단건배달/치타배달
     * distinction (2019) and Baemin's own 배민1 equivalent
     * (docs/DESIGN_REFERENCES.md): nothing in this method previously stopped a rider
     * from claiming a second delivery while still carrying an unfinished one (no
     * batching UI/logic exists anywhere in this backend to justify it either -- this
     * was a real oversight, not an intentional multi-order feature). Every itunda
     * delivery is now genuinely single-order, the same real guarantee Coupang
     * Eats/배민1 market -- see `ShoppingController.getEligibleMerchants`'s own
     * `singleOrderDelivery: true` field, honestly true because it's enforced here, not
     * decorative copy. */
    @Transactional
    fun claimDelivery(riderUserId: String, orderId: String): EatsOrder {
        val rider = riderRepository.findByUserId(riderUserId)
            ?: throw RiderNotRegisteredException("This account is not registered as a rider")
        if (!rider.available) {
            throw RiderNotAvailableException("Go online before claiming a delivery")
        }
        if (eatsOrderRepository.existsByRiderIdAndStatusIn(rider.id, listOf(EatsOrderStatus.RIDER_ASSIGNED, EatsOrderStatus.PICKED_UP))) {
            throw RiderAlreadyOnDeliveryException("Finish your current delivery before claiming another -- itunda riders carry one order at a time")
        }
        val order = eatsOrderRepository.findById(orderId).orElseThrow { EatsOrderNotFoundException("Order not found") }
        if (order.status != EatsOrderStatus.READY_FOR_PICKUP || order.riderId != null) {
            throw DeliveryAlreadyClaimedException("This delivery is no longer available")
        }
        // Real defense-in-depth for Baemin-style 포장주문 (Pickup): a PICKUP order is
        // already excluded from getAvailableDeliveries' own browse list, but a rider
        // who somehow has the raw order id (e.g. a stale client cache) must still be
        // real-rejected here -- same "no different message that would leak anything"
        // discipline the exclusive-offer check just below already uses.
        if (order.fulfillmentType == EatsFulfillmentType.PICKUP) {
            throw DeliveryAlreadyClaimedException("This delivery is no longer available")
        }
        // Real exclusive dispatch window (2026-07-20): while a real offer to a specific
        // rider hasn't expired yet, only that rider may claim it -- everyone else gets
        // the same real "no longer available" error an already-claimed delivery gives,
        // never a different message that would leak the existence of an active offer to
        // someone it wasn't made to.
        val offerStillActive = order.offerExpiresAt?.isAfter(Instant.now()) == true
        if (offerStillActive && order.offeredRiderId != rider.id) {
            throw DeliveryAlreadyClaimedException("This delivery is no longer available")
        }
        order.riderId = rider.id
        order.status = EatsOrderStatus.RIDER_ASSIGNED
        order.offeredRiderId = null
        order.offerExpiresAt = null
        order.updatedAt = Instant.now()
        val saved = eatsOrderRepository.save(order)
        notifyBuyer(saved, "Rider on the way", "A rider has been assigned to your order and is heading to the restaurant.")
        return saved
    }

    /** The assigned rider only, forward-only through RIDER_ASSIGNED -> PICKED_UP ->
     * DELIVERED. Reaching DELIVERED triggers the real delivery-fee payout, straight out
     * of `eats_delivery_holding` and into the rider's own wallet -- real money, paid the
     * moment the real work (the delivery) is actually done.
     *
     * `deliveryPhotoUrl` is the real Baemin/Coupang Eats/Uber Eats-style 안심배달
     * (safe/contactless delivery) proof photo -- see EatsOrder.deliveryProofPhotoUrl's
     * own doc comment. Only ever applied on the DELIVERED transition (the real product
     * only prompts for a drop-off photo at that step, never at PICKED_UP); silently
     * ignored for every other transition rather than rejected, since a client simply
     * wouldn't show the capture step there. */
    @Transactional
    fun updateRiderStatus(riderUserId: String, orderId: String, newStatus: EatsOrderStatus, deliveryPhotoUrl: String? = null): EatsOrder {
        val rider = riderRepository.findByUserId(riderUserId)
            ?: throw RiderNotRegisteredException("This account is not registered as a rider")
        val order = eatsOrderRepository.findById(orderId).orElseThrow { EatsOrderNotFoundException("Order not found") }
        if (order.riderId != rider.id) {
            throw NotAssignedRiderException("You are not the rider assigned to this delivery")
        }
        val currentIndex = riderStatusOrder.indexOf(order.status)
        val newIndex = riderStatusOrder.indexOf(newStatus)
        if (currentIndex == -1 || newIndex != currentIndex + 1) {
            throw InvalidEatsOrderStatusTransitionException(
                "Cannot move from ${order.status} to $newStatus -- status can only advance one step at a time",
            )
        }
        order.status = newStatus
        order.updatedAt = Instant.now()

        if (newStatus == EatsOrderStatus.DELIVERED) {
            if (deliveryPhotoUrl != null) {
                order.deliveryProofPhotoUrl = deliveryPhotoUrl
            }
            val riderWallet = walletRepository.findById(rider.walletId)
                .orElseThrow { RiderNoWalletException("Rider wallet not found") }
            val payout = ledgerService.postLedgerTransaction(
                riderWallet.currency,
                listOf(
                    LedgerLeg("eats_delivery_holding", LedgerAccountType.EATS_DELIVERY_HOLDING, LedgerDirection.DEBIT, order.deliveryFee, "Delivery fee payout - order ${order.id}"),
                    LedgerLeg(riderWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, order.deliveryFee, "Delivery fee payout - order ${order.id}"),
                ),
            )
            order.deliveryPayoutTransactionId = payout.transactionId
        }

        val saved = eatsOrderRepository.save(order)
        when (newStatus) {
            EatsOrderStatus.PICKED_UP -> notifyBuyer(saved, "Order picked up", "Your rider has picked up your order and is on the way to you.")
            EatsOrderStatus.DELIVERED -> notifyBuyer(saved, "Order delivered", "Your order has arrived. Enjoy your meal!")
            else -> {}
        }
        return saved
    }

    // Real buyer order-status notifications (2026-07-20) -- the real "your order was
    // accepted / your rider picked it up / your order arrived" push moments every real
    // Coupang Eats/Uber Eats/요기요/배민-style app sends, closing a gap this class's own
    // status-transition methods had carried since day one (they moved the real order
    // forward but never told the one person actually waiting on it). Same
    // `Notification` shape `dispatchToNextCandidate`'s own rider notification already
    // established, just addressed to the buyer instead.
    private fun notifyBuyer(order: EatsOrder, title: String, body: String) {
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = order.buyerId, type = "EATS_ORDER_UPDATE",
                title = title, body = body, isRead = false, createdAt = Instant.now(),
                dataJson = "{\"orderId\":\"${order.id}\"}",
            ),
        )
        // Real push (item 124) -- same buyer-facing status-update urgency as
        // OrderService/DineInOrderService's own matching gaps, closed the same pass.
        pushNotificationService.sendToUser(order.buyerId, title, body, mapOf("orderId" to order.id))
    }
}
