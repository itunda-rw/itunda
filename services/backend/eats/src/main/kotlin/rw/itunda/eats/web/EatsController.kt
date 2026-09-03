package rw.itunda.eats.web

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.domain.EatsFulfillmentType
import rw.itunda.core.domain.EatsOrderStatus
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.AccountFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import rw.itunda.eats.DeliveryAlreadyClaimedException
import rw.itunda.eats.RiderAlreadyOnDeliveryException
import rw.itunda.eats.EatsBuyerNoAccountException
import rw.itunda.eats.EatsFavoriteService
import rw.itunda.eats.EatsMembershipNoAccountException
import rw.itunda.eats.EatsMembershipService
import rw.itunda.eats.InvalidMembershipDurationException
import rw.itunda.eats.InvalidPlatformMembershipDurationException
import rw.itunda.eats.PlatformMembershipNoAccountException
import rw.itunda.eats.PlatformMembershipService
import rw.itunda.eats.RestaurantNotAcceptingOrdersException
import rw.itunda.eats.EatsOrderAlreadyReviewedException
import rw.itunda.eats.EatsOrderItemRequest
import rw.itunda.eats.EatsOrderAllItemsUnavailableException
import rw.itunda.eats.EatsOrderItemAlreadyUnavailableException
import rw.itunda.eats.EatsOrderItemNotFoundException
import rw.itunda.eats.EatsOrderAlreadyTippedException
import rw.itunda.eats.EatsOrderNoRiderException
import rw.itunda.eats.EatsOrderNotDeliveredException
import rw.itunda.eats.EatsOrderNotFoundException
import rw.itunda.eats.EatsOrderTipWindowExpiredException
import rw.itunda.eats.InvalidEatsTipAmountException
import rw.itunda.eats.EatsOrderNotYetDeliveredException
import rw.itunda.eats.EatsOrderService
import rw.itunda.eats.EatsReviewAlreadyReportedException
import rw.itunda.eats.EatsReviewNotFoundException
import rw.itunda.eats.EatsReviewService
import rw.itunda.eats.OwnEatsReviewReportException
import rw.itunda.core.domain.EatsReviewReportReason
import rw.itunda.eats.EmptyEatsOrderException
import rw.itunda.eats.InvalidEatsReviewReplyException
import rw.itunda.eats.InvalidEatsCoordinatesException
import rw.itunda.eats.InvalidEatsDeliveryAddressException
import rw.itunda.eats.InvalidEatsDeliveryNotesException
import rw.itunda.eats.InvalidEatsOrderStatusTransitionException
import rw.itunda.eats.InvalidEatsQuantityException
import rw.itunda.eats.InvalidEatsRatingException
import rw.itunda.eats.InvalidRiderLocationException
import rw.itunda.eats.InvalidMenuOptionSelectionException
import rw.itunda.eats.MenuItemNotFoundException
import rw.itunda.eats.MenuItemSoldOutException
import rw.itunda.eats.MenuItemSurplusDealExpiredException
import rw.itunda.eats.MissingRequiredMenuOptionException
import rw.itunda.eats.NoActiveOfferException
import rw.itunda.eats.NotAssignedRiderException
import rw.itunda.eats.RestaurantNoAccountException
import rw.itunda.eats.RestaurantNotFoundException
import rw.itunda.eats.RiderAlreadyRegisteredException
import rw.itunda.eats.RiderNoAccountException
import rw.itunda.eats.RiderNotAvailableException
import rw.itunda.eats.RiderNotRegisteredException
import rw.itunda.eats.RiderService
import rw.itunda.eats.ScheduledOrdersNotSupportedException
import rw.itunda.eats.InvalidScheduledOrderTimeException
import rw.itunda.eats.MinOrderAmountNotMetException
import rw.itunda.eats.SelfEatsOrderException
import java.time.Instant

data class PlaceEatsOrderRequest(
    val restaurantId: String,
    val items: List<EatsOrderItemRequest>,
    // Optional for a real Baemin-style 포장주문 (Pickup) order -- see
    // EatsOrderService.placeOrder's own doc comment; a buyer collecting in person has
    // no real delivery address to submit. Defaults to "" (fully backward compatible
    // with every existing DELIVERY-only client).
    val deliveryAddress: String = "",
    val deliveryLatitude: Double? = null,
    val deliveryLongitude: Double? = null,
    val deliveryNotes: String? = null,
    val fulfillmentType: EatsFulfillmentType = EatsFulfillmentType.DELIVERY,
    // Real 배달의민족 예약주문 (scheduled ordering) (2026-07-26) -- see
    // EatsOrderService.placeOrder's own doc comment. Null (the default) means ASAP,
    // every existing client's behavior completely unchanged.
    val scheduledFor: Instant? = null,
)
data class SubscribeMembershipRequest(val days: Int)
data class SubscribePlatformMembershipRequest(val days: Int)
data class UpdateEatsOrderStatusRequest(
    val status: EatsOrderStatus,
    // Real 안심배달 (safe/contactless delivery) proof photo -- only meaningful when
    // status is DELIVERED, see EatsOrderService.updateRiderStatus's own doc comment.
    // Ignored by the restaurant-status endpoint, which reuses this same DTO.
    val deliveryPhotoUrl: String? = null,
)
data class SetRiderAvailabilityRequest(val available: Boolean)
data class UpdateRiderLocationRequest(val latitude: Double, val longitude: Double)
data class TipEatsOrderRequest(val amount: java.math.BigDecimal)
data class SubmitEatsReviewRequest(
    val restaurantRating: Int,
    val restaurantComment: String? = null,
    // Nullable (2026-07-26) -- a real Baemin-style PICKUP order has no rider to rate;
    // see EatsReview.kt's own doc comment for the full account.
    val riderRating: Int? = null,
    val riderComment: String? = null,
    // Real optional review photo (2026-08-04) -- see EatsReview.photoUrl's own doc
    // comment.
    val photoUrl: String? = null,
    // Real preset-tag checklist (2026-08-28) -- see EatsReview.goodPoints' own doc
    // comment; invalid/unknown tag ids are silently filtered server-side, not rejected.
    val goodPoints: List<String> = emptyList(),
)
data class ReplyToEatsReviewRequest(val reply: String)

// Real 배달의민족 리뷰 신고하기 (report a review) -- see EatsReviewReport.kt's own doc
// comment.
data class ReportEatsReviewRequest(val reason: EatsReviewReportReason, val details: String? = null)
data class ShareFavoritesRequest(val conversationId: String)

// Real edge case, same "don't leak a raw messaging exception as an unhandled 500"
// discipline MarketplaceService.contactSeller's own OwnListingException already
// establishes -- a merchant owner who placed a real order at their own restaurant
// (a real, if unusual, possibility: nothing stops one itunda account from being both)
// would otherwise hit MessagingService.startOrGetConversation's own
// SelfConversationException with no controller-layer translation.
class EatsOrderOwnRestaurantException(message: String) : RuntimeException(message)

// Real Coupang Eats-style food ordering + delivery. Restaurant browsing/menus
// deliberately reuse the existing GET /api/v1/shopping/merchants and GET
// /api/v1/shopping/merchants/{id}/products endpoints (a restaurant IS a Merchant, a menu
// item IS a MerchantProduct) -- no duplicate catalog-browsing endpoint added here. See
// EatsOrderService's own doc comment for the full account, including the real
// distance-based delivery fee (2026-07-18) computed when both restaurant and buyer
// coordinates exist. Normal itunda-user JWT gate.
@RestController
@RequestMapping("/api/v1/eats")
class EatsController(
    private val riderService: RiderService,
    private val eatsOrderService: EatsOrderService,
    private val eatsReviewService: EatsReviewService,
    private val eatsFavoriteService: EatsFavoriteService,
    private val eatsMembershipService: EatsMembershipService,
    private val platformMembershipService: PlatformMembershipService,
    private val membershipExpiryReminderScheduler: rw.itunda.eats.MembershipExpiryReminderScheduler,
    private val idempotencyService: IdempotencyService,
    private val merchantProductRepository: rw.itunda.core.repository.MerchantProductRepository,
    private val merchantRepository: rw.itunda.core.repository.MerchantRepository,
    private val riderRepository: rw.itunda.core.repository.RiderRepository,
    private val userRepository: rw.itunda.core.repository.UserRepository,
    private val eatsOrderRepository: rw.itunda.core.repository.EatsOrderRepository,
    private val messagingService: rw.itunda.messaging.MessagingService,
    private val dishRecommendationService: rw.itunda.eats.EatsDishRecommendationService,
) {
    // Real fresh Uber Eats research (2026-08-15, restaurantdive.com's coverage of Uber
    // Eats' own delivery-tracker redesign, sourced from real internal research across
    // nine countries): a real customer-facing rider name and "Latest Arrival By" time
    // shown alongside delivery status -- itunda's own order-tracking screen already had
    // a real stepped status UI and live rider-location map (at or beyond Uber Eats'
    // bar), but never resolved order.riderId to a real name, and never turned the real,
    // already-stored order.distanceKm into a customer-facing arrival estimate the way
    // DeliveryEtaEstimator (promoted from ShoppingController's own browse-time version)
    // already does for browsing. Resolved here, at the controller layer, rather than
    // adding new repository dependencies to EatsOrderService's own already-19-parameter
    // constructor (which would mean updating all 9 of its existing test call sites for a
    // read-only enrichment that has nothing to do with that service's real business
    // logic) -- EatsController has no test file of its own yet, so this is genuinely the
    // lower-risk seam, not just the easier one.
    private fun withRiderEtaFields(order: rw.itunda.core.domain.EatsOrder): Map<String, Any?> {
        val rider = order.riderId?.let { riderRepository.findById(it).orElse(null) }
        val riderName = rider?.let { userRepository.findById(it.userId).orElse(null) }?.firstName
        val etaMinutes = order.distanceKm?.let {
            val prepTimeMinutes = merchantRepository.findById(order.restaurantId).orElse(null)?.avgPrepTimeMinutes
            rw.itunda.core.geo.DeliveryEtaEstimator.estimateDeliveryMinutes(it.toDouble(), prepTimeMinutes)
        }
        return mapOf(
            "id" to order.id, "buyerId" to order.buyerId, "restaurantId" to order.restaurantId,
            "riderId" to order.riderId, "deliveryAddress" to order.deliveryAddress,
            "itemsSubtotal" to order.itemsSubtotal, "deliveryFee" to order.deliveryFee,
            "platformFee" to order.platformFee, "totalAmount" to order.totalAmount,
            "promotionDiscount" to order.promotionDiscount,
            "transactionId" to order.transactionId,
            "deliveryPayoutTransactionId" to order.deliveryPayoutTransactionId,
            "status" to order.status, "createdAt" to order.createdAt, "updatedAt" to order.updatedAt,
            "refundTransactionId" to order.refundTransactionId,
            "deliveryLatitude" to order.deliveryLatitude, "deliveryLongitude" to order.deliveryLongitude,
            "distanceKm" to order.distanceKm, "deliveryNotes" to order.deliveryNotes,
            "riderName" to riderName, "estimatedArrivalMinutes" to etaMinutes,
        )
    }
    // Real Coupang Eats-style dish grid (2026-08-03) -- see
    // EatsDishRecommendationService.getDishes' own doc comment for the full
    // sourcing + real sortBy=popular addition (extracted from this endpoint
    // 2026-08-28, keeping this controller at its real frozen file-size-lint
    // baseline).
    @GetMapping("/dishes")
    fun getDishes(
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) maxBudget: java.math.BigDecimal?,
        @RequestParam(required = false) sortBy: String?,
        @PageableDefault(size = 30) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (page, dishes) = dishRecommendationService.getDishes(category, maxBudget, sortBy, pageable, currentUser.userId)
        return ResponseEntity.ok(mapOf("success" to true, "dishes" to dishes) + pageMeta(page))
    }

    // Real Coupang 와우 (Wow)-style unconditional delivery-fee waiver -- see
    // PlatformMembership.kt's own doc comment for the full sourced account and its
    // honest distinction from Eats Club (above).
    @PostMapping("/platform-membership/subscribe")
    fun subscribePlatformMembership(
        @RequestBody request: SubscribePlatformMembershipRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/eats/platform-membership/subscribe", idempotencyKey, request) {
            val membership = platformMembershipService.subscribe(currentUser.userId, request.days)
            200 to mapOf("success" to true, "membership" to membership)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/platform-membership/me")
    fun getMyPlatformMembership(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "membership" to platformMembershipService.getMyMembership(currentUser.userId)))

    // Real Baemin Club (배민클럽)-style free-delivery membership -- see
    // EatsMembership.kt's own doc comment. Real Idempotency-Key requirement, same
    // convention as every other money-moving creation endpoint in this backend.
    @PostMapping("/membership/subscribe")
    fun subscribeMembership(
        @RequestBody request: SubscribeMembershipRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/eats/membership/subscribe", idempotencyKey, request) {
            val membership = eatsMembershipService.subscribe(currentUser.userId, request.days)
            200 to mapOf("success" to true, "membership" to membership)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/membership/me")
    fun getMyMembership(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "membership" to eatsMembershipService.getMyMembership(currentUser.userId)))

    // Real membership expiry-reminder manual trigger -- same "expose the scheduler's
    // own real logic as a callable endpoint" convention
    // MerchantCouponController/InsuranceController/CertificateController already
    // establish, so a real membership's real activeUntil can be verified without
    // waiting actual wall-clock days for it to enter the reminder window. See
    // MembershipExpiryReminderScheduler's own doc comment.
    // Real gap found live (2026-08-31, market-readiness audit): this fires the
    // reminder job for EVERY user's expiring memberships system-wide, yet had no ADMIN
    // gate -- any authenticated user could call it, unlike this file's own
    // processAbandonedDeliveries below, which is already ADMIN-gated. ADMIN-gated the
    // same @PreAuthorize("hasRole('ADMIN')") way (this route doesn't live under
    // /api/v1/system/**, so it doesn't inherit SecurityConfig's blanket ADMIN gate
    // there).
    @PostMapping("/membership/process-expiry-reminders")
    @PreAuthorize("hasRole('ADMIN')")
    fun processMembershipExpiryReminders(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val processed = membershipExpiryReminderScheduler.processDue()
        return ResponseEntity.ok(mapOf("success" to true, "processed" to processed))
    }

    @PostMapping("/riders/register")
    fun registerRider(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val rider = riderService.register(currentUser.userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "rider" to rider))
    }

    @GetMapping("/riders/me")
    fun getMyRiderProfile(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val rider = riderService.getMyRiderProfile(currentUser.userId)
        return ResponseEntity.ok(mapOf("success" to true, "rider" to rider))
    }

    @PostMapping("/riders/availability")
    fun setAvailability(
        @RequestBody request: SetRiderAvailabilityRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val rider = riderService.setAvailability(currentUser.userId, request.available)
        return ResponseEntity.ok(mapOf("success" to true, "rider" to rider))
    }

    // Real GPS location update (2026-07-19) -- see RiderService.updateLocation's own
    // doc comment. Backs real nearest-first ranking on /orders/available below.
    @PostMapping("/riders/location")
    fun updateRiderLocation(
        @RequestBody request: UpdateRiderLocationRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val rider = riderService.updateLocation(currentUser.userId, request.latitude, request.longitude)
        return ResponseEntity.ok(mapOf("success" to true, "rider" to rider))
    }

    @PostMapping("/orders")
    fun placeOrder(
        @RequestBody request: PlaceEatsOrderRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/eats/orders", idempotencyKey, request) {
            val detail = eatsOrderService.placeOrder(
                currentUser.userId, request.restaurantId, request.items, request.deliveryAddress,
                request.deliveryLatitude, request.deliveryLongitude, request.deliveryNotes, request.fulfillmentType,
                request.scheduledFor,
            )
            201 to mapOf("success" to true, "order" to detail.order, "items" to detail.items)
        }
        return ResponseEntity.status(status).body(body)
    }

    // Real address-search autocomplete (2026-07-18) -- see EatsOrderService's own doc
    // comment. Backs a real client-side address picker so a buyer can see and confirm
    // real coordinates before checkout, on top of itunda's own self-hosted Nominatim.
    @GetMapping("/geocode/search")
    fun searchDeliveryAddress(
        @RequestParam q: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val suggestions = eatsOrderService.searchDeliveryAddress(currentUser.userId, q)
        return ResponseEntity.ok(mapOf("success" to true, "suggestions" to suggestions))
    }

    @GetMapping("/orders/my-orders")
    fun getMyOrders(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = eatsOrderService.getMyOrders(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "orders" to page.content.map(::withRiderEtaFields)) + pageMeta(page))
    }

    @GetMapping("/orders/restaurant-orders")
    fun getRestaurantOrders(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = eatsOrderService.getRestaurantOrders(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "orders" to page.content) + pageMeta(page))
    }

    @GetMapping("/orders/rider-deliveries")
    fun getRiderDeliveries(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = eatsOrderService.getRiderDeliveries(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "orders" to page.content) + pageMeta(page))
    }

    @GetMapping("/orders/available")
    fun getAvailableDeliveries(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = eatsOrderService.getAvailableDeliveries(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "orders" to page.content) + pageMeta(page))
    }

    @GetMapping("/orders/{orderId}")
    fun getOrder(
        @PathVariable orderId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val detail = eatsOrderService.getOrderDetail(currentUser.userId, orderId)
        return ResponseEntity.ok(mapOf("success" to true, "order" to detail.order, "items" to detail.items))
    }

    // Real live rider-location tracking (2026-07-19) -- see EatsOrderService's own doc
    // comment. `available: false` (not an error) is the honest, expected response
    // whenever there's genuinely nothing to show yet.
    @GetMapping("/orders/{orderId}/rider-location")
    fun getRiderLocation(
        @PathVariable orderId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val location = eatsOrderService.getRiderLocation(currentUser.userId, orderId)
        return ResponseEntity.ok(mapOf("success" to true, "available" to (location != null), "location" to location))
    }

    // Real "message restaurant" (2026-08-16, Uber Eats' own real Live Order Chat --
    // "merchants can initiate chats directly with customers once an order has been
    // received" to confirm substitutions/special requests/allergies before delivery
    // rather than discovering an issue after). itunda's own `MarketplaceService
    // .contactSeller` already established the exact real pattern this reuses
    // unmodified: `MessagingService.startOrGetConversation`, IDOR-checked via the
    // same real buyer/restaurant-owner resolution `EatsOrderService.getOrderDetail`
    // already does. Deliberately resolved at the controller layer, matching this
    // file's own `withRiderEtaFields` precedent, rather than growing
    // `EatsOrderService`'s already-28-parameter constructor for a read-adjacent
    // enrichment with nothing to do with that service's real order-lifecycle logic.
    @PostMapping("/orders/{orderId}/contact-restaurant")
    fun contactRestaurant(
        @PathVariable orderId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val order = eatsOrderRepository.findById(orderId).orElseThrow { rw.itunda.eats.EatsOrderNotFoundException("Order not found") }
        if (order.buyerId != currentUser.userId) {
            // Same "don't reveal a resource exists to someone who shouldn't act on it"
            // discipline every other real ownership check in this codebase uses.
            throw rw.itunda.eats.EatsOrderNotFoundException("Order not found")
        }
        val restaurant = merchantRepository.findById(order.restaurantId).orElseThrow { rw.itunda.eats.EatsOrderNotFoundException("Order not found") }
        val conversation = try {
            messagingService.startOrGetConversation(currentUser.userId, restaurant.ownerUserId)
        } catch (e: rw.itunda.messaging.SelfConversationException) {
            throw EatsOrderOwnRestaurantException("This is your own restaurant")
        }
        return ResponseEntity.ok(mapOf("success" to true, "conversation" to conversation))
    }

    @PostMapping("/orders/{orderId}/status")
    fun updateRestaurantStatus(
        @PathVariable orderId: String,
        @RequestBody request: UpdateEatsOrderStatusRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val order = eatsOrderService.updateRestaurantStatus(currentUser.userId, orderId, request.status)
        return ResponseEntity.ok(mapOf("success" to true, "order" to order))
    }

    // Real DoorDash/Uber Eats-style "Item Unavailable" flow -- see
    // EatsOrderService.markItemUnavailable's own doc comment.
    @PostMapping("/orders/{orderId}/items/{itemId}/unavailable")
    fun markItemUnavailable(
        @PathVariable orderId: String,
        @PathVariable itemId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val order = eatsOrderService.markItemUnavailable(currentUser.userId, orderId, itemId)
        return ResponseEntity.ok(mapOf("success" to true, "order" to order))
    }

    // Real Baemin-style 포장주문 (Pickup) terminal edge -- see
    // EatsOrderService.completePickup's own doc comment.
    @PostMapping("/orders/{orderId}/complete-pickup")
    fun completePickup(
        @PathVariable orderId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val order = eatsOrderService.completePickup(currentUser.userId, orderId)
        return ResponseEntity.ok(mapOf("success" to true, "order" to order))
    }

    // Real cancellation + refund (2026-07-18) -- buyer or restaurant, PLACED orders
    // only. See EatsOrderService.cancelOrder's own doc comment for the full account.
    @PostMapping("/orders/{orderId}/cancel")
    fun cancelOrder(
        @PathVariable orderId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val order = eatsOrderService.cancelOrder(currentUser.userId, orderId)
        return ResponseEntity.ok(mapOf("success" to true, "order" to order))
    }

    @PostMapping("/orders/{orderId}/claim")
    fun claimDelivery(
        @PathVariable orderId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val order = eatsOrderService.claimDelivery(currentUser.userId, orderId)
        return ResponseEntity.ok(mapOf("success" to true, "order" to order))
    }

    // Real automatic dispatch (2026-07-20) -- see EatsOrderService.declineDelivery's own
    // doc comment. Only the rider currently holding the real exclusive offer can decline it.
    @PostMapping("/orders/{orderId}/decline")
    fun declineDelivery(
        @PathVariable orderId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val order = eatsOrderService.declineDelivery(currentUser.userId, orderId)
        return ResponseEntity.ok(mapOf("success" to true, "order" to order))
    }

    @PostMapping("/orders/{orderId}/rider-status")
    fun updateRiderStatus(
        @PathVariable orderId: String,
        @RequestBody request: UpdateEatsOrderStatusRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val order = eatsOrderService.updateRiderStatus(currentUser.userId, orderId, request.status, request.deliveryPhotoUrl)
        return ResponseEntity.ok(mapOf("success" to true, "order" to order))
    }

    // Real manual trigger for EatsOrderAbandonedDeliveryScheduler's own real 60-second
    // cron -- same "let a coordinator/admin fire the real due sweep on demand rather
    // than waiting on wall-clock time" precedent BikeRentalController.processAbandonedRentals/
    // ParkingController.processAbandonedSessions already establish. Admin-gated since
    // this force-cancels a real other rider's delivery and refunds a real other buyer's
    // money, not a self-service action.
    @PostMapping("/orders/process-abandoned-deliveries")
    @PreAuthorize("hasRole('ADMIN')")
    fun processAbandonedDeliveries(): ResponseEntity<Map<String, Any?>> {
        val due = eatsOrderService.getAbandonedDeliveries()
        val processed = due.mapNotNull { eatsOrderService.forceCancelAbandonedDelivery(it.id) }
        return ResponseEntity.ok(mapOf("success" to true, "processedCount" to processed.size, "orders" to processed))
    }

    // Real Uber Eats post-delivery tip -- see EatsOrderService.tipRider's own doc comment.
    // Real Idempotency-Key required, matching RideController.tipDriver's own identical
    // fix (2026-09-03): without this, a legitimate client-side retry (timeout, double-tap
    // before the button disables) hits EatsOrderAlreadyTippedException and shows the buyer
    // a scary error even though their first tip already succeeded and money already moved.
    @PostMapping("/orders/{orderId}/tip")
    fun tipRider(
        @PathVariable orderId: String,
        @RequestBody request: TipEatsOrderRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/eats/orders/$orderId/tip", idempotencyKey, request) {
            val order = eatsOrderService.tipRider(currentUser.userId, orderId, request.amount)
            200 to mapOf("success" to true, "order" to order)
        }
        return ResponseEntity.status(status).body(body)
    }

    // Real post-delivery ratings & reviews (2026-07-18) -- see EatsReviewService's own
    // doc comment for the full account.
    @PostMapping("/orders/{orderId}/review")
    fun submitReview(
        @PathVariable orderId: String,
        @RequestBody request: SubmitEatsReviewRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val review = eatsReviewService.submitReview(
            currentUser.userId, orderId, request.restaurantRating, request.restaurantComment,
            request.riderRating, request.riderComment, request.photoUrl, request.goodPoints,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "review" to review))
    }

    // Real owner-side reply (2026-07-26) -- see EatsReviewService's own doc comment.
    @PostMapping("/reviews/{reviewId}/reply")
    fun replyToRestaurantReview(
        @PathVariable reviewId: String,
        @RequestBody request: ReplyToEatsReviewRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val review = eatsReviewService.replyToRestaurantReview(currentUser.userId, reviewId, request.reply)
        return ResponseEntity.ok(mapOf("success" to true, "review" to review))
    }

    @GetMapping("/restaurants/{restaurantId}/reviews")
    fun getRestaurantReviews(
        @PathVariable restaurantId: String,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val page = eatsReviewService.getRestaurantReviews(restaurantId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "reviews" to page.content) + pageMeta(page))
    }

    // Real Baemin/Coupang-style "도움돼요" (helpful) idempotent toggle -- see
    // EatsReviewService.toggleHelpful's own doc comment.
    @PostMapping("/reviews/{reviewId}/helpful")
    fun toggleReviewHelpful(
        @PathVariable reviewId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val helpful = eatsReviewService.toggleHelpful(currentUser.userId, reviewId)
        return ResponseEntity.ok(mapOf("success" to true, "helpful" to helpful))
    }

    // Real 배달의민족 리뷰 신고하기 (report a review) -- see EatsReviewService
    // .reportReview's own doc comment. Not money-moving, so no Idempotency-Key required,
    // same simpler discipline toggleReviewHelpful above already follows.
    @PostMapping("/reviews/{reviewId}/report")
    fun reportReview(
        @PathVariable reviewId: String,
        @RequestBody request: ReportEatsReviewRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val report = eatsReviewService.reportReview(currentUser.userId, reviewId, request.reason, request.details)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "report" to report))
    }

    @GetMapping("/restaurants/{restaurantId}/rating")
    fun getRestaurantRating(@PathVariable restaurantId: String): ResponseEntity<Map<String, Any?>> {
        val rating = eatsReviewService.getRestaurantRating(restaurantId)
        return ResponseEntity.ok(mapOf("success" to true, "average" to rating.average, "count" to rating.count))
    }

    // Real preset-tag aggregate (itunda Maps redesign, 2026-08-28) -- see
    // EatsReviewService.restaurantGoodPointCounts' own doc comment. goodPointOptions is
    // the real, fixed vocab clients render the pill-picker from at review-submission
    // time -- never invented client-side.
    @GetMapping("/restaurants/{restaurantId}/good-points")
    fun getRestaurantGoodPoints(@PathVariable restaurantId: String): ResponseEntity<Map<String, Any?>> {
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "counts" to eatsReviewService.restaurantGoodPointCounts(restaurantId),
                "goodPointOptions" to EatsReviewService.EATS_GOOD_POINTS,
            ),
        )
    }

    @GetMapping("/riders/{riderId}/rating")
    fun getRiderRating(@PathVariable riderId: String): ResponseEntity<Map<String, Any?>> {
        val rating = eatsReviewService.getRiderRating(riderId)
        return ResponseEntity.ok(mapOf("success" to true, "average" to rating.average, "count" to rating.count))
    }

    // Real bookmarked/favorited restaurants (2026-07-19) -- see EatsFavoriteService's
    // own doc comment for why add/remove are both deliberately idempotent.
    @PostMapping("/restaurants/{restaurantId}/favorite")
    fun addFavorite(
        @PathVariable restaurantId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val favorite = eatsFavoriteService.addFavorite(currentUser.userId, restaurantId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "favorite" to favorite))
    }

    @DeleteMapping("/restaurants/{restaurantId}/favorite")
    fun removeFavorite(
        @PathVariable restaurantId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        eatsFavoriteService.removeFavorite(currentUser.userId, restaurantId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @GetMapping("/favorites")
    fun getMyFavorites(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = eatsFavoriteService.getMyFavorites(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "favorites" to page.content) + pageMeta(page))
    }

    // Real Baemin-style 찜 리스트 공유하기 (share favorites list) -- see
    // EatsFavoriteService.shareFavoritesToConversation's own doc comment.
    @PostMapping("/favorites/share")
    fun shareFavorites(
        @RequestBody request: ShareFavoritesRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val message = eatsFavoriteService.shareFavoritesToConversation(currentUser.userId, request.conversationId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "message" to message))
    }

    @ExceptionHandler(rw.itunda.eats.NoFavoritesToShareException::class)
    fun handleNoFavoritesToShare(ex: rw.itunda.eats.NoFavoritesToShareException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("NO_FAVORITES_TO_SHARE", ex.message ?: "Bad request"))

    @ExceptionHandler(rw.itunda.messaging.ConversationNotFoundException::class)
    fun handleConversationNotFound(ex: rw.itunda.messaging.ConversationNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("CONVERSATION_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(RestaurantNotFoundException::class)
    fun handleRestaurantNotFound(ex: RestaurantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("RESTAURANT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(RestaurantNoAccountException::class)
    fun handleRestaurantNoAccount(ex: RestaurantNoAccountException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("RESTAURANT_ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(EatsBuyerNoAccountException::class)
    fun handleBuyerNoAccount(ex: EatsBuyerNoAccountException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(EatsMembershipNoAccountException::class)
    fun handleMembershipNoAccount(ex: EatsMembershipNoAccountException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidMembershipDurationException::class)
    fun handleInvalidMembershipDuration(ex: InvalidMembershipDurationException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_MEMBERSHIP_DURATION", ex.message ?: "Bad request"))

    @ExceptionHandler(PlatformMembershipNoAccountException::class)
    fun handlePlatformMembershipNoAccount(ex: PlatformMembershipNoAccountException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidPlatformMembershipDurationException::class)
    fun handleInvalidPlatformMembershipDuration(ex: InvalidPlatformMembershipDurationException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_MEMBERSHIP_DURATION", ex.message ?: "Bad request"))

    @ExceptionHandler(EmptyEatsOrderException::class)
    fun handleEmptyOrder(ex: EmptyEatsOrderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("EMPTY_ORDER", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidEatsDeliveryAddressException::class)
    fun handleInvalidAddress(ex: InvalidEatsDeliveryAddressException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_DELIVERY_ADDRESS", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidEatsCoordinatesException::class)
    fun handleInvalidCoordinates(ex: InvalidEatsCoordinatesException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_COORDINATES", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidEatsDeliveryNotesException::class)
    fun handleInvalidDeliveryNotes(ex: InvalidEatsDeliveryNotesException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_DELIVERY_NOTES", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidEatsQuantityException::class)
    fun handleInvalidQuantity(ex: InvalidEatsQuantityException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_QUANTITY", ex.message ?: "Bad request"))

    @ExceptionHandler(MenuItemNotFoundException::class)
    fun handleMenuItemNotFound(ex: MenuItemNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MENU_ITEM_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(MenuItemSoldOutException::class)
    fun handleMenuItemSoldOut(ex: MenuItemSoldOutException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("MENU_ITEM_SOLD_OUT", ex.message ?: "Conflict"))

    @ExceptionHandler(MenuItemSurplusDealExpiredException::class)
    fun handleMenuItemSurplusDealExpired(ex: MenuItemSurplusDealExpiredException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("SURPLUS_DEAL_EXPIRED", ex.message ?: "This closing deal has expired"))

    @ExceptionHandler(MissingRequiredMenuOptionException::class)
    fun handleMissingRequiredMenuOption(ex: MissingRequiredMenuOptionException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("MISSING_REQUIRED_MENU_OPTION", ex.message ?: "Unprocessable"))

    @ExceptionHandler(InvalidMenuOptionSelectionException::class)
    fun handleInvalidMenuOptionSelection(ex: InvalidMenuOptionSelectionException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_MENU_OPTION_SELECTION", ex.message ?: "Bad request"))

    @ExceptionHandler(SelfEatsOrderException::class)
    fun handleSelfOrder(ex: SelfEatsOrderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("SELF_ORDER_NOT_ALLOWED", ex.message ?: "Bad request"))

    @ExceptionHandler(RestaurantNotAcceptingOrdersException::class)
    fun handleRestaurantNotAcceptingOrders(ex: RestaurantNotAcceptingOrdersException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("RESTAURANT_NOT_ACCEPTING_ORDERS", ex.message ?: "Bad request"))

    @ExceptionHandler(EatsOrderNotFoundException::class)
    fun handleOrderNotFound(ex: EatsOrderNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ORDER_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(EatsOrderNotDeliveredException::class)
    fun handleOrderNotDelivered(ex: EatsOrderNotDeliveredException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("ORDER_NOT_DELIVERED", ex.message ?: "Conflict"))

    @ExceptionHandler(EatsOrderAlreadyTippedException::class)
    fun handleOrderAlreadyTipped(ex: EatsOrderAlreadyTippedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("ORDER_ALREADY_TIPPED", ex.message ?: "Conflict"))

    @ExceptionHandler(EatsOrderTipWindowExpiredException::class)
    fun handleOrderTipWindowExpired(ex: EatsOrderTipWindowExpiredException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("ORDER_TIP_WINDOW_EXPIRED", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidEatsTipAmountException::class)
    fun handleInvalidTipAmount(ex: InvalidEatsTipAmountException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_TIP_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(EatsOrderNoRiderException::class)
    fun handleOrderNoRider(ex: EatsOrderNoRiderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("ORDER_NO_RIDER", ex.message ?: "Bad request"))

    @ExceptionHandler(EatsOrderOwnRestaurantException::class)
    fun handleOwnRestaurant(ex: EatsOrderOwnRestaurantException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("CANNOT_MESSAGE_OWN_RESTAURANT", ex.message ?: "Bad request"))

    @ExceptionHandler(EatsOrderNotYetDeliveredException::class)
    fun handleOrderNotYetDelivered(ex: EatsOrderNotYetDeliveredException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("ORDER_NOT_YET_DELIVERED", ex.message ?: "Conflict"))

    @ExceptionHandler(EatsOrderAlreadyReviewedException::class)
    fun handleOrderAlreadyReviewed(ex: EatsOrderAlreadyReviewedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("ORDER_ALREADY_REVIEWED", ex.message ?: "Conflict"))

    @ExceptionHandler(InvalidEatsRatingException::class)
    fun handleInvalidRating(ex: InvalidEatsRatingException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_RATING", ex.message ?: "Bad request"))

    @ExceptionHandler(EatsReviewNotFoundException::class)
    fun handleReviewNotFound(ex: EatsReviewNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("REVIEW_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidEatsReviewReplyException::class)
    fun handleInvalidReply(ex: InvalidEatsReviewReplyException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_REVIEW_REPLY", ex.message ?: "Bad request"))

    @ExceptionHandler(OwnEatsReviewReportException::class)
    fun handleOwnReviewReport(ex: OwnEatsReviewReportException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("OWN_REVIEW_REPORT", ex.message ?: "Bad request"))

    @ExceptionHandler(EatsReviewAlreadyReportedException::class)
    fun handleReviewAlreadyReported(ex: EatsReviewAlreadyReportedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("REVIEW_ALREADY_REPORTED", ex.message ?: "Conflict"))

    @ExceptionHandler(ScheduledOrdersNotSupportedException::class)
    fun handleScheduledOrdersNotSupported(ex: ScheduledOrdersNotSupportedException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("SCHEDULED_ORDERS_NOT_SUPPORTED", ex.message ?: "Unprocessable"))

    @ExceptionHandler(InvalidScheduledOrderTimeException::class)
    fun handleInvalidScheduledOrderTime(ex: InvalidScheduledOrderTimeException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_SCHEDULED_ORDER_TIME", ex.message ?: "Bad request"))

    @ExceptionHandler(MinOrderAmountNotMetException::class)
    fun handleMinOrderAmountNotMet(ex: MinOrderAmountNotMetException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("MIN_ORDER_AMOUNT_NOT_MET", ex.message ?: "Unprocessable"))

    @ExceptionHandler(InvalidEatsOrderStatusTransitionException::class)
    fun handleInvalidTransition(ex: InvalidEatsOrderStatusTransitionException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("INVALID_ORDER_STATUS_TRANSITION", ex.message ?: "Conflict"))

    @ExceptionHandler(EatsOrderItemNotFoundException::class)
    fun handleOrderItemNotFound(ex: EatsOrderItemNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ORDER_ITEM_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(EatsOrderItemAlreadyUnavailableException::class)
    fun handleOrderItemAlreadyUnavailable(ex: EatsOrderItemAlreadyUnavailableException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("ORDER_ITEM_ALREADY_UNAVAILABLE", ex.message ?: "Conflict"))

    @ExceptionHandler(EatsOrderAllItemsUnavailableException::class)
    fun handleAllItemsUnavailable(ex: EatsOrderAllItemsUnavailableException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("CANNOT_EMPTY_ORDER", ex.message ?: "Unprocessable"))

    @ExceptionHandler(RiderAlreadyRegisteredException::class)
    fun handleRiderAlreadyRegistered(ex: RiderAlreadyRegisteredException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("RIDER_ALREADY_REGISTERED", ex.message ?: "Conflict"))

    @ExceptionHandler(RiderNotRegisteredException::class)
    fun handleRiderNotRegistered(ex: RiderNotRegisteredException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("RIDER_NOT_REGISTERED", ex.message ?: "Not found"))

    @ExceptionHandler(RiderNoAccountException::class)
    fun handleRiderNoAccount(ex: RiderNoAccountException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("RIDER_ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidRiderLocationException::class)
    fun handleInvalidRiderLocation(ex: InvalidRiderLocationException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_RIDER_LOCATION", ex.message ?: "Bad request"))

    @ExceptionHandler(RiderNotAvailableException::class)
    fun handleRiderNotAvailable(ex: RiderNotAvailableException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("RIDER_NOT_AVAILABLE", ex.message ?: "Conflict"))

    @ExceptionHandler(DeliveryAlreadyClaimedException::class)
    fun handleDeliveryAlreadyClaimed(ex: DeliveryAlreadyClaimedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("DELIVERY_ALREADY_CLAIMED", ex.message ?: "Conflict"))

    @ExceptionHandler(RiderAlreadyOnDeliveryException::class)
    fun handleRiderAlreadyOnDelivery(ex: RiderAlreadyOnDeliveryException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("RIDER_ALREADY_ON_DELIVERY", ex.message ?: "Conflict"))

    @ExceptionHandler(NoActiveOfferException::class)
    fun handleNoActiveOffer(ex: NoActiveOfferException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("NO_ACTIVE_OFFER", ex.message ?: "Conflict"))

    @ExceptionHandler(NotAssignedRiderException::class)
    fun handleNotAssignedRider(ex: NotAssignedRiderException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("NOT_ASSIGNED_RIDER", ex.message ?: "Not found"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(AccountFrozenException::class)
    fun handleAccountFrozen(ex: AccountFrozenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("ACCOUNT_FROZEN", ex.message ?: "Account is frozen"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}
