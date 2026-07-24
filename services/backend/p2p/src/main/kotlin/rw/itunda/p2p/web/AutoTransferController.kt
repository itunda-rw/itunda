package rw.itunda.p2p.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.domain.AutoTransferFrequency
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.p2p.AutoTransferInvalidScheduleException
import rw.itunda.p2p.AutoTransferNotFoundException
import rw.itunda.p2p.AutoTransferService
import rw.itunda.p2p.P2pInvalidAmountException
import rw.itunda.p2p.P2pNoWalletException
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
class AutoTransferController(private val autoTransferService: AutoTransferService) {

    @PostMapping
    fun create(
        @RequestBody request: CreateAutoTransferRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> {
        val autoTransfer = autoTransferService.create(
            currentUser.userId, request.recipient, request.amount, request.frequency,
            request.dayOfWeek, request.dayOfMonth, request.description,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "autoTransfer" to autoTransfer))
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

    @ExceptionHandler(P2pNoWalletException::class)
    fun handleNoWallet(ex: P2pNoWalletException) =
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
}
