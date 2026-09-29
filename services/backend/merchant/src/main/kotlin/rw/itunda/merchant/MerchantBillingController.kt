package rw.itunda.merchant

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import rw.itunda.auth.RateLimitExceededException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal

data class CreateBillingPlanRequest(val name: String, val description: String? = null, val amount: BigDecimal, val intervalDays: Int)

// Real Kakao Pay 정기결제/Toss Payments billing-key-style recurring merchant billing --
// see MerchantBillingService's own doc comment.
@RestController
@RequestMapping("/api/v1/merchant")
class MerchantBillingController(private val merchantBillingService: MerchantBillingService, private val idempotencyService: IdempotencyService) {

    @PostMapping("/billing-plans")
    fun createPlan(
        @RequestBody request: CreateBillingPlanRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/merchant/billing-plans", idempotencyKey, request) {
            val plan = merchantBillingService.createPlan(currentUser.userId, request.name, request.description, request.amount, request.intervalDays)
            201 to mapOf("success" to true, "plan" to plan)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/billing-plans")
    fun getMyPlans(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "plans" to merchantBillingService.getMyPlans(currentUser.userId)))

    @PostMapping("/billing-plans/{planId}/deactivate")
    fun deactivatePlan(
        @PathVariable planId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "plan" to merchantBillingService.deactivatePlan(currentUser.userId, planId)))

    @GetMapping("/{merchantId}/billing-plans")
    fun getPlansForMerchant(@PathVariable merchantId: String): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "plans" to merchantBillingService.getPlansForMerchant(merchantId)))

    @PostMapping("/billing-plans/{planId}/subscribe")
    fun subscribe(
        @PathVariable planId: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/merchant/billing-plans/$planId/subscribe", idempotencyKey, planId) {
            val subscription = merchantBillingService.subscribe(currentUser.userId, planId)
            201 to mapOf("success" to true, "subscription" to subscription)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/billing-subscriptions/my")
    fun getMySubscriptions(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "subscriptions" to merchantBillingService.getMySubscriptions(currentUser.userId)))

    @PostMapping("/billing-subscriptions/{subscriptionId}/cancel")
    fun cancelSubscription(
        @PathVariable subscriptionId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "subscription" to merchantBillingService.cancelSubscription(currentUser.userId, subscriptionId)))

    @ExceptionHandler(MerchantNotFoundException::class)
    fun handleMerchantNotFound(ex: MerchantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidBillingPlanException::class)
    fun handleInvalidBillingPlan(ex: InvalidBillingPlanException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_BILLING_PLAN", ex.message ?: "Bad request"))

    @ExceptionHandler(BillingPlanNotFoundException::class)
    fun handleBillingPlanNotFound(ex: BillingPlanNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("BILLING_PLAN_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(BillingSubscriptionNotFoundException::class)
    fun handleBillingSubscriptionNotFound(ex: BillingSubscriptionNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("BILLING_SUBSCRIPTION_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(SelfSubscriptionException::class)
    fun handleSelfSubscription(ex: SelfSubscriptionException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("SELF_SUBSCRIPTION_NOT_ALLOWED", ex.message ?: "Bad request"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(BillingNoAccountException::class)
    fun handleNoAccount(ex: BillingNoAccountException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleIdempotencyConflict(ex: IdempotencyConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleIdempotencyInProgress(ex: IdempotencyInProgressException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))
}
