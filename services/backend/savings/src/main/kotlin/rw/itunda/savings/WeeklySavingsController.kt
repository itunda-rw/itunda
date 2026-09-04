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

data class CreateWeeklyPlanRequest(val name: String, val baseWeeklyAmount: BigDecimal, val escalationRate: BigDecimal)

@RestController
@RequestMapping("/api/v1/weekly-savings")
class WeeklySavingsController(
    private val weeklySavingsService: WeeklySavingsService,
    private val weeklySavingsScheduler: WeeklySavingsScheduler,
    private val idempotencyService: IdempotencyService,
) {
    @PostMapping("/plans")
    fun create(
        @RequestBody request: CreateWeeklyPlanRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/weekly-savings/plans", idempotencyKey, request) {
            val plan = weeklySavingsService.createPlan(currentUser.userId, request.name, request.baseWeeklyAmount, request.escalationRate)
            201 to mapOf("success" to true, "plan" to plan)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/plans")
    fun myPlans(@AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "plans" to weeklySavingsService.getPlans(currentUser.userId)))

    @GetMapping("/plans/{id}")
    fun get(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true) + weeklySavingsService.getPlan(currentUser.userId, id).toMap())

    @GetMapping("/plans/{id}/transactions")
    fun getTransactions(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "transactions" to weeklySavingsService.getPlanTransactions(currentUser.userId, id)))

    @PostMapping("/plans/{id}/cancel")
    fun cancel(
        @PathVariable id: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/weekly-savings/plans/$id/cancel", idempotencyKey, id) {
            val view = weeklySavingsService.cancelPlan(currentUser.userId, id)
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
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/weekly-savings/plans/$id/withdraw", idempotencyKey, id) {
            val view = weeklySavingsService.withdraw(currentUser.userId, id)
            200 to (mapOf("success" to true, "message" to "Matured plan withdrawn to your main account") + view.toMap())
        }
        return ResponseEntity.status(status).body(body)
    }

    // Demo/ops convenience endpoint (2026-07-21) -- see WeeklySavingsScheduler's own doc
    // comment: exposes the exact same due-installment processing the real @Scheduled
    // job runs, network-wide, so a real 26-week maturity (and its per-installment
    // interest/streak-bonus math) can be verified without waiting real wall-clock
    // weeks. Not a per-user-scoped action -- like the scheduler itself, it only ever
    // touches plans that are actually due and returns a count, never other users' data.
    @PostMapping("/process-due")
    @PreAuthorize("hasRole('ADMIN')")
    fun processDue(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val processed = weeklySavingsScheduler.processDue()
        return ResponseEntity.ok(mapOf("success" to true, "processed" to processed))
    }

    private fun WeeklySavingsPlanView.toMap() = mapOf("plan" to plan, "accountBalance" to accountBalance, "installments" to installments)

    @ExceptionHandler(WeeklyPlanNotFoundException::class)
    fun handleNotFound(ex: WeeklyPlanNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WEEKLY_PLAN_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(WeeklyPlanInvalidEscalationException::class)
    fun handleInvalidEscalation(ex: WeeklyPlanInvalidEscalationException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_ESCALATION_RATE", ex.message ?: "Invalid request"))

    @ExceptionHandler(WeeklyPlanInvalidAmountException::class)
    fun handleInvalidAmount(ex: WeeklyPlanInvalidAmountException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_AMOUNT", ex.message ?: "Invalid request"))

    @ExceptionHandler(WeeklyPlanNotActiveException::class)
    fun handleNotActive(ex: WeeklyPlanNotActiveException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("WEEKLY_PLAN_NOT_ACTIVE", ex.message ?: "Conflict"))

    @ExceptionHandler(WeeklyPlanNotMaturedException::class)
    fun handleNotMatured(ex: WeeklyPlanNotMaturedException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("WEEKLY_PLAN_NOT_MATURED", ex.message ?: "Conflict"))

    @ExceptionHandler(WeeklyPlanAlreadyWithdrawnException::class)
    fun handleAlreadyWithdrawn(ex: WeeklyPlanAlreadyWithdrawnException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("WEEKLY_PLAN_ALREADY_WITHDRAWN", ex.message ?: "Conflict"))

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
