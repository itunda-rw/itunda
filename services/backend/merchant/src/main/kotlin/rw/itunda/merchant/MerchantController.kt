package rw.itunda.merchant

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
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal

data class RegisterMerchantRequest(val businessName: String)
data class GenerateQrRequest(val amount: BigDecimal, val description: String)
data class SetWebhookUrlRequest(val webhookUrl: String)

@RestController
@RequestMapping("/api/v1/merchant")
class MerchantController(
    private val merchantService: MerchantService,
    private val idempotencyService: IdempotencyService,
) {
    @PostMapping("/register")
    fun register(
        @RequestBody request: RegisterMerchantRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val merchant = merchantService.register(currentUser.userId, request.businessName)
        return ResponseEntity.ok(mapOf("success" to true, "merchant" to merchant))
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "merchant" to merchantService.getMyMerchant(currentUser.userId)))

    @PostMapping("/qr/generate")
    fun generateQr(
        @RequestBody request: GenerateQrRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val intent = merchantService.generateQr(currentUser.userId, request.amount, request.description)
        return ResponseEntity.ok(mapOf("success" to true, "paymentIntent" to intent))
    }

    // Real webhook registration (2026-07-13) -- see docs/TOSS_PARITY_MATRIX.md's Merchant
    // row and WebhookDeliveryService for the real delivery mechanism this feeds.
    @PostMapping("/webhook-url")
    fun setWebhookUrl(
        @RequestBody request: SetWebhookUrlRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val merchant = merchantService.setWebhookUrl(currentUser.userId, request.webhookUrl)
        return ResponseEntity.ok(mapOf("success" to true, "merchant" to merchant))
    }

    @PostMapping("/collect/{intentId}")
    fun collect(
        @PathVariable intentId: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/merchant/collect/$intentId", idempotencyKey, intentId) {
            200 to merchantService.collect(currentUser.userId, intentId)
        }
        return ResponseEntity.status(status).body(body)
    }

    @ExceptionHandler(MerchantAlreadyRegisteredException::class)
    fun handleAlreadyRegistered(ex: MerchantAlreadyRegisteredException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("MERCHANT_ALREADY_REGISTERED", ex.message ?: "Conflict"))

    @ExceptionHandler(MerchantNotFoundException::class)
    fun handleNotFound(ex: MerchantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(MerchantNoWalletException::class)
    fun handleNoWallet(ex: MerchantNoWalletException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(PaymentIntentNotFoundException::class)
    fun handleIntentNotFound(ex: PaymentIntentNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("PAYMENT_CODE_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(PaymentIntentNotPayableException::class)
    fun handleIntentNotPayable(ex: PaymentIntentNotPayableException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("PAYMENT_CODE_NOT_PAYABLE", ex.message ?: "Conflict"))

    @ExceptionHandler(SelfPaymentException::class)
    fun handleSelfPayment(ex: SelfPaymentException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("SELF_PAYMENT_NOT_ALLOWED", ex.message ?: "Bad request"))

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
}
