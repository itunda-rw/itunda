package rw.itunda.savings

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.AccountFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal

data class CreateGrow31PlanRequest(val name: String, val dailyAmount: BigDecimal)

@RestController
@RequestMapping("/api/v1/grow31-savings")
class Grow31SavingsController(
    private val grow31SavingsService: Grow31SavingsService,
    private val grow31SavingsScheduler: Grow31SavingsScheduler,
    private val idempotencyService: IdempotencyService,
) {
    @PostMapping("/plans")
    fun create(
        @RequestBody request: CreateGrow31PlanRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/grow31-savings/plans", idempotencyKey, request) {
            val plan = grow31SavingsService.createPlan(currentUser.userId, request.name, request.dailyAmount)
            201 to mapOf("success" to true, "plan" to plan)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/plans")
    fun myPlans(@AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "plans" to grow31SavingsService.getPlans(currentUser.userId)))

    @GetMapping("/plans/{id}")
    fun get(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true) + grow31SavingsService.getPlan(currentUser.userId, id).toMap())

    @GetMapping("/plans/{id}/transactions")
    fun getTransactions(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "transactions" to grow31SavingsService.getPlanTransactions(currentUser.userId, id)))

    @PostMapping("/plans/{id}/deposit-today")
    fun depositToday(
        @PathVariable id: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/grow31-savings/plans/$id/deposit-today", idempotencyKey, id) {
            val view = grow31SavingsService.depositToday(currentUser.userId, id)
            200 to (mapOf("success" to true) + view.toMap())
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/plans/{id}/cancel")
    fun cancel(
        @PathVariable id: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/grow31-savings/plans/$id/cancel", idempotencyKey, id) {
            val view = grow31SavingsService.cancelPlan(currentUser.userId, id)
            200 to (mapOf("success" to true, "message" to "Plan cancelled -- streak bonus forfeited, principal and base-rate interest paid out") + view.toMap())
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/plans/{id}/withdraw")
    fun withdraw(
        @PathVariable id: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/grow31-savings/plans/$id/withdraw", idempotencyKey, id) {
            val view = grow31SavingsService.withdraw(currentUser.userId, id)
            200 to (mapOf("success" to true, "message" to "Matured plan withdrawn to your main account") + view.toMap())
        }
        return ResponseEntity.status(status).body(body)
    }

    // Demo/ops convenience endpoint, same shape as WeeklySavingsController.processDue --
    // exposes the exact same due-maturity sweep the real @Scheduled job runs, so a real
    // 31-day maturity can be verified without waiting real wall-clock days.
    @PostMapping("/process-due")
    @PreAuthorize("hasRole('ADMIN')")
    fun processDue(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val processed = grow31SavingsScheduler.processDue()
        return ResponseEntity.ok(mapOf("success" to true, "processed" to processed))
    }

    private fun Grow31SavingsPlanView.toMap() = mapOf("plan" to plan, "accountBalance" to accountBalance, "deposits" to deposits)

    @ExceptionHandler(Grow31PlanNotFoundException::class)
    fun handleNotFound(ex: Grow31PlanNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("GROW31_PLAN_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(Grow31PlanInvalidAmountException::class)
    fun handleInvalidAmount(ex: Grow31PlanInvalidAmountException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_AMOUNT", ex.message ?: "Invalid request"))

    @ExceptionHandler(InvalidGrow31PlanNameException::class)
    fun handleInvalidName(ex: InvalidGrow31PlanNameException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_NAME", ex.message ?: "Invalid request"))

    @ExceptionHandler(Grow31PlanNotActiveException::class)
    fun handleNotActive(ex: Grow31PlanNotActiveException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("GROW31_PLAN_NOT_ACTIVE", ex.message ?: "Conflict"))

    @ExceptionHandler(Grow31PlanNotMaturedException::class)
    fun handleNotMatured(ex: Grow31PlanNotMaturedException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("GROW31_PLAN_NOT_MATURED", ex.message ?: "Conflict"))

    @ExceptionHandler(Grow31PlanAlreadyWithdrawnException::class)
    fun handleAlreadyWithdrawn(ex: Grow31PlanAlreadyWithdrawnException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("GROW31_PLAN_ALREADY_WITHDRAWN", ex.message ?: "Conflict"))

    @ExceptionHandler(Grow31AlreadyDepositedTodayException::class)
    fun handleAlreadyDepositedToday(ex: Grow31AlreadyDepositedTodayException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("ALREADY_DEPOSITED_TODAY", ex.message ?: "Conflict"))

    @ExceptionHandler(NoAccountException::class)
    fun handleNoAccount(ex: NoAccountException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(AccountFrozenException::class)
    fun handleAccountFrozen(ex: AccountFrozenException) = ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("ACCOUNT_FROZEN", ex.message ?: "Account is frozen"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleIdempotencyConflict(ex: IdempotencyConflictException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleIdempotencyInProgress(ex: IdempotencyInProgressException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))
}
