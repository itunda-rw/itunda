package rw.itunda.wallet

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
import org.springframework.web.bind.MissingRequestHeaderException
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.agents.AgentWithdrawalAuthorizationService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.WalletFrozenException
import rw.itunda.core.provider.ProviderDeclinedException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal

data class QuoteTransferRequest(val amount: BigDecimal, val recipient: String, val fromWalletId: String? = null, val description: String? = null)
data class ConfirmTransferRequest(val quoteId: String)
data class SetBudgetRequest(val category: String? = null, val monthlyLimit: BigDecimal)
data class CreateAgentWithdrawalAuthorizationRequest(val amount: BigDecimal)
data class CancelAgentWithdrawalAuthorizationRequest(val code: String)

@RestController
@RequestMapping("/api/v1/wallet")
class WalletController(
    private val walletService: WalletService,
    private val idempotencyService: IdempotencyService,
    private val agentWithdrawalAuthorizationService: AgentWithdrawalAuthorizationService,
) {

    @GetMapping
    fun getWallets(@AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "wallets" to walletService.getWallets(currentUser.userId)))

    @GetMapping("/{id}")
    fun getWalletById(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "wallet" to walletService.getWalletById(id, currentUser.userId)))

    // Real transaction history (2026-07-12) -- backs the new card/transaction-
    // history screen on both platforms; see WalletService.getTransactionHistory.
    @GetMapping("/transactions")
    fun getTransactionHistory(@AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "transactions" to walletService.getTransactionHistory(currentUser.userId)))

    // Real spending categorization (2026-07-13) -- see WalletService.getSpendingInsight
    // for why this is built over the ledger, not the transactions table.
    @GetMapping("/spending")
    fun getSpendingInsight(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> {
        val result = walletService.getSpendingInsight(currentUser.userId)
        return ResponseEntity.ok(mapOf("success" to true, "categories" to result.categories, "totalSpent" to result.totalSpent))
    }

    // Real budgeting/limits (2026-07-13) -- see WalletService.setBudget/getBudgets.
    @PostMapping("/budgets")
    fun setBudget(@RequestBody request: SetBudgetRequest, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> {
        val budget = walletService.setBudget(currentUser.userId, request.category, request.monthlyLimit)
        return ResponseEntity.ok(mapOf("success" to true, "budget" to budget))
    }

    @GetMapping("/budgets")
    fun getBudgets(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "budgets" to walletService.getBudgets(currentUser.userId)))

    /** Creates a one-time, ten-minute code the customer shows only after confirming a cash-out. */
    @PostMapping("/agent-withdrawal-authorizations")
    fun createAgentWithdrawalAuthorization(
        @RequestBody request: CreateAgentWithdrawalAuthorizationRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = ResponseEntity.status(HttpStatus.CREATED).body(
        mapOf("success" to true, "authorization" to agentWithdrawalAuthorizationService.create(currentUser.userId, request.amount)),
    )

    @PostMapping("/agent-withdrawal-authorizations/cancel")
    fun cancelAgentWithdrawalAuthorization(
        @RequestBody request: CancelAgentWithdrawalAuthorizationRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = ResponseEntity.ok(
        mapOf("success" to true, "authorization" to agentWithdrawalAuthorizationService.cancel(currentUser.userId, request.code)),
    )

    @GetMapping("/agent-withdrawal-authorizations")
    fun getAgentWithdrawalAuthorizations(@AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "authorizations" to agentWithdrawalAuthorizationService.list(currentUser.userId)))

    @PostMapping("/transfer/quote")
    fun quoteTransfer(@RequestBody request: QuoteTransferRequest, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> {
        val quote = walletService.quoteTransfer(currentUser.userId, request.fromWalletId, request.recipient, request.amount)
        return ResponseEntity.ok(mapOf("success" to true, "quote" to quote))
    }

    /** Requires Idempotency-Key, same as Express's POST /wallet/transfer/confirm — a
     * retried request (client timeout, double-tap) replays the original result instead
     * of double-spending. Now durable in MySQL instead of an in-memory Map. */
    @PostMapping("/transfer/confirm")
    fun confirmTransfer(
        @RequestBody request: ConfirmTransferRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/wallet/transfer/confirm", idempotencyKey, request) {
            val (transaction, newBalance) = walletService.confirmTransfer(request.quoteId, currentUser.userId)
            200 to mapOf("success" to true, "message" to "Transfer successful", "transaction" to transaction, "newBalance" to newBalance)
        }
        return ResponseEntity.status(status).body(body)
    }

    // Codes match Toss Payments' own real error taxonomy where there's a direct parity
    // feature (docs.tosspayments.com/reference/error-codes uses IDEMPOTENT_REQUEST_
    // PROCESSING and INVALID_REQUEST verbatim); the rest follow the same
    // SCREAMING_SNAKE_CASE convention for this backend's own domain errors.
    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleIdempotencyConflict(ex: IdempotencyConflictException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleIdempotencyInProgress(ex: IdempotencyInProgressException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required to confirm a transfer"))

    @ExceptionHandler(WalletNotFoundException::class)
    fun handleNotFound(ex: WalletNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(WalletNotOwnedException::class)
    fun handleNotOwned(ex: WalletNotOwnedException) = ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("WALLET_NOT_OWNED", ex.message ?: "Forbidden"))

    @ExceptionHandler(QuoteNotFoundException::class)
    fun handleQuoteNotFound(ex: QuoteNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("QUOTE_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(QuoteExpiredException::class)
    fun handleQuoteExpired(ex: QuoteExpiredException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("QUOTE_EXPIRED", ex.message ?: "Conflict"))

    @ExceptionHandler(QuoteAlreadyUsedException::class)
    fun handleQuoteAlreadyUsed(ex: QuoteAlreadyUsedException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("QUOTE_ALREADY_USED", ex.message ?: "Conflict"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(WalletFrozenException::class)
    fun handleWalletFrozen(ex: WalletFrozenException) = ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("WALLET_FROZEN", ex.message ?: "Wallet is frozen"))

    // Same handler as BillsController's -- provider connector wired into confirmTransfer.
    @ExceptionHandler(ProviderDeclinedException::class)
    fun handleProviderDeclined(ex: ProviderDeclinedException) = ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiError("PROVIDER_DECLINED", ex.message ?: "Provider declined"))

    @ExceptionHandler(rw.itunda.core.agents.WithdrawalAuthorizationInvalidException::class)
    fun handleWithdrawalAuthorization(ex: rw.itunda.core.agents.WithdrawalAuthorizationInvalidException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("WITHDRAWAL_AUTHORIZATION_INVALID", ex.message ?: "Invalid authorization"))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_REQUEST", ex.message ?: "Bad request"))
}
