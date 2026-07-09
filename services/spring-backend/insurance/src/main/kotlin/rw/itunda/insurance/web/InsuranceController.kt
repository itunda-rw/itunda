package rw.itunda.insurance.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.*
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.insurance.InsuranceService
import rw.itunda.insurance.NoWalletException
import rw.itunda.insurance.PlanNotFoundException

data class EnrollRequest(val planId: String)

@RestController
@RequestMapping("/api/v1/insurance")
class InsuranceController(
    private val insuranceService: InsuranceService,
    private val idempotencyService: IdempotencyService
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

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))

    @ExceptionHandler(PlanNotFoundException::class)
    fun handlePlanNotFound(ex: PlanNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("PLAN_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(NoWalletException::class)
    fun handleNoWallet(ex: NoWalletException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))
}
