package rw.itunda.savings

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
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
import rw.itunda.core.ledger.AccountFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal

data class CreateGoalRequest(val name: String, val targetAmount: BigDecimal, val monthlyContribution: BigDecimal? = null, val targetDate: String? = null, val category: String? = null)
data class DepositRequest(val goalId: String, val amount: BigDecimal, val fromAccountId: String? = null)

@RestController
@RequestMapping("/api/v1/savings")
class SavingsController(
    private val savingsService: SavingsService,
    private val idempotencyService: IdempotencyService,
    private val depositProtectionService: DepositProtectionService,
    private val savingsMaturityReminderScheduler: SavingsMaturityReminderScheduler,
) {

    @GetMapping("/goals")
    fun getGoals(@AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "goals" to savingsService.getGoals(currentUser.userId)))

    @PostMapping("/goals")
    fun createGoal(
        @RequestBody request: CreateGoalRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/savings/goals", idempotencyKey, request) {
            val goal = savingsService.createGoal(currentUser.userId, request.name, request.targetAmount, request.monthlyContribution, request.targetDate, request.category)
            HttpStatus.CREATED.value() to mapOf("success" to true, "goal" to goal)
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/deposit")
    fun deposit(
        @RequestBody request: DepositRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/savings/deposit", idempotencyKey, request) {
            val goal = savingsService.depositToGoal(currentUser.userId, request.goalId, request.amount, request.fromAccountId)
            200 to mapOf("success" to true, "message" to "Deposited ${request.amount} RWF to \"${goal.name}\"", "goal" to goal)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/interest-jar")
    fun getInterestJar(@AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "jar" to savingsService.getInterestJar(currentUser.userId)))

    @PostMapping("/interest-jar/claim")
    fun claimInterest(
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/savings/interest-jar/claim", idempotencyKey, emptyMap<String, Any>()) {
            val result = savingsService.claimInterest(currentUser.userId)
            200 to (mapOf("success" to true, "message" to "Claimed ${result["claimed"]} RWF interest") + result)
        }
        return ResponseEntity.status(status).body(body)
    }

    // Real Deposit Protection Fund status (2026-08-11) -- see DepositProtectionFund.kt's
    // own doc comment for the full honesty framing this feature is built under.
    @GetMapping("/deposit-protection")
    fun getDepositProtectionStatus(@AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "status" to depositProtectionService.getStatus(currentUser.userId)))

    // Real KB국민은행-style 상품만기알림서비스 (product maturity alert) manual trigger --
    // same "expose the scheduler's own real logic as a callable endpoint" convention
    // WeeklySavingsController.processDue already establishes, so a real goal's real
    // targetDate can be verified without waiting actual wall-clock days for it to
    // arrive.
    @PostMapping("/goals/process-maturity-reminders")
    fun processMaturityReminders(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val processed = savingsMaturityReminderScheduler.processDue()
        return ResponseEntity.ok(mapOf("success" to true, "processed" to processed))
    }

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))

    @ExceptionHandler(GoalNotFoundException::class)
    fun handleGoalNotFound(ex: GoalNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("GOAL_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(NoAccountException::class)
    fun handleNoAccount(ex: NoAccountException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(NoInterestJarException::class)
    fun handleNoJar(ex: NoInterestJarException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("INTEREST_JAR_NOT_FOUND", ex.message ?: "Not found"))

    // Real fix (IDOR audit pass 2, docs/DESIGN_REFERENCES.md-adjacent sweep, same bug
    // class as the GroupAccountController fix): a caller submitting someone else's real
    // accountId as depositToGoal's optional fromAccountId used to real-403, confirming
    // that account exists -- an existence-oracle this codebase's own established
    // convention (a stranger gets a real 404, never a 403) exists specifically to avoid.
    @ExceptionHandler(AccountNotOwnedException::class)
    fun handleNotOwned(ex: AccountNotOwnedException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_OWNED", ex.message ?: "Not found"))

    @ExceptionHandler(NoInterestAvailableException::class)
    fun handleNoInterest(ex: NoInterestAvailableException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("NO_INTEREST_AVAILABLE", ex.message ?: "Conflict"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(AccountFrozenException::class)
    fun handleAccountFrozen(ex: AccountFrozenException) = ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("ACCOUNT_FROZEN", ex.message ?: "Account is frozen"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMIT_EXCEEDED", ex.message ?: "Too many requests"))
}
