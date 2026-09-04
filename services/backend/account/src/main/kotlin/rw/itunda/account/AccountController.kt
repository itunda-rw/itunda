package rw.itunda.account

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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.MissingRequestHeaderException
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.agents.AgentWithdrawalAuthorizationService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.AccountFrozenException
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.provider.ProviderDeclinedException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal

data class QuoteTransferRequest(val amount: BigDecimal, val recipient: String, val fromAccountId: String? = null, val description: String? = null)
data class ConfirmTransferRequest(val quoteId: String)
data class SetBudgetRequest(val category: String? = null, val monthlyLimit: BigDecimal)
data class CreateAgentWithdrawalAuthorizationRequest(val amount: BigDecimal)
data class CancelAgentWithdrawalAuthorizationRequest(val code: String)

@RestController
@RequestMapping("/api/v1/account")
class AccountController(
    private val accountService: AccountService,
    private val spendingInsightService: SpendingInsightService,
    private val idempotencyService: IdempotencyService,
    private val agentWithdrawalAuthorizationService: AgentWithdrawalAuthorizationService,
    private val subscriptionDetectionService: SubscriptionDetectionService,
) {

    @GetMapping
    fun getAccounts(@AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "accounts" to accountService.getAccounts(currentUser.userId)))

    @GetMapping("/{id}")
    fun getAccountById(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "account" to accountService.getAccountById(id, currentUser.userId)))

    // Real transaction history (2026-07-12) -- backs the new card/transaction-
    // history screen on both platforms; see AccountService.getTransactionHistory.
    @GetMapping("/transactions")
    fun getTransactionHistory(@AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "transactions" to accountService.getTransactionHistory(currentUser.userId)))

    // Real Toss Bank/Toss Pay separation follow-up (2026-08-21) -- see
    // AccountService.getAccountTransactionHistory's own doc comment. Backs a real
    // "itunda Pay Money" detail screen showing only that account's own transactions,
    // separate from the user-wide list above.
    @GetMapping("/{id}/transactions")
    fun getAccountTransactionHistory(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "transactions" to accountService.getAccountTransactionHistory(currentUser.userId, id)))

    // Real Toss Timeline-style unusual-spend flag (2026-07-26) -- see
    // AccountService.getTransactionTimeline's own doc comment. A new, separate read path
    // over the exact same real transactions -- /transactions above is unchanged.
    @GetMapping("/transactions/timeline")
    fun getTransactionTimeline(@AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "timeline" to accountService.getTransactionTimeline(currentUser.userId)))

    // Real spending categorization (2026-07-13) -- see SpendingInsightService.getSpendingInsight
    // for why this is built over the ledger, not the transactions table.
    @GetMapping("/spending")
    fun getSpendingInsight(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> {
        val result = spendingInsightService.getSpendingInsight(currentUser.userId)
        return ResponseEntity.ok(mapOf("success" to true, "categories" to result.categories, "totalSpent" to result.totalSpent))
    }

    // Real Kakao Pay 페이아이 소비 리포트 (AI spending report) -- see
    // SpendingInsightService.getMonthlySpendingReport's own doc comment.
    @GetMapping("/spending/monthly-report")
    fun getMonthlySpendingReport(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val report = spendingInsightService.getMonthlySpendingReport(currentUser.userId)
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "currentTotal" to report.currentTotal,
                "previousTotal" to report.previousTotal,
                "percentChange" to report.percentChange,
                "categories" to report.categories,
            ),
        )
    }

    // Real business expense summary (2026-08-11) -- see SpendingInsightService.getBusinessExpenseSummary's
    // own doc comment for the real Toss Bank 세금 신고용 이용내역 자동발송 (tax-filing usage
    // summary) pattern this closes the honest slice of.
    @GetMapping("/business-expense-summary")
    fun getBusinessExpenseSummary(
        @RequestParam(defaultValue = "3") sinceMonthsAgo: Long,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> {
        val result = spendingInsightService.getBusinessExpenseSummary(currentUser.userId, sinceMonthsAgo)
        return ResponseEntity.ok(mapOf("success" to true, "categories" to result.categories, "totalSpent" to result.totalSpent, "sinceMonthsAgo" to sinceMonthsAgo))
    }

    // Real recurring-payment ("subscription") detection -- see
    // SubscriptionDetectionService's own doc comment for the full sourced account.
    @GetMapping("/subscriptions")
    fun getSubscriptions(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> {
        val result = subscriptionDetectionService.detectSubscriptions(currentUser.userId)
        return ResponseEntity.ok(mapOf("success" to true, "subscriptions" to result.subscriptions, "estimatedMonthlyTotal" to result.estimatedMonthlyTotal))
    }

    // Real budgeting/limits (2026-07-13) -- see SpendingInsightService.setBudget/getBudgets.
    @PostMapping("/budgets")
    fun setBudget(@RequestBody request: SetBudgetRequest, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> {
        val budget = spendingInsightService.setBudget(currentUser.userId, request.category, request.monthlyLimit)
        return ResponseEntity.ok(mapOf("success" to true, "budget" to budget))
    }

    @GetMapping("/budgets")
    fun getBudgets(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "budgets" to spendingInsightService.getBudgets(currentUser.userId)))

    /** Creates a one-time, ten-minute code the customer shows only after confirming a cash-out. */
    @PostMapping("/agent-withdrawal-authorizations")
    fun createAgentWithdrawalAuthorization(
        @RequestBody request: CreateAgentWithdrawalAuthorizationRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute(
            "POST /api/v1/account/agent-withdrawal-authorizations", idempotencyKey, request,
        ) {
            201 to mapOf("success" to true, "authorization" to agentWithdrawalAuthorizationService.create(currentUser.userId, request.amount))
        }
        return ResponseEntity.status(status).body(body)
    }

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
        val quote = accountService.quoteTransfer(currentUser.userId, request.fromAccountId, request.recipient, request.amount)
        return ResponseEntity.ok(mapOf("success" to true, "quote" to quote))
    }

    /** Requires Idempotency-Key, same as Express's POST /account/transfer/confirm — a
     * retried request (client timeout, double-tap) replays the original result instead
     * of double-spending. Now durable in MySQL instead of an in-memory Map. */
    @PostMapping("/transfer/confirm")
    fun confirmTransfer(
        @RequestBody request: ConfirmTransferRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/account/transfer/confirm", idempotencyKey, request) {
            val (transaction, newBalance) = accountService.confirmTransfer(request.quoteId, currentUser.userId)
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

    @ExceptionHandler(AccountNotFoundException::class)
    fun handleNotFound(ex: AccountNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(QuoteNotFoundException::class)
    fun handleQuoteNotFound(ex: QuoteNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("QUOTE_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(QuoteExpiredException::class)
    fun handleQuoteExpired(ex: QuoteExpiredException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("QUOTE_EXPIRED", ex.message ?: "Conflict"))

    @ExceptionHandler(QuoteAlreadyUsedException::class)
    fun handleQuoteAlreadyUsed(ex: QuoteAlreadyUsedException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("QUOTE_ALREADY_USED", ex.message ?: "Conflict"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(AccountFrozenException::class)
    fun handleAccountFrozen(ex: AccountFrozenException) = ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("ACCOUNT_FROZEN", ex.message ?: "Account is frozen"))

    // Same handler as BillsController's -- provider connector wired into confirmTransfer.
    @ExceptionHandler(ProviderDeclinedException::class)
    fun handleProviderDeclined(ex: ProviderDeclinedException) = ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiError("PROVIDER_DECLINED", ex.message ?: "Provider declined"))

    @ExceptionHandler(rw.itunda.core.agents.WithdrawalAuthorizationInvalidException::class)
    fun handleWithdrawalAuthorization(ex: rw.itunda.core.agents.WithdrawalAuthorizationInvalidException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("WITHDRAWAL_AUTHORIZATION_INVALID", ex.message ?: "Invalid authorization"))

    @ExceptionHandler(rw.itunda.core.agents.TooManyWithdrawalAuthorizationsException::class)
    fun handleTooManyWithdrawalAuthorizations(ex: rw.itunda.core.agents.TooManyWithdrawalAuthorizationsException) = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("TOO_MANY_WITHDRAWAL_AUTHORIZATIONS", ex.message ?: "Too many requests"))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_REQUEST", ex.message ?: "Bad request"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}
