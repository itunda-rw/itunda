package rw.itunda.commerce.web

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
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.commerce.BuyerNoAccountException
import rw.itunda.commerce.EmptyOrderException
import rw.itunda.commerce.InvalidDeliveryAddressException
import rw.itunda.commerce.InvalidProductSubscriptionException
import rw.itunda.commerce.InvalidQuantityException
import rw.itunda.commerce.MerchantNoAccountException
import rw.itunda.commerce.MerchantNotFoundException
import rw.itunda.commerce.MinOrderAmountNotMetException
import rw.itunda.commerce.ProductSubscriptionNotFoundException
import rw.itunda.commerce.ProductSubscriptionProductNotFoundException
import rw.itunda.commerce.ProductSubscriptionService
import rw.itunda.commerce.SelfOrderException
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError

data class CreateProductSubscriptionRequest(
    val merchantId: String, val productId: String, val quantity: Int, val intervalDays: Int, val deliveryAddress: String,
)
data class UpdateProductSubscriptionRequest(val quantity: Int? = null, val intervalDays: Int? = null)

// Real Coupang 정기배송 (subscribe & save)-style recurring product delivery -- see
// ProductSubscriptionService's own doc comment for the full sourced account.
@RestController
@RequestMapping("/api/v1/product-subscriptions")
class ProductSubscriptionController(
    private val productSubscriptionService: ProductSubscriptionService,
    private val idempotencyService: IdempotencyService,
) {

    @PostMapping
    fun subscribe(
        @RequestBody request: CreateProductSubscriptionRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/product-subscriptions", idempotencyKey, request) {
            val subscription = productSubscriptionService.subscribe(
                currentUser.userId, request.merchantId, request.productId, request.quantity, request.intervalDays, request.deliveryAddress,
            )
            201 to mapOf("success" to true, "subscription" to subscription)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping
    fun getMine(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "subscriptions" to productSubscriptionService.getMine(currentUser.userId)))

    @PostMapping("/{id}/pause")
    fun pause(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "subscription" to productSubscriptionService.pause(currentUser.userId, id)))

    @PostMapping("/{id}/resume")
    fun resume(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "subscription" to productSubscriptionService.resume(currentUser.userId, id)))

    @PostMapping("/{id}/cancel")
    fun cancel(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "subscription" to productSubscriptionService.cancel(currentUser.userId, id)))

    // Real Coupang 정기배송 "건너뛰기" (skip next delivery) -- see
    // ProductSubscriptionService.skipNext's own doc comment.
    @PostMapping("/{id}/skip-next")
    fun skipNext(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "subscription" to productSubscriptionService.skipNext(currentUser.userId, id)))

    // Real Coupang 정기배송 수량/주기 변경 -- see
    // ProductSubscriptionService.updateSubscription's own doc comment.
    @PostMapping("/{id}/update")
    fun update(
        @PathVariable id: String,
        @RequestBody request: UpdateProductSubscriptionRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "subscription" to productSubscriptionService.updateSubscription(currentUser.userId, id, request.quantity, request.intervalDays)))

    @ExceptionHandler(ProductSubscriptionNotFoundException::class)
    fun handleNotFound(ex: ProductSubscriptionNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("PRODUCT_SUBSCRIPTION_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(ProductSubscriptionProductNotFoundException::class)
    fun handleProductNotFound(ex: ProductSubscriptionProductNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("PRODUCT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidProductSubscriptionException::class)
    fun handleInvalid(ex: InvalidProductSubscriptionException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_PRODUCT_SUBSCRIPTION", ex.message ?: "Bad request"))

    @ExceptionHandler(MerchantNotFoundException::class)
    fun handleMerchantNotFound(ex: MerchantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(SelfOrderException::class)
    fun handleSelfOrder(ex: SelfOrderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("SELF_ORDER_NOT_ALLOWED", ex.message ?: "Bad request"))

    @ExceptionHandler(EmptyOrderException::class)
    fun handleEmptyOrder(ex: EmptyOrderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("EMPTY_ORDER", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidDeliveryAddressException::class)
    fun handleInvalidAddress(ex: InvalidDeliveryAddressException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_DELIVERY_ADDRESS", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidQuantityException::class)
    fun handleInvalidQuantity(ex: InvalidQuantityException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_QUANTITY", ex.message ?: "Bad request"))

    @ExceptionHandler(MinOrderAmountNotMetException::class)
    fun handleMinOrderAmountNotMet(ex: MinOrderAmountNotMetException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("MIN_ORDER_AMOUNT_NOT_MET", ex.message ?: "Unprocessable"))

    @ExceptionHandler(BuyerNoAccountException::class, MerchantNoAccountException::class)
    fun handleNoAccount(ex: RuntimeException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleIdempotencyConflict(ex: IdempotencyConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleIdempotencyInProgress(ex: IdempotencyInProgressException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))
}
