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
import rw.itunda.core.domain.DineInOrderStatus
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.WalletFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import rw.itunda.eats.DineInBuyerNoWalletException
import rw.itunda.eats.DineInMenuItemNotFoundException
import rw.itunda.eats.DineInMenuItemSoldOutException
import rw.itunda.eats.DineInMenuItemSurplusDealExpiredException
import rw.itunda.eats.DineInOrderItemRequest
import rw.itunda.eats.DineInOrderNotFoundException
import rw.itunda.eats.DineInOrderService
import rw.itunda.eats.DineInRestaurantNoWalletException
import rw.itunda.eats.DineInRestaurantNotFoundException
import rw.itunda.eats.EmptyDineInOrderException
import rw.itunda.eats.InvalidDineInMenuOptionSelectionException
import rw.itunda.eats.InvalidDineInQuantityException
import rw.itunda.eats.InvalidDineInStatusTransitionException
import rw.itunda.eats.InvalidDineInTableException
import rw.itunda.eats.MissingRequiredDineInMenuOptionException
import rw.itunda.eats.SelfDineInOrderException

data class PlaceDineInOrderRequest(
    val restaurantId: String,
    val tableNumber: String,
    val items: List<DineInOrderItemRequest>,
    val notes: String? = null,
)
data class UpdateDineInOrderStatusRequest(val status: DineInOrderStatus)

// Real 배민오더-style table/QR in-store ordering. Restaurant browsing/menus deliberately
// reuse the existing GET /api/v1/shopping/merchants and GET
// /api/v1/shopping/merchants/{id}/products endpoints -- same "no duplicate catalog
// endpoint" discipline EatsController itself already established. See
// DineInOrderService's own doc comment for the full account. Normal itunda-user JWT gate.
@RestController
@RequestMapping("/api/v1/eats/dine-in")
class DineInOrderController(
    private val dineInOrderService: DineInOrderService,
    private val idempotencyService: IdempotencyService,
) {
    @PostMapping("/orders")
    fun placeOrder(
        @RequestBody request: PlaceDineInOrderRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/eats/dine-in/orders", idempotencyKey, request) {
            val detail = dineInOrderService.placeOrder(
                currentUser.userId, request.restaurantId, request.tableNumber, request.items, request.notes,
            )
            201 to mapOf("success" to true, "order" to detail.order, "items" to detail.items)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/orders/my-orders")
    fun getMyOrders(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = dineInOrderService.getMyOrders(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "orders" to page.content) + pageMeta(page))
    }

    @GetMapping("/orders/restaurant-orders")
    fun getRestaurantOrders(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = dineInOrderService.getRestaurantOrders(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "orders" to page.content) + pageMeta(page))
    }

    @GetMapping("/orders/{orderId}")
    fun getOrder(
        @PathVariable orderId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val detail = dineInOrderService.getOrderDetail(currentUser.userId, orderId)
        return ResponseEntity.ok(mapOf("success" to true, "order" to detail.order, "items" to detail.items))
    }

    @PostMapping("/orders/{orderId}/status")
    fun updateStatus(
        @PathVariable orderId: String,
        @RequestBody request: UpdateDineInOrderStatusRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val order = dineInOrderService.updateStatus(currentUser.userId, orderId, request.status)
        return ResponseEntity.ok(mapOf("success" to true, "order" to order))
    }

    @PostMapping("/orders/{orderId}/cancel")
    fun cancelOrder(
        @PathVariable orderId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val order = dineInOrderService.cancelOrder(currentUser.userId, orderId)
        return ResponseEntity.ok(mapOf("success" to true, "order" to order))
    }

    @ExceptionHandler(DineInRestaurantNotFoundException::class)
    fun handleRestaurantNotFound(ex: DineInRestaurantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("RESTAURANT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(DineInRestaurantNoWalletException::class)
    fun handleRestaurantNoWallet(ex: DineInRestaurantNoWalletException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("RESTAURANT_WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(DineInBuyerNoWalletException::class)
    fun handleBuyerNoWallet(ex: DineInBuyerNoWalletException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(EmptyDineInOrderException::class)
    fun handleEmptyOrder(ex: EmptyDineInOrderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("EMPTY_ORDER", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidDineInTableException::class)
    fun handleInvalidTable(ex: InvalidDineInTableException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_TABLE_NUMBER", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidDineInQuantityException::class)
    fun handleInvalidQuantity(ex: InvalidDineInQuantityException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_QUANTITY", ex.message ?: "Bad request"))

    @ExceptionHandler(DineInMenuItemNotFoundException::class)
    fun handleMenuItemNotFound(ex: DineInMenuItemNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MENU_ITEM_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(DineInMenuItemSoldOutException::class)
    fun handleMenuItemSoldOut(ex: DineInMenuItemSoldOutException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("MENU_ITEM_SOLD_OUT", ex.message ?: "Conflict"))

    @ExceptionHandler(DineInMenuItemSurplusDealExpiredException::class)
    fun handleMenuItemSurplusDealExpired(ex: DineInMenuItemSurplusDealExpiredException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("SURPLUS_DEAL_EXPIRED", ex.message ?: "This closing deal has expired"))

    @ExceptionHandler(MissingRequiredDineInMenuOptionException::class)
    fun handleMissingRequiredMenuOption(ex: MissingRequiredDineInMenuOptionException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("MISSING_REQUIRED_MENU_OPTION", ex.message ?: "Unprocessable"))

    @ExceptionHandler(InvalidDineInMenuOptionSelectionException::class)
    fun handleInvalidMenuOptionSelection(ex: InvalidDineInMenuOptionSelectionException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_MENU_OPTION_SELECTION", ex.message ?: "Bad request"))

    @ExceptionHandler(SelfDineInOrderException::class)
    fun handleSelfOrder(ex: SelfDineInOrderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("SELF_ORDER_NOT_ALLOWED", ex.message ?: "Bad request"))

    @ExceptionHandler(DineInOrderNotFoundException::class)
    fun handleOrderNotFound(ex: DineInOrderNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ORDER_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidDineInStatusTransitionException::class)
    fun handleInvalidTransition(ex: InvalidDineInStatusTransitionException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("INVALID_ORDER_STATUS_TRANSITION", ex.message ?: "Conflict"))

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
