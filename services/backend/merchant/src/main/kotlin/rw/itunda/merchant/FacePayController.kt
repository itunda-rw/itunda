package rw.itunda.merchant

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.AccountFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError

@RestController
@RequestMapping("/api/v1/facepay")
class FacePayController(
    private val facePayService: FacePayService,
    private val idempotencyService: IdempotencyService,
) {
    @PostMapping("/enroll")
    fun enroll(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "enrollment" to facePayService.enroll(currentUser.userId)))

    @PostMapping("/revoke")
    fun revoke(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "enrollment" to facePayService.revoke(currentUser.userId)))

    @GetMapping("/status")
    fun status(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val enrollment = facePayService.status(currentUser.userId)
        return ResponseEntity.ok(mapOf("success" to true, "enrolled" to (enrollment != null), "enrollment" to enrollment))
    }

    @PostMapping("/collect/{intentId}")
    fun collect(
        @PathVariable intentId: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/facepay/collect/$intentId", idempotencyKey, intentId) {
            200 to facePayService.collect(currentUser.userId, intentId)
        }
        return ResponseEntity.status(status).body(body)
    }

    @ExceptionHandler(FacePayNotEnrolledException::class)
    fun handleNotEnrolled(ex: FacePayNotEnrolledException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("FACEPAY_NOT_ENROLLED", ex.message ?: "Forbidden"))

    @ExceptionHandler(MerchantNotFoundException::class)
    fun handleMerchantNotFound(ex: MerchantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(MerchantNoAccountException::class)
    fun handleNoAccount(ex: MerchantNoAccountException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

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

    @ExceptionHandler(AccountFrozenException::class)
    fun handleAccountFrozen(ex: AccountFrozenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("ACCOUNT_FROZEN", ex.message ?: "Account is frozen"))
}
