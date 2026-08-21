package rw.itunda.p2p.web

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
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.domain.AutoTransferFrequency
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.p2p.AutoTransferInvalidScheduleException
import rw.itunda.p2p.AutoTransferNotFoundException
import rw.itunda.p2p.AutoTransferService
import rw.itunda.p2p.P2pInvalidAmountException
import rw.itunda.p2p.P2pNoAccountException
import rw.itunda.p2p.P2pRecipientNotFoundException
import rw.itunda.p2p.P2pSelfPaymentException
import java.math.BigDecimal

data class CreateAutoTransferRequest(
    val recipient: String,
    val amount: BigDecimal,
    val frequency: AutoTransferFrequency,
    val dayOfWeek: Int? = null,
    val dayOfMonth: Int? = null,
    val description: String = "",
)

// Real Toss Bank 자동이체 (auto-transfer) equivalent -- see AutoTransfer.kt's own doc
// comment. Lives alongside P2pController since execution reuses P2pService.sendDirect
// unmodified -- this is a different trigger for the same real money movement, not a
// separate payment rail.
@RestController
@RequestMapping("/api/v1/p2p/auto-transfers")
class AutoTransferController(
    private val autoTransferService: AutoTransferService,
    private val idempotencyService: IdempotencyService,
) {

    // Real idempotency fix (item 235, found via a periodic Idempotency-Key coverage
    // audit): `create` was a real gap in this session's own established risk
    // signature -- it creates a brand-new recurring-transfer row with no existing row
    // for a retry to conflict against. A network-timeout retry or a double-tap didn't
    // double-charge immediately (money only moves later when the scheduler processes
    // a due AutoTransfer), but it silently created two active recurring rules to the
    // same recipient -- a duplicate charge every period going forward, not just once,
    // until the user noticed and manually cancelled one.
    @PostMapping
    fun create(
        @RequestBody request: CreateAutoTransferRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/p2p/auto-transfers", idempotencyKey, request) {
            val autoTransfer = autoTransferService.create(
                currentUser.userId, request.recipient, request.amount, request.frequency,
                request.dayOfWeek, request.dayOfMonth, request.description,
            )
            HttpStatus.CREATED.value() to mapOf("success" to true, "autoTransfer" to autoTransfer)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping
    fun getMine(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "autoTransfers" to autoTransferService.getMine(currentUser.userId)))

    @PostMapping("/{id}/pause")
    fun pause(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "autoTransfer" to autoTransferService.pause(currentUser.userId, id)))

    @PostMapping("/{id}/resume")
    fun resume(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "autoTransfer" to autoTransferService.resume(currentUser.userId, id)))

    @DeleteMapping("/{id}")
    fun cancel(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "autoTransfer" to autoTransferService.cancel(currentUser.userId, id)))

    @ExceptionHandler(AutoTransferNotFoundException::class)
    fun handleNotFound(ex: AutoTransferNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("AUTO_TRANSFER_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(AutoTransferInvalidScheduleException::class)
    fun handleInvalidSchedule(ex: AutoTransferInvalidScheduleException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_SCHEDULE", ex.message ?: "Bad request"))

    @ExceptionHandler(P2pRecipientNotFoundException::class)
    fun handleRecipientNotFound(ex: P2pRecipientNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("P2P_RECIPIENT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(P2pSelfPaymentException::class)
    fun handleSelfPayment(ex: P2pSelfPaymentException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("SELF_PAYMENT_NOT_ALLOWED", ex.message ?: "Bad request"))

    @ExceptionHandler(P2pNoAccountException::class)
    fun handleNoAccount(ex: P2pNoAccountException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(P2pInvalidAmountException::class)
    fun handleInvalidAmount(ex: P2pInvalidAmountException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))
}
