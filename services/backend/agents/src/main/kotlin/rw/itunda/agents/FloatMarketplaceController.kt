package rw.itunda.agents

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal

data class PostFloatListingRequest(val amount: BigDecimal)
data class RequestFloatRequest(val amount: BigDecimal)

/**
 * Real peer-to-peer agent float rebalancing marketplace -- see FloatMarketplaceService's
 * own doc comment for the full sourced account. Every endpoint requires a normal itunda
 * operator JWT (`AuthenticationPrincipal`, same as `AgentOperatorController`) and derives
 * the caller's own agent identity server-side; no endpoint accepts a caller-supplied
 * agent id, so there is no ownership parameter to spoof in the first place.
 */
@RestController
@RequestMapping("/api/v1/float-marketplace")
class FloatMarketplaceController(
    private val floatMarketplaceService: FloatMarketplaceService,
    private val idempotencyService: IdempotencyService,
) {

    @PostMapping("/listings")
    fun postListing(@RequestBody request: PostFloatListingRequest, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "listing" to floatMarketplaceService.postListing(currentUser.userId, request.amount)))

    @GetMapping("/listings/nearby")
    fun nearbyListings(
        @RequestParam latitude: Double,
        @RequestParam longitude: Double,
        @RequestParam(defaultValue = "20") radiusKm: Double,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "listings" to floatMarketplaceService.getNearbyListings(currentUser.userId, latitude, longitude, radiusKm)))

    @GetMapping("/listings/mine")
    fun myListings(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "listings" to floatMarketplaceService.myListings(currentUser.userId)))

    @PostMapping("/listings/{listingId}/cancel")
    fun cancelListing(@PathVariable listingId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "listing" to floatMarketplaceService.cancelListing(currentUser.userId, listingId)))

    @PostMapping("/listings/{listingId}/requests")
    fun requestFloat(
        @PathVariable listingId: String,
        @RequestBody request: RequestFloatRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "request" to floatMarketplaceService.requestFloat(currentUser.userId, listingId, request.amount)))

    @GetMapping("/requests/mine")
    fun myRequests(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "requests" to floatMarketplaceService.myRequests(currentUser.userId)))

    @GetMapping("/requests/incoming")
    fun incomingRequests(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "requests" to floatMarketplaceService.myIncomingRequests(currentUser.userId)))

    // Real money movement -- requires a real Idempotency-Key, same discipline as
    // AgentOperatorController.cashOut / CooperativeController.repayAdvance.
    @PostMapping("/requests/{requestId}/accept")
    fun acceptRequest(
        @PathVariable requestId: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/float-marketplace/requests/$requestId/accept", idempotencyKey, currentUser.userId) {
            200 to mapOf("success" to true, "request" to floatMarketplaceService.acceptRequest(currentUser.userId, requestId))
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/requests/{requestId}/decline")
    fun declineRequest(@PathVariable requestId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "request" to floatMarketplaceService.declineRequest(currentUser.userId, requestId)))

    @ExceptionHandler(AgentOperatorNotAuthorizedException::class)
    fun handleUnauthorized(ex: AgentOperatorNotAuthorizedException) = ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("AGENT_OPERATOR_NOT_AUTHORIZED", ex.message ?: "Forbidden"))
    @ExceptionHandler(AgentSuspendedException::class)
    fun handleSuspended(ex: AgentSuspendedException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("AGENT_SUSPENDED", ex.message ?: "Conflict"))
    @ExceptionHandler(FloatListingNotFoundException::class)
    fun handleListingNotFound(ex: FloatListingNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("FLOAT_LISTING_NOT_FOUND", ex.message ?: "Not found"))
    @ExceptionHandler(FloatTransferRequestNotFoundException::class)
    fun handleRequestNotFound(ex: FloatTransferRequestNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("FLOAT_TRANSFER_REQUEST_NOT_FOUND", ex.message ?: "Not found"))
    @ExceptionHandler(FloatListingNotOpenException::class)
    fun handleListingNotOpen(ex: FloatListingNotOpenException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("FLOAT_LISTING_NOT_OPEN", ex.message ?: "Conflict"))
    @ExceptionHandler(FloatListingInsufficientRemainingException::class)
    fun handleInsufficientRemaining(ex: FloatListingInsufficientRemainingException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("FLOAT_LISTING_INSUFFICIENT_REMAINING", ex.message ?: "Unprocessable"))
    @ExceptionHandler(FloatTransferRequestNotPendingException::class)
    fun handleRequestNotPending(ex: FloatTransferRequestNotPendingException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("FLOAT_TRANSFER_REQUEST_NOT_PENDING", ex.message ?: "Conflict"))
    @ExceptionHandler(FloatSelfTransferException::class)
    fun handleSelfTransfer(ex: FloatSelfTransferException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("FLOAT_SELF_TRANSFER", ex.message ?: "Bad request"))
    @ExceptionHandler(FloatListingInsufficientCashException::class)
    fun handleInsufficientCash(ex: FloatListingInsufficientCashException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("FLOAT_LISTING_INSUFFICIENT_CASH", ex.message ?: "Unprocessable"))
    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleIdempotencyConflict(ex: IdempotencyConflictException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))
    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleIdempotencyProgress(ex: IdempotencyInProgressException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleInvalid(ex: IllegalArgumentException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_FLOAT_MARKETPLACE_REQUEST", ex.message ?: "Bad request"))
}
