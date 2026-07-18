package rw.itunda.eats.web

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
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
import rw.itunda.eats.EatsBuyerNoWalletException
import rw.itunda.eats.EatsOrderItemRequest
import rw.itunda.eats.EatsOrderNotFoundException
import rw.itunda.eats.EatsOrderService
import rw.itunda.eats.EmptyEatsOrderException
import rw.itunda.eats.InvalidEatsDeliveryAddressException
import rw.itunda.eats.InvalidEatsOrderStatusTransitionException
import rw.itunda.eats.InvalidEatsQuantityException
import rw.itunda.eats.MenuItemNotFoundException
import rw.itunda.eats.NotAssignedRiderException
import rw.itunda.eats.RestaurantNoWalletException
import rw.itunda.eats.RestaurantNotFoundException
import rw.itunda.eats.RiderAlreadyRegisteredException
import rw.itunda.eats.RiderNoWalletException
import rw.itunda.eats.RiderNotAvailableException
import rw.itunda.eats.RiderNotRegisteredException
import rw.itunda.eats.RiderService
import rw.itunda.eats.SelfEatsOrderException

data class PlaceEatsOrderRequest(val restaurantId: String, val items: List<EatsOrderItemRequest>, val deliveryAddress: String)
data class UpdateEatsOrderStatusRequest(val status: EatsOrderStatus)
data class SetRiderAvailabilityRequest(val available: Boolean)

// Real Coupang Eats-style food ordering + delivery. Restaurant browsing/menus
// deliberately reuse the existing GET /api/v1/shopping/merchants and GET
// /api/v1/shopping/merchants/{id}/products endpoints (a restaurant IS a Merchant, a menu
// item IS a MerchantProduct) -- no duplicate catalog-browsing endpoint added here. See
// EatsOrderService's own doc comment for the full account, including the honest "flat
// delivery fee, no real geo/distance data" scope. Normal itunda-user JWT gate.
@RestController
@RequestMapping("/api/v1/eats")
class EatsController(
    private val riderService: RiderService,
    private val eatsOrderService: EatsOrderService,
    private val idempotencyService: IdempotencyService,
) {
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

    @PostMapping("/orders")
    fun placeOrder(
        @RequestBody request: PlaceEatsOrderRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/eats/orders", idempotencyKey, request) {
            val detail = eatsOrderService.placeOrder(currentUser.userId, request.restaurantId, request.items, request.deliveryAddress)
            201 to mapOf("success" to true, "order" to detail.order, "items" to detail.items)
        }
        return ResponseEntity.status(status).body(body)
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
    fun getAvailableDeliveries(@PageableDefault(size = 20) pageable: Pageable): ResponseEntity<Map<String, Any?>> {
        val page = eatsOrderService.getAvailableDeliveries(pageable)
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

    @PostMapping("/orders/{orderId}/status")
    fun updateRestaurantStatus(
        @PathVariable orderId: String,
        @RequestBody request: UpdateEatsOrderStatusRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val order = eatsOrderService.updateRestaurantStatus(currentUser.userId, orderId, request.status)
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

    @PostMapping("/orders/{orderId}/rider-status")
    fun updateRiderStatus(
        @PathVariable orderId: String,
        @RequestBody request: UpdateEatsOrderStatusRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val order = eatsOrderService.updateRiderStatus(currentUser.userId, orderId, request.status)
        return ResponseEntity.ok(mapOf("success" to true, "order" to order))
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

    @ExceptionHandler(EmptyEatsOrderException::class)
    fun handleEmptyOrder(ex: EmptyEatsOrderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("EMPTY_ORDER", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidEatsDeliveryAddressException::class)
    fun handleInvalidAddress(ex: InvalidEatsDeliveryAddressException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_DELIVERY_ADDRESS", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidEatsQuantityException::class)
    fun handleInvalidQuantity(ex: InvalidEatsQuantityException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_QUANTITY", ex.message ?: "Bad request"))

    @ExceptionHandler(MenuItemNotFoundException::class)
    fun handleMenuItemNotFound(ex: MenuItemNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MENU_ITEM_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(SelfEatsOrderException::class)
    fun handleSelfOrder(ex: SelfEatsOrderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("SELF_ORDER_NOT_ALLOWED", ex.message ?: "Bad request"))

    @ExceptionHandler(EatsOrderNotFoundException::class)
    fun handleOrderNotFound(ex: EatsOrderNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ORDER_NOT_FOUND", ex.message ?: "Not found"))

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

    @ExceptionHandler(RiderNotAvailableException::class)
    fun handleRiderNotAvailable(ex: RiderNotAvailableException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("RIDER_NOT_AVAILABLE", ex.message ?: "Conflict"))

    @ExceptionHandler(DeliveryAlreadyClaimedException::class)
    fun handleDeliveryAlreadyClaimed(ex: DeliveryAlreadyClaimedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("DELIVERY_ALREADY_CLAIMED", ex.message ?: "Conflict"))

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
}
