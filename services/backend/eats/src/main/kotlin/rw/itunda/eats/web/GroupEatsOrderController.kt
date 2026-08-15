package rw.itunda.eats.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.domain.EatsFulfillmentType
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.WalletFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.eats.EatsBuyerNoWalletException
import rw.itunda.eats.EmptyEatsOrderException
import rw.itunda.eats.EatsOrderItemRequest
import rw.itunda.eats.GroupEatsOrderEmptyException
import rw.itunda.eats.GroupEatsOrderInvalidJoinCodeException
import rw.itunda.eats.GroupEatsOrderNotFoundException
import rw.itunda.eats.GroupEatsOrderNotHostException
import rw.itunda.eats.GroupEatsOrderNotOpenException
import rw.itunda.eats.GroupEatsOrderService
import rw.itunda.eats.InvalidEatsCoordinatesException
import rw.itunda.eats.InvalidEatsDeliveryAddressException
import rw.itunda.eats.InvalidEatsQuantityException
import rw.itunda.eats.MenuItemNotFoundException
import rw.itunda.eats.MinOrderAmountNotMetException
import rw.itunda.eats.MissingRequiredMenuOptionException
import rw.itunda.eats.InvalidMenuOptionSelectionException
import rw.itunda.eats.RestaurantNoWalletException
import rw.itunda.eats.RestaurantNotFoundException
import rw.itunda.eats.SelfEatsOrderException

data class CreateGroupEatsOrderRequest(
    val restaurantId: String,
    val deliveryAddress: String = "",
    val deliveryLatitude: Double? = null,
    val deliveryLongitude: Double? = null,
    val fulfillmentType: EatsFulfillmentType = EatsFulfillmentType.DELIVERY,
)
data class JoinGroupEatsOrderRequest(val joinCode: String)
data class SetGroupEatsOrderItemsRequest(val items: List<EatsOrderItemRequest>)

// Real 배달의민족 함께주문 (Baemin "Together Order") -- see GroupEatsOrderService's own
// doc comment for the full account. A pre-checkout shared-cart layer in front of the
// existing, unchanged POST /api/v1/eats/orders.
@RestController
@RequestMapping("/api/v1/eats/group-orders")
class GroupEatsOrderController(
    private val groupEatsOrderService: GroupEatsOrderService,
    private val idempotencyService: IdempotencyService,
) {
    @PostMapping
    fun create(
        @RequestBody request: CreateGroupEatsOrderRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val groupOrder = groupEatsOrderService.create(
            currentUser.userId, request.restaurantId, request.deliveryAddress,
            request.deliveryLatitude, request.deliveryLongitude, request.fulfillmentType,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "groupOrder" to groupOrder))
    }

    @PostMapping("/join")
    fun join(
        @RequestBody request: JoinGroupEatsOrderRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val groupOrder = groupEatsOrderService.join(currentUser.userId, request.joinCode)
        return ResponseEntity.ok(mapOf("success" to true, "groupOrder" to groupOrder))
    }

    @GetMapping("/{groupOrderId}")
    fun getDetail(
        @PathVariable groupOrderId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val detail = groupEatsOrderService.getDetail(currentUser.userId, groupOrderId)
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "groupOrder" to detail.groupOrder,
                "grandTotal" to detail.grandTotal,
                "participants" to detail.participants.map { p ->
                    mapOf(
                        "userId" to p.participant.userId,
                        "joinedAt" to p.participant.joinedAt,
                        "subtotal" to p.subtotal,
                        "items" to p.items.map { i ->
                            mapOf(
                                "productId" to i.item.productId,
                                "productName" to i.productName,
                                "quantity" to i.item.quantity,
                                "unitPrice" to i.item.unitPriceSnapshot,
                                "lineTotal" to i.lineTotal,
                            )
                        },
                    )
                },
            ),
        )
    }

    @PostMapping("/{groupOrderId}/items")
    fun setMyItems(
        @PathVariable groupOrderId: String,
        @RequestBody request: SetGroupEatsOrderItemsRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        groupEatsOrderService.setMyItems(currentUser.userId, groupOrderId, request.items)
        return getDetail(groupOrderId, currentUser)
    }

    // Money-moving (places one real order + posts real split-bill requests) -- same
    // Idempotency-Key discipline as POST /api/v1/eats/orders.
    @PostMapping("/{groupOrderId}/finalize")
    fun finalize(
        @PathVariable groupOrderId: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/eats/group-orders/$groupOrderId/finalize", idempotencyKey, groupOrderId) {
            val detail = groupEatsOrderService.finalizeOrder(currentUser.userId, groupOrderId)
            201 to mapOf("success" to true, "order" to detail.order, "items" to detail.items)
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/{groupOrderId}/cancel")
    fun cancel(
        @PathVariable groupOrderId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val groupOrder = groupEatsOrderService.cancel(currentUser.userId, groupOrderId)
        return ResponseEntity.ok(mapOf("success" to true, "groupOrder" to groupOrder))
    }

    @ExceptionHandler(GroupEatsOrderNotFoundException::class)
    fun handleNotFound(ex: GroupEatsOrderNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("GROUP_ORDER_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(GroupEatsOrderNotOpenException::class)
    fun handleNotOpen(ex: GroupEatsOrderNotOpenException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("GROUP_ORDER_NOT_OPEN", ex.message ?: "Conflict"))

    @ExceptionHandler(GroupEatsOrderNotHostException::class)
    fun handleNotHost(ex: GroupEatsOrderNotHostException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("NOT_GROUP_ORDER_HOST", ex.message ?: "Forbidden"))

    @ExceptionHandler(GroupEatsOrderEmptyException::class)
    fun handleEmpty(ex: GroupEatsOrderEmptyException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("GROUP_ORDER_EMPTY", ex.message ?: "Bad request"))

    @ExceptionHandler(GroupEatsOrderInvalidJoinCodeException::class)
    fun handleInvalidJoinCode(ex: GroupEatsOrderInvalidJoinCodeException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("INVALID_JOIN_CODE", ex.message ?: "Not found"))

    @ExceptionHandler(RestaurantNotFoundException::class)
    fun handleRestaurantNotFound(ex: RestaurantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("RESTAURANT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(RestaurantNoWalletException::class)
    fun handleRestaurantNoWallet(ex: RestaurantNoWalletException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("RESTAURANT_WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(EatsBuyerNoWalletException::class)
    fun handleBuyerNoWallet(ex: EatsBuyerNoWalletException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidEatsDeliveryAddressException::class)
    fun handleInvalidAddress(ex: InvalidEatsDeliveryAddressException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_DELIVERY_ADDRESS", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidEatsCoordinatesException::class)
    fun handleInvalidCoordinates(ex: InvalidEatsCoordinatesException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_COORDINATES", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidEatsQuantityException::class)
    fun handleInvalidQuantity(ex: InvalidEatsQuantityException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_QUANTITY", ex.message ?: "Bad request"))

    @ExceptionHandler(MenuItemNotFoundException::class)
    fun handleMenuItemNotFound(ex: MenuItemNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MENU_ITEM_NOT_FOUND", ex.message ?: "Not found"))

    // The following can all legitimately surface from finalize()'s internal call to the
    // unchanged EatsOrderService.placeOrder -- same real validations a normal single-buyer
    // order gets, same status/code as EatsController's own handlers for them.
    @ExceptionHandler(MinOrderAmountNotMetException::class)
    fun handleMinOrderAmountNotMet(ex: MinOrderAmountNotMetException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("MIN_ORDER_AMOUNT_NOT_MET", ex.message ?: "Unprocessable"))

    @ExceptionHandler(SelfEatsOrderException::class)
    fun handleSelfOrder(ex: SelfEatsOrderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("SELF_ORDER_NOT_ALLOWED", ex.message ?: "Bad request"))

    @ExceptionHandler(MissingRequiredMenuOptionException::class)
    fun handleMissingRequiredMenuOption(ex: MissingRequiredMenuOptionException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("MISSING_REQUIRED_MENU_OPTION", ex.message ?: "Unprocessable"))

    @ExceptionHandler(InvalidMenuOptionSelectionException::class)
    fun handleInvalidMenuOptionSelection(ex: InvalidMenuOptionSelectionException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_MENU_OPTION_SELECTION", ex.message ?: "Bad request"))

    @ExceptionHandler(EmptyEatsOrderException::class)
    fun handleEmptyOrder(ex: EmptyEatsOrderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("EMPTY_ORDER", ex.message ?: "Bad request"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(WalletFrozenException::class)
    fun handleWalletFrozen(ex: WalletFrozenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("WALLET_FROZEN", ex.message ?: "Wallet is frozen"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}
