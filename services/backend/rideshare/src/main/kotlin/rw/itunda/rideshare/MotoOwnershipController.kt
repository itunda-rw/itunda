package rw.itunda.rideshare

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

data class CreateMotoOwnershipPlanRequest(val bikePrice: BigDecimal, val dailyContribution: BigDecimal)
data class ContributeToMotoOwnershipPlanRequest(val amount: BigDecimal)
data class RepayMotoOwnershipPlanRequest(val amount: BigDecimal)

// Real Rwanda moto-taxi ownership savings-to-loan plan -- see
// MotoOwnershipService's own doc comment for the full sourced account. Normal
// itunda-user JWT gate.
@RestController
@RequestMapping("/api/v1/moto-ownership")
class MotoOwnershipController(
    private val motoOwnershipService: MotoOwnershipService,
    private val idempotencyService: IdempotencyService,
) {
    // Real gap found 2026-09-05, same class as StudentLoanController.apply's
    // identical fix (see its own doc comment) -- the "not money movement" reasoning
    // this endpoint (and the loans/vup, loans/student, vendor-advance, cooperatives/
    // advances siblings) previously relied on to skip Idempotency-Key missed the
    // actual risk: MotoOwnershipService.createPlan's own MotoOwnershipPlanAlreadyActiveException
    // guard would fire on a legitimate lost-response retry, not because money moved,
    // but because "only one active plan" is a real uniqueness constraint a retry can
    // spuriously trip. contribute/cancel/convert-to-loan/repay below were already
    // protected; this create endpoint was the outlier.
    @PostMapping("/plans")
    fun createPlan(
        @RequestBody request: CreateMotoOwnershipPlanRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/moto-ownership/plans", idempotencyKey, request) {
            val plan = motoOwnershipService.createPlan(currentUser.userId, request.bikePrice, request.dailyContribution)
            HttpStatus.CREATED.value() to mapOf("success" to true, "plan" to plan)
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/plans/{planId}/contribute")
    fun contribute(
        @PathVariable planId: String,
        @RequestBody request: ContributeToMotoOwnershipPlanRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/moto-ownership/plans/$planId/contribute", idempotencyKey, request) {
            val plan = motoOwnershipService.contribute(currentUser.userId, planId, request.amount)
            200 to mapOf("success" to true, "plan" to plan)
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/plans/{planId}/cancel")
    fun cancel(
        @PathVariable planId: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/moto-ownership/plans/$planId/cancel", idempotencyKey, currentUser.userId) {
            val plan = motoOwnershipService.cancel(currentUser.userId, planId)
            200 to mapOf("success" to true, "plan" to plan)
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/plans/{planId}/convert-to-loan")
    fun convertToLoan(
        @PathVariable planId: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/moto-ownership/plans/$planId/convert-to-loan", idempotencyKey, currentUser.userId) {
            val plan = motoOwnershipService.convertToLoan(currentUser.userId, planId)
            200 to mapOf("success" to true, "plan" to plan)
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/plans/{planId}/repay")
    fun repay(
        @PathVariable planId: String,
        @RequestBody request: RepayMotoOwnershipPlanRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/moto-ownership/plans/$planId/repay", idempotencyKey, request) {
            val plan = motoOwnershipService.repay(currentUser.userId, planId, request.amount)
            200 to mapOf("success" to true, "plan" to plan)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/plans/me")
    fun getMyPlans(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "plans" to motoOwnershipService.getMyPlans(currentUser.userId)))

    @GetMapping("/plans/{planId}")
    fun getPlan(@PathVariable planId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "plan" to motoOwnershipService.getPlan(currentUser.userId, planId)))

    @ExceptionHandler(MotoOwnershipPlanNotFoundException::class)
    fun handleNotFound(ex: MotoOwnershipPlanNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MOTO_OWNERSHIP_PLAN_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(MotoOwnershipNoAccountException::class)
    fun handleNoAccount(ex: MotoOwnershipNoAccountException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidMotoOwnershipBikePriceException::class)
    fun handleInvalidBikePrice(ex: InvalidMotoOwnershipBikePriceException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_BIKE_PRICE", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidMotoOwnershipContributionException::class)
    fun handleInvalidContribution(ex: InvalidMotoOwnershipContributionException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_DAILY_CONTRIBUTION", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidMotoOwnershipAmountException::class)
    fun handleInvalidAmount(ex: InvalidMotoOwnershipAmountException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(MotoOwnershipPlanAlreadyActiveException::class)
    fun handleAlreadyActive(ex: MotoOwnershipPlanAlreadyActiveException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("MOTO_OWNERSHIP_PLAN_ALREADY_ACTIVE", ex.message ?: "Conflict"))

    @ExceptionHandler(MotoOwnershipPlanNotSavingException::class)
    fun handleNotSaving(ex: MotoOwnershipPlanNotSavingException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("MOTO_OWNERSHIP_PLAN_NOT_SAVING", ex.message ?: "Conflict"))

    @ExceptionHandler(MotoOwnershipPlanNotCancellableException::class)
    fun handleNotCancellable(ex: MotoOwnershipPlanNotCancellableException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("MOTO_OWNERSHIP_PLAN_NOT_CANCELLABLE", ex.message ?: "Conflict"))

    @ExceptionHandler(MotoOwnershipPlanNotRepayableException::class)
    fun handleNotRepayable(ex: MotoOwnershipPlanNotRepayableException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("MOTO_OWNERSHIP_PLAN_NOT_REPAYABLE", ex.message ?: "Conflict"))

    @ExceptionHandler(MotoOwnershipDownPaymentNotMetException::class)
    fun handleDownPaymentNotMet(ex: MotoOwnershipDownPaymentNotMetException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("DOWN_PAYMENT_NOT_MET", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(AccountFrozenException::class)
    fun handleAccountFrozen(ex: AccountFrozenException) = ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("ACCOUNT_FROZEN", ex.message ?: "Account is frozen"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}
