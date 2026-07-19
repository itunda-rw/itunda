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
import rw.itunda.core.ledger.WalletFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.p2p.P2pInvalidAmountException
import rw.itunda.p2p.P2pNoWalletException
import rw.itunda.p2p.P2pRecipientNotFoundException
import rw.itunda.p2p.P2pRequestNotFoundException
import rw.itunda.p2p.P2pRequestNotPayableException
import rw.itunda.p2p.P2pSelfPaymentException
import rw.itunda.p2p.P2pService
import java.math.BigDecimal

data class GenerateP2pRequest(val amount: BigDecimal, val description: String)
data class SendDirectP2pRequest(val recipient: String, val amount: BigDecimal, val description: String = "")

// Person-to-person QR -- see docs/API_SPECIFICATION.md's P2P section and
// docs/TOSS_PARITY_MATRIX.md's QR Pay row.
@RestController
@RequestMapping("/api/v1/p2p")
class P2pController(private val p2pService: P2pService, private val idempotencyService: IdempotencyService) {

    @PostMapping("/request")
    fun generateRequest(
        @RequestBody request: GenerateP2pRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> {
        val paymentRequest = p2pService.generateRequest(currentUser.userId, request.amount, request.description)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "request" to paymentRequest))
    }

    @GetMapping("/requests")
    fun getMyRequests(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "requests" to p2pService.getMyRequests(currentUser.userId)))

    @PostMapping("/pay/{requestId}")
    fun pay(
        @PathVariable requestId: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/p2p/pay/$requestId", idempotencyKey, requestId) {
            val (transaction, newBalance) = p2pService.payRequest(currentUser.userId, requestId)
            200 to mapOf("success" to true, "message" to "Payment successful", "transaction" to transaction, "newBalance" to newBalance)
        }
        return ResponseEntity.status(status).body(body)
    }

    // Real direct push-transfer (2026-07-20) -- see P2pService.sendDirect's own doc
    // comment. A real recipient in one step, no pre-existing request needed.
    @PostMapping("/send")
    fun sendDirect(
        @RequestBody request: SendDirectP2pRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/p2p/send", idempotencyKey, request) {
            val (transaction, newBalance) = p2pService.sendDirect(currentUser.userId, request.recipient, request.amount, request.description)
            200 to mapOf("success" to true, "message" to "Transfer successful", "transaction" to transaction, "newBalance" to newBalance)
        }
        return ResponseEntity.status(status).body(body)
    }

    @ExceptionHandler(P2pRequestNotFoundException::class)
    fun handleNotFound(ex: P2pRequestNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("P2P_REQUEST_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(P2pRequestNotPayableException::class)
    fun handleNotPayable(ex: P2pRequestNotPayableException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("P2P_REQUEST_NOT_PAYABLE", ex.message ?: "Conflict"))

    @ExceptionHandler(P2pSelfPaymentException::class)
    fun handleSelfPayment(ex: P2pSelfPaymentException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("SELF_PAYMENT_NOT_ALLOWED", ex.message ?: "Bad request"))

    @ExceptionHandler(P2pNoWalletException::class)
    fun handleNoWallet(ex: P2pNoWalletException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(WalletFrozenException::class)
    fun handleWalletFrozen(ex: WalletFrozenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("WALLET_FROZEN", ex.message ?: "Wallet is frozen"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))

    @ExceptionHandler(P2pRecipientNotFoundException::class)
    fun handleRecipientNotFound(ex: P2pRecipientNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("P2P_RECIPIENT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(P2pInvalidAmountException::class)
    fun handleInvalidAmount(ex: P2pInvalidAmountException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_AMOUNT", ex.message ?: "Bad request"))
}
