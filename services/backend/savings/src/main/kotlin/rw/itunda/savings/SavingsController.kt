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
import rw.itunda.core.ledger.WalletFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal

data class CreateGoalRequest(val name: String, val targetAmount: BigDecimal, val monthlyContribution: BigDecimal? = null, val targetDate: String? = null, val category: String? = null)
data class DepositRequest(val goalId: String, val amount: BigDecimal, val fromWalletId: String? = null)

@RestController
@RequestMapping("/api/v1/savings")
class SavingsController(private val savingsService: SavingsService, private val idempotencyService: IdempotencyService) {

    @GetMapping("/goals")
    fun getGoals(@AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "goals" to savingsService.getGoals(currentUser.userId)))

    @PostMapping("/goals")
    fun createGoal(@RequestBody request: CreateGoalRequest, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val goal = savingsService.createGoal(currentUser.userId, request.name, request.targetAmount, request.monthlyContribution, request.targetDate, request.category)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "goal" to goal))
    }

    @PostMapping("/deposit")
    fun deposit(
        @RequestBody request: DepositRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/savings/deposit", idempotencyKey, request) {
            val goal = savingsService.depositToGoal(currentUser.userId, request.goalId, request.amount, request.fromWalletId)
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

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))

    @ExceptionHandler(GoalNotFoundException::class)
    fun handleGoalNotFound(ex: GoalNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("GOAL_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(NoWalletException::class)
    fun handleNoWallet(ex: NoWalletException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(NoInterestJarException::class)
    fun handleNoJar(ex: NoInterestJarException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("INTEREST_JAR_NOT_FOUND", ex.message ?: "Not found"))

    // Real fix (IDOR audit pass 2, docs/DESIGN_REFERENCES.md-adjacent sweep, same bug
    // class as the GroupAccountController fix): a caller submitting someone else's real
    // walletId as depositToGoal's optional fromWalletId used to real-403, confirming
    // that wallet exists -- an existence-oracle this codebase's own established
    // convention (a stranger gets a real 404, never a 403) exists specifically to avoid.
    @ExceptionHandler(WalletNotOwnedException::class)
    fun handleNotOwned(ex: WalletNotOwnedException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_OWNED", ex.message ?: "Not found"))

    @ExceptionHandler(NoInterestAvailableException::class)
    fun handleNoInterest(ex: NoInterestAvailableException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("NO_INTEREST_AVAILABLE", ex.message ?: "Conflict"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(WalletFrozenException::class)
    fun handleWalletFrozen(ex: WalletFrozenException) = ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("WALLET_FROZEN", ex.message ?: "Wallet is frozen"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMIT_EXCEEDED", ex.message ?: "Too many requests"))
}
