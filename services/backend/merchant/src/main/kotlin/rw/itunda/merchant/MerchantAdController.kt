package rw.itunda.merchant

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError

data class CreateAdRequest(val title: String, val description: String? = null, val radiusMeters: Int, val days: Int)

// Real radius-targeted local business ads -- see MerchantAd.kt's own doc comment. Ad
// creation/extension IS a real payment (fee_revenue), so it's Idempotency-Key-gated,
// same convention as MarketplaceService.boostListing's own endpoint.
@RestController
@RequestMapping("/api/v1/merchant")
class MerchantAdController(
    private val merchantAdService: MerchantAdService,
    private val idempotencyService: IdempotencyService,
) {
    @PostMapping("/ads")
    fun createOrExtendAd(
        @RequestBody request: CreateAdRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/merchant/ads", idempotencyKey, request) {
            val ad = merchantAdService.createOrExtendAd(currentUser.userId, request.title, request.description, request.radiusMeters, request.days)
            201 to mapOf("success" to true, "ad" to ad)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/ads/me")
    fun getMyAd(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "ad" to merchantAdService.getMyAd(currentUser.userId)))

    @GetMapping("/ads/nearby")
    fun nearby(
        @RequestParam latitude: Double,
        @RequestParam longitude: Double,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "ads" to merchantAdService.nearby(latitude, longitude)))

    @ExceptionHandler(MerchantNotFoundException::class)
    fun handleMerchantNotFound(ex: MerchantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(MerchantLocationRequiredException::class)
    fun handleLocationRequired(ex: MerchantLocationRequiredException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("MERCHANT_LOCATION_REQUIRED", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidAdTitleException::class)
    fun handleInvalidTitle(ex: InvalidAdTitleException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_AD_TITLE", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidAdRadiusException::class)
    fun handleInvalidRadius(ex: InvalidAdRadiusException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_AD_RADIUS", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidAdDurationException::class)
    fun handleInvalidDuration(ex: InvalidAdDurationException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_AD_DURATION", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidCoordinateException::class)
    fun handleInvalidCoordinate(ex: InvalidCoordinateException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_COORDINATE", ex.message ?: "Bad request"))

    @ExceptionHandler(MerchantNoAccountException::class)
    fun handleNoAccount(ex: MerchantNoAccountException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))
}
