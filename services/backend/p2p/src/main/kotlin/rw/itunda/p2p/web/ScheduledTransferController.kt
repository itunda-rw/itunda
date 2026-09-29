package rw.itunda.p2p.web

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
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.p2p.P2pInvalidAmountException
import rw.itunda.p2p.P2pNoAccountException
import rw.itunda.p2p.P2pRecipientNotFoundException
import rw.itunda.p2p.P2pSelfPaymentException
import rw.itunda.p2p.ScheduledTransferInvalidDateException
import rw.itunda.p2p.ScheduledTransferNotFoundException
import rw.itunda.p2p.ScheduledTransferNotPendingException
import rw.itunda.p2p.ScheduledTransferService
import java.math.BigDecimal
import java.time.LocalDate

data class CreateScheduledTransferRequest(
    val recipient: String,
    val amount: BigDecimal,
    val scheduledDate: LocalDate,
    val description: String = "",
)

// Real Toss 예약송금 (scheduled/reserved one-time transfer) equivalent -- see
// ScheduledTransfer.kt's own doc comment. Lives alongside P2pController/
// AutoTransferController since execution reuses P2pService.sendDirect unmodified.
@RestController
@RequestMapping("/api/v1/p2p/scheduled-transfers")
class ScheduledTransferController(
    private val scheduledTransferService: ScheduledTransferService,
    private val idempotencyService: IdempotencyService,
) {

    @PostMapping
    fun create(
        @RequestBody request: CreateScheduledTransferRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/p2p/scheduled-transfers", idempotencyKey, request) {
            val scheduledTransfer = scheduledTransferService.create(
                currentUser.userId, request.recipient, request.amount, request.scheduledDate, request.description,
            )
            201 to mapOf("success" to true, "scheduledTransfer" to scheduledTransfer)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping
    fun getMine(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "scheduledTransfers" to scheduledTransferService.getMine(currentUser.userId)))

    @PostMapping("/{id}/cancel")
    fun cancel(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "scheduledTransfer" to scheduledTransferService.cancel(currentUser.userId, id)))

    @ExceptionHandler(ScheduledTransferNotFoundException::class)
    fun handleNotFound(ex: ScheduledTransferNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("SCHEDULED_TRANSFER_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(ScheduledTransferNotPendingException::class)
    fun handleNotPending(ex: ScheduledTransferNotPendingException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("SCHEDULED_TRANSFER_NOT_PENDING", ex.message ?: "Conflict"))

    @ExceptionHandler(ScheduledTransferInvalidDateException::class)
    fun handleInvalidDate(ex: ScheduledTransferInvalidDateException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_SCHEDULED_DATE", ex.message ?: "Bad request"))

    @ExceptionHandler(P2pRecipientNotFoundException::class)
    fun handleRecipientNotFound(ex: P2pRecipientNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("P2P_RECIPIENT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(P2pSelfPaymentException::class)
    fun handleSelfPayment(ex: P2pSelfPaymentException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("SELF_PAYMENT_NOT_ALLOWED", ex.message ?: "Bad request"))

    @ExceptionHandler(P2pNoAccountException::class)
    fun handleNoAccount(ex: P2pNoAccountException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

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
    fun handleIdempotencyConflict(ex: IdempotencyConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleIdempotencyInProgress(ex: IdempotencyInProgressException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))
}
