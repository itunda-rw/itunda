package rw.itunda.rideshare.web

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import rw.itunda.rideshare.InvalidParkingLocationException
import rw.itunda.rideshare.ParkingNoWalletException
import rw.itunda.rideshare.ParkingSelfRentalException
import rw.itunda.rideshare.ParkingService
import rw.itunda.rideshare.ParkingSessionAlreadyEndedException
import rw.itunda.rideshare.ParkingSessionNotFoundException
import rw.itunda.rideshare.ParkingSpotNotAvailableException
import rw.itunda.rideshare.ParkingSpotNotFoundException
import java.math.BigDecimal

data class RegisterParkingSpotRequest(val address: String, val latitude: Double, val longitude: Double, val hourlyRate: BigDecimal)
data class SetParkingSpotAvailabilityRequest(val available: Boolean)
data class StartParkingSessionRequest(val spotId: String)

// Real Kakao T 주차 (Kakao T Parking) -- see ParkingService's own doc comment for the
// full sourced account. Normal itunda-user JWT gate.
@RestController
@RequestMapping("/api/v1/parking")
class ParkingController(private val parkingService: ParkingService) {

    @PostMapping("/spots")
    fun registerSpot(@RequestBody request: RegisterParkingSpotRequest, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val spot = parkingService.registerSpot(currentUser.userId, request.address, request.latitude, request.longitude, request.hourlyRate)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "spot" to spot))
    }

    @GetMapping("/spots/mine")
    fun getMySpots(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "spots" to parkingService.getMySpots(currentUser.userId)))

    @PostMapping("/spots/{spotId}/availability")
    fun setAvailability(
        @PathVariable spotId: String,
        @RequestBody request: SetParkingSpotAvailabilityRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "spot" to parkingService.setAvailability(currentUser.userId, spotId, request.available)))

    @GetMapping("/spots/nearby")
    fun getNearbySpots(
        @RequestParam latitude: Double,
        @RequestParam longitude: Double,
        @RequestParam(defaultValue = "5.0") radiusKm: Double,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "spots" to parkingService.getNearbySpots(latitude, longitude, radiusKm)))

    @PostMapping("/sessions")
    fun startSession(@RequestBody request: StartParkingSessionRequest, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val session = parkingService.startSession(currentUser.userId, request.spotId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "session" to session))
    }

    @PostMapping("/sessions/{sessionId}/end")
    fun endSession(@PathVariable sessionId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "session" to parkingService.endSession(currentUser.userId, sessionId)))

    // Real manual trigger for `ParkingAbandonedSessionScheduler`'s own real 60-second
    // cron -- same "let a coordinator/admin fire the real due sweep on demand rather
    // than waiting on wall-clock time" precedent `BikeRentalController.processAbandonedRentals`
    // already establishes. Admin-gated since this force-settles real other users' money,
    // not a self-service action.
    @PostMapping("/sessions/process-abandoned")
    @PreAuthorize("hasRole('ADMIN')")
    fun processAbandonedSessions(): ResponseEntity<Map<String, Any?>> {
        val due = parkingService.getAbandonedSessions()
        val processed = due.mapNotNull { parkingService.forceEndAbandonedSession(it.id) }
        return ResponseEntity.ok(mapOf("success" to true, "processedCount" to processed.size, "sessions" to processed))
    }

    @GetMapping("/sessions/my-history")
    fun getMyRentalHistory(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = parkingService.getMyRentalHistory(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "sessions" to page.content) + pageMeta(page))
    }

    @ExceptionHandler(ParkingSpotNotFoundException::class)
    fun handleNotFound(ex: ParkingSpotNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("PARKING_SPOT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(ParkingSpotNotAvailableException::class)
    fun handleNotAvailable(ex: ParkingSpotNotAvailableException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("PARKING_SPOT_NOT_AVAILABLE", ex.message ?: "Conflict"))

    @ExceptionHandler(ParkingSelfRentalException::class)
    fun handleSelfRental(ex: ParkingSelfRentalException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("SELF_RENTAL_NOT_ALLOWED", ex.message ?: "Bad request"))

    @ExceptionHandler(ParkingNoWalletException::class)
    fun handleNoWallet(ex: ParkingNoWalletException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidParkingLocationException::class)
    fun handleInvalidLocation(ex: InvalidParkingLocationException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_LOCATION", ex.message ?: "Bad request"))

    @ExceptionHandler(ParkingSessionNotFoundException::class)
    fun handleSessionNotFound(ex: ParkingSessionNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("PARKING_SESSION_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(ParkingSessionAlreadyEndedException::class)
    fun handleAlreadyEnded(ex: ParkingSessionAlreadyEndedException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("PARKING_SESSION_ALREADY_ENDED", ex.message ?: "Conflict"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}
