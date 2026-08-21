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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.AccountFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.family.FamilySpendLimitExceededException
import rw.itunda.p2p.P2pDelayedTransferNotCancellableException
import rw.itunda.p2p.P2pDelayedTransferNotFoundException
import rw.itunda.p2p.P2pDelayedTransferService
import rw.itunda.p2p.P2pInvalidAmountException
import rw.itunda.p2p.P2pNoAccountException
import rw.itunda.p2p.P2pRecipientNotFoundException
import rw.itunda.p2p.P2pRequestNotFoundException
import rw.itunda.p2p.P2pRequestNotPayableException
import rw.itunda.p2p.P2pSelfPaymentException
import rw.itunda.p2p.P2pService
import rw.itunda.p2p.P2pTransferLimitExceededException
import java.math.BigDecimal

data class GenerateP2pRequest(val amount: BigDecimal, val description: String)
data class SendDirectP2pRequest(val recipient: String, val amount: BigDecimal, val description: String = "")
data class SendToFamilyMemberRequest(val childUserId: String, val amount: BigDecimal, val description: String = "")
data class SendDelayedP2pRequest(val recipient: String, val amount: BigDecimal, val description: String = "")

// Person-to-person QR -- see docs/API_SPECIFICATION.md's P2P section and
// docs/TOSS_PARITY_MATRIX.md's QR Pay row.
@RestController
@RequestMapping("/api/v1/p2p")
class P2pController(
    private val p2pService: P2pService,
    private val p2pDelayedTransferService: P2pDelayedTransferService,
    private val idempotencyService: IdempotencyService,
) {

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

    // Real Toss/Kakao Bank-style recipient-name confirmation ("받는분 성함 확인") -- see
    // P2pService.resolveRecipient's own doc comment for the full sourced account. A
    // client calls this right after the sender types a phone/account number, to show
    // the real resolved recipient's name before rendering the final "Send X RWF to
    // [name]?" confirmation -- catches a mistyped digit before money moves, not after.
    // Read-only, no Idempotency-Key needed (moves no money, has no side effect to replay).
    @GetMapping("/recipient")
    fun resolveRecipient(
        @RequestParam identifier: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "recipient" to p2pService.resolveRecipient(currentUser.userId, identifier)))

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

    // Real Naver Pay "가족 공유 자산 관리" (family shared asset management) -- instant
    // transfer to a linked family member, see P2pService.sendToFamilyMember's own doc
    // comment.
    @PostMapping("/send-to-family")
    fun sendToFamilyMember(
        @RequestBody request: SendToFamilyMemberRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/p2p/send-to-family", idempotencyKey, request) {
            val (transaction, newBalance) = p2pService.sendToFamilyMember(currentUser.userId, request.childUserId, request.amount, request.description)
            200 to mapOf("success" to true, "message" to "Transfer successful", "transaction" to transaction, "newBalance" to newBalance)
        }
        return ResponseEntity.status(status).body(body)
    }

    // Real Korean 지연이체서비스 (Delayed Transfer Service) -- see
    // P2pDelayedTransferService.sendDelayed's own doc comment for the full sourced
    // account. An explicit, opt-in alternative to /send: the sender's real money is
    // held for a real window instead of landing instantly, specifically so a transfer
    // made under active phishing pressure (or just a fat-fingered recipient) can still
    // be cancelled before it's irreversible.
    @PostMapping("/send-delayed")
    fun sendDelayed(
        @RequestBody request: SendDelayedP2pRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/p2p/send-delayed", idempotencyKey, request) {
            val transfer = p2pDelayedTransferService.sendDelayed(currentUser.userId, request.recipient, request.amount, request.description)
            201 to mapOf("success" to true, "message" to "Transfer held -- it'll be sent unless you cancel before it releases", "transfer" to transfer)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/delayed-transfers")
    fun getMyDelayedTransfers(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "transfers" to p2pDelayedTransferService.getMyDelayedTransfers(currentUser.userId)))

    // Real sender-initiated cancel within the real delay window -- refunds the held
    // amount back to the sender immediately. No Idempotency-Key: this mutates a single
    // resource by its own id into a terminal CANCELLED state, the same
    // already-idempotent-by-nature shape RideTrustedContactService.remove's own DELETE
    // endpoint uses (a retried cancel of an already-cancelled transfer just real-409s
    // via P2pDelayedTransferNotCancellableException, never double-refunds).
    @PostMapping("/delayed-transfers/{transferId}/cancel")
    fun cancelDelayedTransfer(
        @PathVariable transferId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val transfer = p2pDelayedTransferService.cancel(currentUser.userId, transferId)
        return ResponseEntity.ok(mapOf("success" to true, "message" to "Transfer cancelled and refunded", "transfer" to transfer))
    }

    @ExceptionHandler(P2pDelayedTransferNotFoundException::class)
    fun handleDelayedTransferNotFound(ex: P2pDelayedTransferNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("P2P_DELAYED_TRANSFER_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(P2pDelayedTransferNotCancellableException::class)
    fun handleDelayedTransferNotCancellable(ex: P2pDelayedTransferNotCancellableException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("P2P_DELAYED_TRANSFER_NOT_CANCELLABLE", ex.message ?: "Conflict"))

    @ExceptionHandler(P2pRequestNotFoundException::class)
    fun handleNotFound(ex: P2pRequestNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("P2P_REQUEST_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(P2pRequestNotPayableException::class)
    fun handleNotPayable(ex: P2pRequestNotPayableException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("P2P_REQUEST_NOT_PAYABLE", ex.message ?: "Conflict"))

    @ExceptionHandler(P2pSelfPaymentException::class)
    fun handleSelfPayment(ex: P2pSelfPaymentException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("SELF_PAYMENT_NOT_ALLOWED", ex.message ?: "Bad request"))

    @ExceptionHandler(P2pNoAccountException::class)
    fun handleNoAccount(ex: P2pNoAccountException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(AccountFrozenException::class)
    fun handleAccountFrozen(ex: AccountFrozenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("ACCOUNT_FROZEN", ex.message ?: "Account is frozen"))

    @ExceptionHandler(FamilySpendLimitExceededException::class)
    fun handleFamilySpendLimit(ex: FamilySpendLimitExceededException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("FAMILY_SPEND_LIMIT_EXCEEDED", ex.message ?: "Spend limit exceeded"))

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

    @ExceptionHandler(P2pTransferLimitExceededException::class)
    fun handleTransferLimitExceeded(ex: P2pTransferLimitExceededException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("P2P_TRANSFER_LIMIT_EXCEEDED", ex.message ?: "Transfer limit exceeded"))
}
