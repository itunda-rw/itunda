package rw.itunda.rideshare.web

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
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
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import rw.itunda.rideshare.DesignatedDriverAlreadyRegisteredException
import rw.itunda.rideshare.DesignatedDriverNoWalletException
import rw.itunda.rideshare.DesignatedDriverNotRegisteredException
import rw.itunda.rideshare.DesignatedDriverSelfTripException
import rw.itunda.rideshare.DesignatedDriverService
import rw.itunda.rideshare.DesignatedDriverTripAlreadyClaimedException
import rw.itunda.rideshare.DesignatedDriverTripNotFoundException
import rw.itunda.rideshare.InvalidDesignatedDriverLocationException
import rw.itunda.rideshare.InvalidDesignatedDriverTripStatusTransitionException

data class RegisterDesignatedDriverRequest(val licenseNumber: String)
data class SetDesignatedDriverAvailabilityRequest(val available: Boolean)
data class UpdateDesignatedDriverLocationRequest(val latitude: Double, val longitude: Double)
data class RequestDesignatedDriverTripRequest(
    val pickupAddress: String,
    val pickupLatitude: Double,
    val pickupLongitude: Double,
    val dropoffAddress: String,
    val dropoffLatitude: Double,
    val dropoffLongitude: Double,
    val vehicleMake: String,
    val vehicleModel: String,
    val vehiclePlate: String,
)

// Real Kakao T 대리운전 (designated driver) -- see DesignatedDriverService's own doc
// comment for the full sourced account. Normal itunda-user JWT gate.
@RestController
@RequestMapping("/api/v1/designated-driver")
class DesignatedDriverController(
    private val designatedDriverService: DesignatedDriverService,
    private val idempotencyService: IdempotencyService,
) {

    @PostMapping("/drivers/register")
    fun registerDriver(
        @RequestBody request: RegisterDesignatedDriverRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val driver = designatedDriverService.register(currentUser.userId, request.licenseNumber)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "driver" to driver))
    }

    @GetMapping("/drivers/me")
    fun getMyDriverProfile(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "driver" to designatedDriverService.getMyDriverProfile(currentUser.userId)))

    @PostMapping("/drivers/availability")
    fun setAvailability(
        @RequestBody request: SetDesignatedDriverAvailabilityRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "driver" to designatedDriverService.setAvailability(currentUser.userId, request.available)))

    @PostMapping("/drivers/location")
    fun updateLocation(
        @RequestBody request: UpdateDesignatedDriverLocationRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "driver" to designatedDriverService.updateLocation(currentUser.userId, request.latitude, request.longitude)))

    // Real bug found live (2026-08-02): unlike RideController's own requestTrip (its
    // direct structural template), this endpoint had no Idempotency-Key requirement --
    // requestTrip() creates a brand-new DesignatedDriverTrip row with a real fare hold
    // on every call and has no "customer already has an active trip" guard, so a
    // client's network-timeout retry of the exact same request would create a real
    // SECOND trip and hold the fare twice. @Version on DesignatedDriverTrip (already
    // present) doesn't help here -- there's no existing row for a retry to conflict
    // against, since each attempt inserts a new one.
    @PostMapping("/trips")
    fun requestTrip(
        @RequestBody request: RequestDesignatedDriverTripRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/designated-driver/trips", idempotencyKey, request) {
            val trip = designatedDriverService.requestTrip(
                currentUser.userId, request.pickupAddress, request.pickupLatitude, request.pickupLongitude,
                request.dropoffAddress, request.dropoffLatitude, request.dropoffLongitude,
                request.vehicleMake, request.vehicleModel, request.vehiclePlate,
            )
            HttpStatus.CREATED.value() to mapOf("success" to true, "trip" to trip)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/trips/available")
    fun getAvailableTrips(): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "trips" to designatedDriverService.getAvailableTrips()))

    @GetMapping("/trips/my-trips")
    fun getMyTrips(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = designatedDriverService.getMyTrips(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "trips" to page.content) + pageMeta(page))
    }

    @GetMapping("/trips/my-driver-trips")
    fun getMyDriverTrips(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = designatedDriverService.getMyDriverTrips(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "trips" to page.content) + pageMeta(page))
    }

    @PostMapping("/trips/{tripId}/accept")
    fun acceptTrip(@PathVariable tripId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "trip" to designatedDriverService.acceptTrip(currentUser.userId, tripId)))

    @PostMapping("/trips/{tripId}/start-driving")
    fun startDriving(@PathVariable tripId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "trip" to designatedDriverService.startDriving(currentUser.userId, tripId)))

    @PostMapping("/trips/{tripId}/complete")
    fun completeTrip(@PathVariable tripId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "trip" to designatedDriverService.completeTrip(currentUser.userId, tripId)))

    @PostMapping("/trips/{tripId}/cancel")
    fun cancelTrip(@PathVariable tripId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "trip" to designatedDriverService.cancelTrip(currentUser.userId, tripId)))

    @ExceptionHandler(DesignatedDriverAlreadyRegisteredException::class)
    fun handleAlreadyRegistered(ex: DesignatedDriverAlreadyRegisteredException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("DESIGNATED_DRIVER_ALREADY_REGISTERED", ex.message ?: "Conflict"))

    @ExceptionHandler(DesignatedDriverNoWalletException::class)
    fun handleNoWallet(ex: DesignatedDriverNoWalletException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(DesignatedDriverNotRegisteredException::class)
    fun handleNotRegistered(ex: DesignatedDriverNotRegisteredException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("DESIGNATED_DRIVER_NOT_REGISTERED", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidDesignatedDriverLocationException::class)
    fun handleInvalidLocation(ex: InvalidDesignatedDriverLocationException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_LOCATION", ex.message ?: "Bad request"))

    @ExceptionHandler(DesignatedDriverSelfTripException::class)
    fun handleSelfTrip(ex: DesignatedDriverSelfTripException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("SELF_TRIP_NOT_ALLOWED", ex.message ?: "Bad request"))

    @ExceptionHandler(DesignatedDriverTripNotFoundException::class)
    fun handleTripNotFound(ex: DesignatedDriverTripNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("DESIGNATED_DRIVER_TRIP_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(DesignatedDriverTripAlreadyClaimedException::class)
    fun handleAlreadyClaimed(ex: DesignatedDriverTripAlreadyClaimedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("DESIGNATED_DRIVER_TRIP_ALREADY_CLAIMED", ex.message ?: "Conflict"))

    @ExceptionHandler(InvalidDesignatedDriverTripStatusTransitionException::class)
    fun handleInvalidTransition(ex: InvalidDesignatedDriverTripStatusTransitionException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("INVALID_DESIGNATED_DRIVER_STATUS_TRANSITION", ex.message ?: "Conflict"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))
}
