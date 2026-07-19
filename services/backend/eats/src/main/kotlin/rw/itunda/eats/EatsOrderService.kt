package rw.itunda.eats

import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.EatsOrder
import rw.itunda.core.domain.EatsOrderItem
import rw.itunda.core.domain.EatsOrderStatus
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.WalletType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.geo.GeocodeSuggestion
import rw.itunda.core.geo.NominatimGeocodingClient
import rw.itunda.core.geo.OsrmRoutingClient
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.EatsOrderItemRepository
import rw.itunda.core.repository.EatsOrderRepository
import rw.itunda.core.repository.LedgerEntryRepository
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
class SelfEatsOrderException(message: String) : RuntimeException(message)
class EatsOrderNotFoundException(message: String) : RuntimeException(message)
class InvalidEatsOrderStatusTransitionException(message: String) : RuntimeException(message)
class RiderNotAvailableException(message: String) : RuntimeException(message)
class DeliveryAlreadyClaimedException(message: String) : RuntimeException(message)
class NotAssignedRiderException(message: String) : RuntimeException(message)
class InvalidEatsCoordinatesException(message: String) : RuntimeException(message)
class InvalidEatsDeliveryNotesException(message: String) : RuntimeException(message)

data class EatsOrderItemRequest(val menuItemId: String, val quantity: Int)
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
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val transactionRepository: TransactionRepository,
    private val fraudRuleEngine: FraudRuleEngine,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val osrmRoutingClient: OsrmRoutingClient,
    private val nominatimGeocodingClient: NominatimGeocodingClient,
    private val rateLimiter: RateLimiter,
    private val notificationRepository: NotificationRepository,
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

    @Transactional
    fun placeOrder(
        buyerId: String,
        restaurantId: String,
        items: List<EatsOrderItemRequest>,
        deliveryAddress: String,
        deliveryLatitude: Double? = null,
        deliveryLongitude: Double? = null,
        deliveryNotes: String? = null,
    ): EatsOrderDetail {
        if (items.isEmpty()) {
            throw EmptyEatsOrderException("An order needs at least one item")
        }
        val trimmedAddress = deliveryAddress.trim()
        if (trimmedAddress.isEmpty()) {
            throw InvalidEatsDeliveryAddressException("A delivery address is required")
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

        val restaurantWallet = walletRepository.findById(restaurant.walletId)
            .orElseThrow { RestaurantNoWalletException("Restaurant settlement wallet not found") }
        val buyerWallet = walletRepository.findByUserIdAndType(buyerId, WalletType.MAIN)
            ?: throw EatsBuyerNoWalletException("No wallet found for this account")

        // Real prices read from the live menu row at checkout time -- never trusted from
        // the client, same price-tampering prevention as commerce's OrderService.
        data class Resolved(val productId: String, val name: String, val unitPrice: BigDecimal, val quantity: Int)
        val resolved = items.map { req ->
            if (req.quantity <= 0) {
                throw InvalidEatsQuantityException("Quantity must be at least 1")
            }
            val menuItem = merchantProductRepository.findById(req.menuItemId)
                .orElseThrow { MenuItemNotFoundException("Menu item not found") }
            if (menuItem.merchantId != restaurantId || !menuItem.active) {
                throw MenuItemNotFoundException("Menu item not found")
            }
            Resolved(menuItem.id, menuItem.name, menuItem.price, req.quantity)
        }
        val itemsSubtotal = resolved.fold(BigDecimal.ZERO) { acc, r -> acc + r.unitPrice.multiply(BigDecimal(r.quantity)) }
        val platformFee = itemsSubtotal.multiply(platformFeeRate).setScale(2, RoundingMode.HALF_UP)
        val netToRestaurant = itemsSubtotal.subtract(platformFee)

        val restaurantLat = restaurant.latitude
        val restaurantLng = restaurant.longitude

        // Real geocoding fallback (2026-07-18): when the buyer didn't submit explicit
        // coordinates, try resolving the free-text delivery address via itunda's own
        // self-hosted Nominatim -- lets the existing deliveryAddress field drive a real
        // distance-based fee without needing any client UI changes yet. Falls back to
        // no coordinates (and therefore the flat fee below) if geocoding is
        // unconfigured/unreachable/finds no match -- never a fabricated location.
        val geocoded = if (deliveryLatitude == null && deliveryLongitude == null) {
            nominatimGeocodingClient.geocode(trimmedAddress)
        } else {
            null
        }
        val resolvedDeliveryLat = deliveryLatitude ?: geocoded?.latitude
        val resolvedDeliveryLng = deliveryLongitude ?: geocoded?.longitude

        val distanceKm = if (resolvedDeliveryLat != null && resolvedDeliveryLng != null && restaurantLat != null && restaurantLng != null) {
            resolveRealDistanceKm(restaurantLat, restaurantLng, resolvedDeliveryLat, resolvedDeliveryLng)
        } else {
            null
        }
        val (deliveryFee, distanceKmRounded) = computeDeliveryFee(distanceKm)
        val totalAmount = itemsSubtotal.add(deliveryFee)

        val result = ledgerService.postLedgerTransaction(
            buyerWallet.currency,
            listOf(
                LedgerLeg(buyerWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, totalAmount, "Eats order - ${restaurant.businessName}"),
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
            amount = totalAmount,
            fee = platformFee.add(deliveryFee),
            currency = buyerWallet.currency,
            type = TransactionType.PAYMENT,
            status = TransactionStatus.COMPLETED,
            description = "Eats order - ${restaurant.businessName}",
            channel = "EATS_ORDER",
            completedAt = Instant.now(),
        )
        fraudRuleEngine.evaluate(buyerId, restaurant.ownerUserId, totalAmount, transaction.id)
        transactionRepository.save(transaction)

        val order = eatsOrderRepository.save(
            EatsOrder(
                id = "eats_order_${UUID.randomUUID()}", buyerId = buyerId, restaurantId = restaurantId,
                deliveryAddress = trimmedAddress, itemsSubtotal = itemsSubtotal, deliveryFee = deliveryFee,
                platformFee = platformFee, totalAmount = totalAmount, transactionId = result.transactionId,
                deliveryLatitude = resolvedDeliveryLat, deliveryLongitude = resolvedDeliveryLng, distanceKm = distanceKmRounded,
                deliveryNotes = trimmedNotes,
            ),
        )
        val orderItems = resolved.map {
            EatsOrderItem(
                id = "eats_order_item_${UUID.randomUUID()}", orderId = order.id, productId = it.productId,
                productName = it.name, unitPrice = it.unitPrice, quantity = it.quantity,
            )
        }
        eatsOrderItemRepository.saveAll(orderItems)

        return EatsOrderDetail(order, orderItems)
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
        if (riderLat == null || riderLng == null) {
            return eatsOrderRepository.findByStatusAndRiderIdIsNullOrderByCreatedAtAsc(EatsOrderStatus.READY_FOR_PICKUP, pageable)
        }

        val candidates = eatsOrderRepository.findByStatusAndRiderIdIsNullOrderByCreatedAtAsc(
            EatsOrderStatus.READY_FOR_PICKUP, Pageable.unpaged(),
        ).content
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
        if (newStatus == EatsOrderStatus.READY_FOR_PICKUP) {
            notifyNearestRiders(saved, restaurant)
        }
        return saved
    }

    // Real proactive nearest-rider push (2026-07-19) -- the "push" half of "push-based
    // rider assignment": rather than every online rider having to poll/browse to
    // discover a new READY_FOR_PICKUP order, the closest few get a real Notification the
    // moment one appears. Deliberately NOT full automatic assignment -- no rider is
    // auto-committed to anything, this only surfaces the real opportunity faster; the
    // existing race-safe claimDelivery (first successful claim wins, already real-409s a
    // second attempt) is completely unchanged and still decides who actually gets it.
    // Ranked by real GeoUtils.haversineKm only, not the OSRM road-distance
    // getAvailableDeliveries itself uses -- a notification trigger only needs a rough
    // "who's actually close," the definitive ranking a rider sees once they open the app
    // is still the fully real one. Best-effort by design, same "an auxiliary side-effect
    // can't block the real operation it's attached to" discipline
    // ShoppingCashbackService's own doc comment already established -- a notification
    // failure must never fail the real status transition it's reacting to.
    private fun notifyNearestRiders(order: EatsOrder, restaurant: Merchant) {
        try {
            val restaurantLat = restaurant.latitude
            val restaurantLng = restaurant.longitude
            if (restaurantLat == null || restaurantLng == null) return
            val candidates = riderRepository.findByAvailableTrueAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull()
            if (candidates.isEmpty()) return

            val nearest = candidates
                .map { rider -> rider to GeoUtils.haversineKm(restaurantLat, restaurantLng, rider.currentLatitude!!, rider.currentLongitude!!) }
                .sortedBy { (_, distanceKm) -> distanceKm }
                .take(NEAREST_RIDERS_TO_NOTIFY)

            notificationRepository.saveAll(
                nearest.map { (rider, distanceKm) ->
                    val roundedKm = BigDecimal(distanceKm).setScale(1, RoundingMode.HALF_UP)
                    Notification(
                        id = "notif_${UUID.randomUUID()}", userId = rider.userId, type = "NEW_DELIVERY_NEARBY",
                        title = "New delivery near you", body = "${restaurant.businessName} -- about $roundedKm km away",
                        isRead = false, createdAt = Instant.now(), dataJson = "{\"orderId\":\"${order.id}\"}",
                    )
                },
            )
        } catch (e: Exception) {
            logger.warn("Failed to notify nearest riders for order {} -- the order is still real and claimable via browse, this is best-effort only: {}", order.id, e.message)
        }
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

    /** A real, available rider claims a READY_FOR_PICKUP order no one else has claimed
     * yet -- the actual "accept delivery" action, moving the order to RIDER_ASSIGNED. */
    @Transactional
    fun claimDelivery(riderUserId: String, orderId: String): EatsOrder {
        val rider = riderRepository.findByUserId(riderUserId)
            ?: throw RiderNotRegisteredException("This account is not registered as a rider")
        if (!rider.available) {
            throw RiderNotAvailableException("Go online before claiming a delivery")
        }
        val order = eatsOrderRepository.findById(orderId).orElseThrow { EatsOrderNotFoundException("Order not found") }
        if (order.status != EatsOrderStatus.READY_FOR_PICKUP || order.riderId != null) {
            throw DeliveryAlreadyClaimedException("This delivery is no longer available")
        }
        order.riderId = rider.id
        order.status = EatsOrderStatus.RIDER_ASSIGNED
        order.updatedAt = Instant.now()
        return eatsOrderRepository.save(order)
    }

    /** The assigned rider only, forward-only through RIDER_ASSIGNED -> PICKED_UP ->
     * DELIVERED. Reaching DELIVERED triggers the real delivery-fee payout, straight out
     * of `eats_delivery_holding` and into the rider's own wallet -- real money, paid the
     * moment the real work (the delivery) is actually done. */
    @Transactional
    fun updateRiderStatus(riderUserId: String, orderId: String, newStatus: EatsOrderStatus): EatsOrder {
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

        return eatsOrderRepository.save(order)
    }
}
