package rw.itunda.insurance.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
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
import rw.itunda.core.domain.SeasonRainfallIndex
import rw.itunda.core.domain.WeatherIndexCropType
import rw.itunda.core.domain.WeatherIndexPolicy
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.AccountFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.insurance.InvalidWeatherIndexEnrollmentException
import rw.itunda.insurance.NoAccountException
import rw.itunda.insurance.SeasonRainfallIndexAlreadyPublishedException
import rw.itunda.insurance.WeatherIndexInsuranceService
import rw.itunda.insurance.WeatherIndexPolicyNotCancellableException
import rw.itunda.insurance.WeatherIndexPolicyNotFoundException
import java.math.BigDecimal

data class EnrollCropIndexPolicyRequest(val cropType: WeatherIndexCropType, val district: String, val season: String, val insuredAmount: BigDecimal)
data class PublishSeasonIndexRequest(val rainfallIndexPercent: Double, val droughtThresholdPercent: Double)

// Real Rwanda NAIS-style parametric crop weather-index insurance -- see
// WeatherIndexInsuranceService's own doc comment for the full sourced account and both
// honest v1 limitations. Kept in its own file rather than added to InsuranceController.kt:
// that file is already 200+ lines covering a structurally different (claims-based)
// product family.
@RestController
@RequestMapping("/api/v1/insurance/crop-index")
class WeatherIndexInsuranceController(
    private val weatherIndexInsuranceService: WeatherIndexInsuranceService,
    private val idempotencyService: IdempotencyService,
) {

    @GetMapping("/catalog")
    fun getCatalog(): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "catalog" to weatherIndexInsuranceService.getCatalog()))

    private fun policyMap(policy: WeatherIndexPolicy) = mapOf(
        "id" to policy.id,
        "cropType" to policy.cropType.name,
        "district" to policy.district,
        "season" to policy.season,
        "insuredAmount" to policy.insuredAmount,
        "premiumAmount" to policy.premiumAmount,
        "status" to policy.status.name,
        "createdAt" to policy.createdAt.toString(),
        "payoutAt" to policy.payoutAt?.toString(),
    )

    @PostMapping("/policies")
    fun enroll(
        @RequestBody request: EnrollCropIndexPolicyRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/insurance/crop-index/policies", idempotencyKey, request) {
            val policy = weatherIndexInsuranceService.enroll(currentUser.userId, request.cropType, request.district, request.season, request.insuredAmount)
            201 to mapOf("success" to true, "policy" to policyMap(policy))
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/policies")
    fun getMyPolicies(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "policies" to weatherIndexInsuranceService.getMyPolicies(currentUser.userId).map(::policyMap)))

    @GetMapping("/policies/{id}")
    fun getPolicy(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "policy" to policyMap(weatherIndexInsuranceService.getPolicy(currentUser.userId, id))))

    @PostMapping("/policies/{id}/cancel")
    fun cancel(
        @PathVariable id: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/insurance/crop-index/policies/$id/cancel", idempotencyKey, id) {
            val policy = weatherIndexInsuranceService.cancel(currentUser.userId, id)
            200 to mapOf("success" to true, "policy" to policyMap(policy))
        }
        return ResponseEntity.status(status).body(body)
    }

    private fun indexMap(index: SeasonRainfallIndex) = mapOf(
        "district" to index.district,
        "season" to index.season,
        "rainfallIndexPercent" to index.rainfallIndexPercent,
        "droughtThresholdPercent" to index.droughtThresholdPercent,
        "publishedAt" to index.publishedAt.toString(),
        "publishedByAdminId" to index.publishedByAdminId,
    )

    // ADMIN-gated the same @PreAuthorize("hasRole('ADMIN')") way
    // WeeklySavingsController.processDue is (this route doesn't live under
    // /api/v1/system/**, so it doesn't inherit SecurityConfig's blanket ADMIN gate there).
    // Deliberately NO Idempotency-Key: this is a one-time, admin-transcribed real fact with
    // its own real DB-unique-constraint idempotency guard on (district, season) -- not a
    // retryable client action -- same reasoning InsuranceController.submitClaim's own
    // comment gives for why claim filing isn't Idempotency-Key-gated either.
    @PostMapping("/districts/{district}/seasons/{season}/index")
    @PreAuthorize("hasRole('ADMIN')")
    fun publishSeasonIndex(
        @PathVariable district: String,
        @PathVariable season: String,
        @RequestBody request: PublishSeasonIndexRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> {
        val index = weatherIndexInsuranceService.publishSeasonIndex(currentUser.userId, district, season, request.rainfallIndexPercent, request.droughtThresholdPercent)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "index" to indexMap(index)))
    }

    @GetMapping("/districts/{district}/seasons/{season}/index")
    fun getSeasonIndex(@PathVariable district: String, @PathVariable season: String): ResponseEntity<Map<String, Any?>> {
        val index = weatherIndexInsuranceService.getSeasonIndex(district, season)
        return ResponseEntity.ok(mapOf("success" to true, "index" to index?.let(::indexMap)))
    }

    @ExceptionHandler(WeatherIndexPolicyNotFoundException::class)
    fun handleNotFound(ex: WeatherIndexPolicyNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WEATHER_INDEX_POLICY_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(WeatherIndexPolicyNotCancellableException::class)
    fun handleNotCancellable(ex: WeatherIndexPolicyNotCancellableException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("WEATHER_INDEX_POLICY_NOT_CANCELLABLE", ex.message ?: "Conflict"))

    @ExceptionHandler(SeasonRainfallIndexAlreadyPublishedException::class)
    fun handleAlreadyPublished(ex: SeasonRainfallIndexAlreadyPublishedException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("SEASON_RAINFALL_INDEX_ALREADY_PUBLISHED", ex.message ?: "Conflict"))

    @ExceptionHandler(InvalidWeatherIndexEnrollmentException::class)
    fun handleInvalidEnrollment(ex: InvalidWeatherIndexEnrollmentException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_WEATHER_INDEX_ENROLLMENT", ex.message ?: "Bad request"))

    @ExceptionHandler(NoAccountException::class)
    fun handleNoAccount(ex: NoAccountException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(AccountFrozenException::class)
    fun handleAccountFrozen(ex: AccountFrozenException) = ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("ACCOUNT_FROZEN", ex.message ?: "Account is frozen"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMIT_EXCEEDED", ex.message ?: "Too many requests"))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_REQUEST", ex.message ?: "Bad request"))
}
