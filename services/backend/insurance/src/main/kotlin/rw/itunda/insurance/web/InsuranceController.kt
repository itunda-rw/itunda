package rw.itunda.insurance.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.*
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.domain.InsurancePremiumFund
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.AccountFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.insurance.InsurancePolicyRenewalReminderScheduler
import rw.itunda.insurance.InsuranceService
import rw.itunda.insurance.InvalidClaimException
import rw.itunda.insurance.InvalidPremiumFundAmountException
import rw.itunda.insurance.NoAccountException
import rw.itunda.insurance.PlanNotFoundException
import rw.itunda.insurance.PolicyNotActiveException
import rw.itunda.insurance.PolicyNotFoundException
import rw.itunda.insurance.PremiumFundAlreadyExistsException
import rw.itunda.insurance.PremiumFundNotActiveException
import rw.itunda.insurance.PremiumFundNotFoundException
import java.math.BigDecimal

data class EnrollRequest(val planId: String)
data class SubmitClaimRequest(val policyId: String, val description: String, val amount: BigDecimal)
data class CreatePremiumFundRequest(val dailyContribution: BigDecimal)
data class ContributeToFundRequest(val amount: BigDecimal)

@RestController
@RequestMapping("/api/v1/insurance")
class InsuranceController(
    private val insuranceService: InsuranceService,
    private val idempotencyService: IdempotencyService,
    private val insurancePolicyRenewalReminderScheduler: InsurancePolicyRenewalReminderScheduler,
) {

    @GetMapping("/plans")
    fun getPlans(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "success" to true,
            "plans" to insuranceService.getPlans()
        ))
    }

    @GetMapping("/my-policies")
    fun getMyPolicies(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> {
        val policies = insuranceService.getMyPolicies(currentUser.userId)
        return ResponseEntity.ok(mapOf(
            "success" to true,
            "policies" to policies.map {
                mapOf(
                    "id" to it.id,
                    "planId" to it.planId,
                    "planName" to it.planName,
                    "category" to it.category,
                    "status" to it.status,
                    "startDate" to it.startDate.toString(),
                    "endDate" to it.endDate.toString(),
                    "monthlyPremium" to it.monthlyPremium,
                    "nextPaymentDate" to it.nextPaymentDate.toString(),
                    "policyNumber" to it.policyNumber
                )
            }
        ))
    }

    @PostMapping("/enroll")
    fun enrollInPlan(
        @RequestBody request: EnrollRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/insurance/enroll", idempotencyKey, request) {
            val policy = insuranceService.enrollInPlan(currentUser.userId, request.planId)
            val policyMap = mapOf(
                "id" to policy.id,
                "planId" to policy.planId,
                "planName" to policy.planName,
                "category" to policy.category,
                "status" to policy.status,
                "startDate" to policy.startDate.toString(),
                "endDate" to policy.endDate.toString(),
                "monthlyPremium" to policy.monthlyPremium,
                "nextPaymentDate" to policy.nextPaymentDate.toString(),
                "policyNumber" to policy.policyNumber
            )
            201 to mapOf(
                "success" to true,
                "message" to "Enrolled in ${policy.planName}",
                "policy" to policyMap
            )
        }
        return ResponseEntity.status(status).body(body)
    }

    // Real claims filing (2026-07-13) -- see docs/TOSS_PARITY_MATRIX.md's Insurance row.
    // Real idempotency fix (item 236, found via a periodic Idempotency-Key coverage
    // audit) -- corrects this endpoint's own earlier reasoning that no key was needed
    // because "filing a claim isn't money-moving." That's true of THIS request, but
    // decideClaim's own guard only stops the SAME claim row from being decided twice
    // -- it does nothing to stop two separate duplicate claim rows (created by a
    // retried/double-tapped submitClaim) from each being independently approved by an
    // admin working through the queue, a real double payout for one real incident.
    @PostMapping("/claims")
    fun submitClaim(
        @RequestBody request: SubmitClaimRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/insurance/claims", idempotencyKey, request) {
            val claim = insuranceService.submitClaim(currentUser.userId, request.policyId, request.description, request.amount)
            HttpStatus.CREATED.value() to mapOf("success" to true, "claim" to claim)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/claims")
    fun getMyClaims(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "claims" to insuranceService.getMyClaims(currentUser.userId)))

    private fun fundMap(fund: InsurancePremiumFund) = mapOf(
        "id" to fund.id,
        "policyId" to fund.policyId,
        "targetAmount" to fund.targetAmount,
        "currentAmount" to fund.currentAmount,
        "dailyContribution" to fund.dailyContribution,
        "status" to fund.status.name,
        "createdAt" to fund.createdAt.toString(),
    )

    // Real Ejo Heza ya Moto-style premium savings fund (2026-08-02) -- see
    // InsuranceService.createPremiumFund's own doc comment. Row creation only, no
    // Idempotency-Key needed, same precedent as POST /api/v1/savings/goals.
    @PostMapping("/policies/{policyId}/premium-fund")
    fun createPremiumFund(
        @PathVariable policyId: String,
        @RequestBody request: CreatePremiumFundRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> {
        val fund = insuranceService.createPremiumFund(currentUser.userId, policyId, request.dailyContribution)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "fund" to fundMap(fund)))
    }

    @PostMapping("/premium-funds/{fundId}/contribute")
    fun contributeToFund(
        @PathVariable fundId: String,
        @RequestBody request: ContributeToFundRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/insurance/premium-funds/{fundId}/contribute", idempotencyKey, request) {
            val fund = insuranceService.contributeToFund(currentUser.userId, fundId, request.amount)
            200 to mapOf("success" to true, "fund" to fundMap(fund))
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/premium-funds/{fundId}/cancel")
    fun cancelFund(
        @PathVariable fundId: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/insurance/premium-funds/{fundId}/cancel", idempotencyKey, fundId) {
            val fund = insuranceService.cancelFund(currentUser.userId, fundId)
            200 to mapOf("success" to true, "fund" to fundMap(fund))
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/premium-funds")
    fun getMyPremiumFunds(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "funds" to insuranceService.getMyPremiumFunds(currentUser.userId).map(::fundMap)))

    // Real Kakao Pay/Toss Insurance 갱신 안내 (renewal notice) manual trigger -- same
    // "expose the scheduler's own real logic as a callable endpoint" convention
    // SavingsController.processMaturityReminders already establishes, so a real
    // policy's real endDate can be verified without waiting actual wall-clock days for
    // it to enter the reminder window.
    // Real gap found live (2026-08-31, market-readiness audit): this fires the
    // reminder job for EVERY user's due policies system-wide, yet had no ADMIN gate --
    // any authenticated user could call it. ADMIN-gated the same
    // @PreAuthorize("hasRole('ADMIN')") way WeatherIndexInsuranceController.
    // publishSeasonIndex/WeeklySavingsController.processDue already are (this route
    // doesn't live under /api/v1/system/**, so it doesn't inherit SecurityConfig's
    // blanket ADMIN gate there).
    @PostMapping("/policies/process-renewal-reminders")
    @PreAuthorize("hasRole('ADMIN')")
    fun processRenewalReminders(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val processed = insurancePolicyRenewalReminderScheduler.processDue()
        return ResponseEntity.ok(mapOf("success" to true, "processed" to processed))
    }

    @ExceptionHandler(PremiumFundNotFoundException::class)
    fun handlePremiumFundNotFound(ex: PremiumFundNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("PREMIUM_FUND_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(PremiumFundAlreadyExistsException::class)
    fun handlePremiumFundAlreadyExists(ex: PremiumFundAlreadyExistsException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("PREMIUM_FUND_ALREADY_EXISTS", ex.message ?: "Conflict"))

    @ExceptionHandler(PremiumFundNotActiveException::class)
    fun handlePremiumFundNotActive(ex: PremiumFundNotActiveException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("PREMIUM_FUND_NOT_ACTIVE", ex.message ?: "Conflict"))

    @ExceptionHandler(InvalidPremiumFundAmountException::class)
    fun handleInvalidPremiumFundAmount(ex: InvalidPremiumFundAmountException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_PREMIUM_FUND_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(PolicyNotFoundException::class)
    fun handlePolicyNotFound(ex: PolicyNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("POLICY_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(PolicyNotActiveException::class)
    fun handlePolicyNotActive(ex: PolicyNotActiveException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("POLICY_NOT_ACTIVE", ex.message ?: "Conflict"))

    @ExceptionHandler(InvalidClaimException::class)
    fun handleInvalidClaim(ex: InvalidClaimException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_CLAIM", ex.message ?: "Bad request"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))

    @ExceptionHandler(PlanNotFoundException::class)
    fun handlePlanNotFound(ex: PlanNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("PLAN_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(NoAccountException::class)
    fun handleNoAccount(ex: NoAccountException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(AccountFrozenException::class)
    fun handleAccountFrozen(ex: AccountFrozenException) = ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("ACCOUNT_FROZEN", ex.message ?: "Account is frozen"))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_REQUEST", ex.message ?: "Bad request"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}
