package rw.itunda.eats.web

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
import rw.itunda.core.ledger.WalletFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import rw.itunda.eats.DeliveryAlreadyClaimedException
import rw.itunda.eats.RiderAlreadyOnDeliveryException
import rw.itunda.eats.EatsBuyerNoWalletException
import rw.itunda.eats.EatsFavoriteService
import rw.itunda.eats.EatsMembershipNoWalletException
import rw.itunda.eats.EatsMembershipService
import rw.itunda.eats.InvalidMembershipDurationException
import rw.itunda.eats.EatsOrderAlreadyReviewedException
import rw.itunda.eats.EatsOrderItemRequest
import rw.itunda.eats.EatsOrderNotFoundException
import rw.itunda.eats.EatsOrderNotYetDeliveredException
import rw.itunda.eats.EatsOrderService
import rw.itunda.eats.EatsReviewNotFoundException
import rw.itunda.eats.EatsReviewService
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
import rw.itunda.eats.MissingRequiredMenuOptionException
import rw.itunda.eats.NoActiveOfferException
import rw.itunda.eats.NotAssignedRiderException
import rw.itunda.eats.RestaurantNoWalletException
import rw.itunda.eats.RestaurantNotFoundException
import rw.itunda.eats.RiderAlreadyRegisteredException
import rw.itunda.eats.RiderNoWalletException
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
data class UpdateEatsOrderStatusRequest(val status: EatsOrderStatus)
data class SetRiderAvailabilityRequest(val available: Boolean)
data class UpdateRiderLocationRequest(val latitude: Double, val longitude: Double)
data class SubmitEatsReviewRequest(
    val restaurantRating: Int,
    val restaurantComment: String? = null,
    // Nullable (2026-07-26) -- a real Baemin-style PICKUP order has no rider to rate;
    // see EatsReview.kt's own doc comment for the full account.
    val riderRating: Int? = null,
    val riderComment: String? = null,
)
data class ReplyToEatsReviewRequest(val reply: String)

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
    private val idempotencyService: IdempotencyService,
) {
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
        return ResponseEntity.ok(mapOf("success" to true, "orders" to page.content) + pageMeta(page))
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

    @PostMapping("/orders/{orderId}/status")
    fun updateRestaurantStatus(
        @PathVariable orderId: String,
        @RequestBody request: UpdateEatsOrderStatusRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val order = eatsOrderService.updateRestaurantStatus(currentUser.userId, orderId, request.status)
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
        val order = eatsOrderService.updateRiderStatus(currentUser.userId, orderId, request.status)
        return ResponseEntity.ok(mapOf("success" to true, "order" to order))
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
            request.riderRating, request.riderComment,
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

    @GetMapping("/restaurants/{restaurantId}/rating")
    fun getRestaurantRating(@PathVariable restaurantId: String): ResponseEntity<Map<String, Any?>> {
        val rating = eatsReviewService.getRestaurantRating(restaurantId)
        return ResponseEntity.ok(mapOf("success" to true, "average" to rating.average, "count" to rating.count))
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

    @ExceptionHandler(RestaurantNotFoundException::class)
    fun handleRestaurantNotFound(ex: RestaurantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("RESTAURANT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(RestaurantNoWalletException::class)
    fun handleRestaurantNoWallet(ex: RestaurantNoWalletException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("RESTAURANT_WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(EatsBuyerNoWalletException::class)
    fun handleBuyerNoWallet(ex: EatsBuyerNoWalletException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(EatsMembershipNoWalletException::class)
    fun handleMembershipNoWallet(ex: EatsMembershipNoWalletException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidMembershipDurationException::class)
    fun handleInvalidMembershipDuration(ex: InvalidMembershipDurationException) =
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

    @ExceptionHandler(MissingRequiredMenuOptionException::class)
    fun handleMissingRequiredMenuOption(ex: MissingRequiredMenuOptionException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("MISSING_REQUIRED_MENU_OPTION", ex.message ?: "Unprocessable"))

    @ExceptionHandler(InvalidMenuOptionSelectionException::class)
    fun handleInvalidMenuOptionSelection(ex: InvalidMenuOptionSelectionException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_MENU_OPTION_SELECTION", ex.message ?: "Bad request"))

    @ExceptionHandler(SelfEatsOrderException::class)
    fun handleSelfOrder(ex: SelfEatsOrderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("SELF_ORDER_NOT_ALLOWED", ex.message ?: "Bad request"))

    @ExceptionHandler(EatsOrderNotFoundException::class)
    fun handleOrderNotFound(ex: EatsOrderNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ORDER_NOT_FOUND", ex.message ?: "Not found"))

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

    @ExceptionHandler(RiderAlreadyRegisteredException::class)
    fun handleRiderAlreadyRegistered(ex: RiderAlreadyRegisteredException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("RIDER_ALREADY_REGISTERED", ex.message ?: "Conflict"))

    @ExceptionHandler(RiderNotRegisteredException::class)
    fun handleRiderNotRegistered(ex: RiderNotRegisteredException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("RIDER_NOT_REGISTERED", ex.message ?: "Not found"))

    @ExceptionHandler(RiderNoWalletException::class)
    fun handleRiderNoWallet(ex: RiderNoWalletException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("RIDER_WALLET_NOT_FOUND", ex.message ?: "Not found"))

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

    @ExceptionHandler(WalletFrozenException::class)
    fun handleWalletFrozen(ex: WalletFrozenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("WALLET_FROZEN", ex.message ?: "Wallet is frozen"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}
