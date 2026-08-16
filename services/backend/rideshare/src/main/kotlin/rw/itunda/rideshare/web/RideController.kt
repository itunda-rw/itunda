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
import rw.itunda.rideshare.InvalidRideDriverLocationException
import rw.itunda.rideshare.InvalidRideLocationException
import rw.itunda.rideshare.InvalidRideRatingException
import rw.itunda.rideshare.InvalidRideTripStatusTransitionException
import rw.itunda.rideshare.InvalidScheduledRideTimeException
import rw.itunda.rideshare.RideDriverAlreadyOnTripException
import rw.itunda.rideshare.RideDriverAlreadyRegisteredException
import rw.itunda.rideshare.RideDriverNoWalletException
import rw.itunda.rideshare.RideDriverNotAvailableException
import rw.itunda.rideshare.RideDriverNotRegisteredException
import rw.itunda.rideshare.RideDriverService
import rw.itunda.rideshare.RideNoActiveOfferException
import rw.itunda.rideshare.RideNoRemainingStopsException
import rw.itunda.rideshare.RidePinMismatchException
import rw.itunda.rideshare.RideSelfTripException
import rw.itunda.rideshare.RideStopInput
import rw.itunda.rideshare.RideTooManyStopsException
import rw.itunda.rideshare.RideTripAlreadyClaimedException
import rw.itunda.rideshare.RideTripAlreadyReviewedException
import rw.itunda.rideshare.RideTripNotFoundException
import rw.itunda.rideshare.RideTripNotYetCompletedException
import rw.itunda.rideshare.RideTripReviewService
import rw.itunda.rideshare.RideTripService

data class SetDriverAvailabilityRequest(val available: Boolean)
data class UpdateDriverLocationRequest(val latitude: Double, val longitude: Double)
// Real Kakao T-style post-trip driver rating (item 213) -- see RideTripReview.kt's own
// doc comment.
data class SubmitRideReviewRequest(val rating: Int, val comment: String? = null)
data class RideStopRequest(val address: String, val latitude: Double, val longitude: Double)
data class RequestTripRequest(
    val pickupAddress: String,
    val pickupLatitude: Double,
    val pickupLongitude: Double,
    val dropoffAddress: String,
    val dropoffLatitude: Double,
    val dropoffLongitude: Double,
    // Real Kakao T 예약 호출 (scheduled ride booking, item 212) -- null (the default)
    // means ASAP, every existing caller's behavior completely unchanged. See
    // RideTrip.scheduledFor's own doc comment.
    val scheduledFor: java.time.Instant? = null,
    // Real Kakao T-style multi-stop rides (item 214) -- empty (the default) means a
    // direct pickup-to-dropoff trip, every existing caller's behavior completely
    // unchanged. See RideTripStop.kt's own doc comment.
    val stops: List<RideStopRequest> = emptyList(),
)
// Real Uber "Verify Your Ride" PIN (see RideTrip.pin's own doc comment) -- entered by
// the driver, told to them verbally by the passenger right before pickup.
data class StartTripRequest(val pin: String)

// Real Kakao T-style ride-hailing -- see RideTripService's own doc comment for the full
// sourced account. Normal itunda-user JWT gate.
@RestController
@RequestMapping("/api/v1/rides")
class RideController(
    private val rideDriverService: RideDriverService,
    private val rideTripService: RideTripService,
    private val rideTripReviewService: RideTripReviewService,
    private val idempotencyService: IdempotencyService,
) {
    @PostMapping("/drivers/register")
    fun registerDriver(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val driver = rideDriverService.register(currentUser.userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "driver" to driver))
    }

    @GetMapping("/drivers/me")
    fun getMyDriverProfile(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "driver" to rideDriverService.getMyDriverProfile(currentUser.userId)))

    @PostMapping("/drivers/availability")
    fun setAvailability(
        @RequestBody request: SetDriverAvailabilityRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "driver" to rideDriverService.setAvailability(currentUser.userId, request.available)))

    @PostMapping("/drivers/location")
    fun updateLocation(
        @RequestBody request: UpdateDriverLocationRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "driver" to rideDriverService.updateLocation(currentUser.userId, request.latitude, request.longitude)))

    @PostMapping("/trips")
    fun requestTrip(
        @RequestBody request: RequestTripRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/rides/trips", idempotencyKey, request) {
            val trip = rideTripService.requestTrip(
                currentUser.userId, request.pickupAddress, request.pickupLatitude, request.pickupLongitude,
                request.dropoffAddress, request.dropoffLatitude, request.dropoffLongitude, request.scheduledFor,
                request.stops.map { RideStopInput(it.address, it.latitude, it.longitude) },
            )
            201 to mapOf("success" to true, "trip" to trip, "stops" to rideTripService.getTripStops(trip.id))
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/trips/available")
    fun getAvailableTrips(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "trips" to rideTripService.getAvailableTrips(currentUser.userId)))

    @GetMapping("/trips/my-trips")
    fun getMyTrips(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = rideTripService.getMyTrips(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "trips" to page.content) + pageMeta(page))
    }

    @GetMapping("/trips/my-driver-trips")
    fun getMyDriverTrips(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = rideTripService.getMyDriverTrips(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "trips" to page.content) + pageMeta(page))
    }

    @PostMapping("/trips/{tripId}/accept")
    fun acceptTrip(@PathVariable tripId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "trip" to rideTripService.acceptTrip(currentUser.userId, tripId)))

    @PostMapping("/trips/{tripId}/decline")
    fun declineTrip(@PathVariable tripId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "trip" to rideTripService.declineTrip(currentUser.userId, tripId)))

    @PostMapping("/trips/{tripId}/start")
    fun startTrip(
        @PathVariable tripId: String,
        @RequestBody request: StartTripRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "trip" to rideTripService.startTrip(currentUser.userId, tripId, request.pin)))

    // Real passenger-only PIN lookup -- see RideTripService.getTripPin's own doc comment
    // for why the driver never sees this through any other endpoint.
    @GetMapping("/trips/{tripId}/pin")
    fun getTripPin(@PathVariable tripId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "pin" to rideTripService.getTripPin(currentUser.userId, tripId)))

    @PostMapping("/trips/{tripId}/complete")
    fun completeTrip(@PathVariable tripId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "trip" to rideTripService.completeTrip(currentUser.userId, tripId)))

    @PostMapping("/trips/{tripId}/cancel")
    fun cancelTrip(@PathVariable tripId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "trip" to rideTripService.cancelTrip(currentUser.userId, tripId)))

    // Real Kakao T-style multi-stop rides (item 214) -- see RideTripStop.kt's own doc
    // comment.
    @GetMapping("/trips/{tripId}/stops")
    fun getTripStops(@PathVariable tripId: String): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "stops" to rideTripService.getTripStops(tripId)))

    @PostMapping("/trips/{tripId}/stops/arrive")
    fun arriveAtStop(@PathVariable tripId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "stop" to rideTripService.arriveAtStop(currentUser.userId, tripId)))

    // Real Kakao T-style post-trip driver rating (item 213) -- see RideTripReviewService's
    // own doc comment.
    @PostMapping("/trips/{tripId}/review")
    fun submitReview(
        @PathVariable tripId: String,
        @RequestBody request: SubmitRideReviewRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val review = rideTripReviewService.submitReview(currentUser.userId, tripId, request.rating, request.comment)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "review" to review))
    }

    @GetMapping("/drivers/{driverId}/reviews")
    fun getDriverReviews(
        @PathVariable driverId: String,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val page = rideTripReviewService.getDriverReviews(driverId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "reviews" to page.content) + pageMeta(page))
    }

    @GetMapping("/drivers/{driverId}/rating")
    fun getDriverRating(@PathVariable driverId: String): ResponseEntity<Map<String, Any?>> {
        val rating = rideTripReviewService.getDriverRating(driverId)
        return ResponseEntity.ok(mapOf("success" to true, "average" to rating.average, "count" to rating.count))
    }

    @ExceptionHandler(RideDriverAlreadyRegisteredException::class)
    fun handleAlreadyRegistered(ex: RideDriverAlreadyRegisteredException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("RIDE_DRIVER_ALREADY_REGISTERED", ex.message ?: "Conflict"))

    @ExceptionHandler(RideDriverNoWalletException::class)
    fun handleNoWallet(ex: RideDriverNoWalletException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(RideDriverNotRegisteredException::class)
    fun handleNotRegistered(ex: RideDriverNotRegisteredException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("RIDE_DRIVER_NOT_REGISTERED", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidRideDriverLocationException::class)
    fun handleInvalidDriverLocation(ex: InvalidRideDriverLocationException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_LOCATION", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidRideLocationException::class)
    fun handleInvalidTripLocation(ex: InvalidRideLocationException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_LOCATION", ex.message ?: "Bad request"))

    @ExceptionHandler(RideSelfTripException::class)
    fun handleSelfTrip(ex: RideSelfTripException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("SELF_TRIP_NOT_ALLOWED", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidScheduledRideTimeException::class)
    fun handleInvalidScheduledRideTime(ex: InvalidScheduledRideTimeException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_SCHEDULED_TIME", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidRideRatingException::class)
    fun handleInvalidRating(ex: InvalidRideRatingException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_RATING", ex.message ?: "Bad request"))

    @ExceptionHandler(RideTripNotYetCompletedException::class)
    fun handleTripNotYetCompleted(ex: RideTripNotYetCompletedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("RIDE_TRIP_NOT_YET_COMPLETED", ex.message ?: "Conflict"))

    @ExceptionHandler(RideTripAlreadyReviewedException::class)
    fun handleTripAlreadyReviewed(ex: RideTripAlreadyReviewedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("RIDE_TRIP_ALREADY_REVIEWED", ex.message ?: "Conflict"))

    @ExceptionHandler(RideTooManyStopsException::class)
    fun handleTooManyStops(ex: RideTooManyStopsException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("TOO_MANY_STOPS", ex.message ?: "Bad request"))

    @ExceptionHandler(RideNoRemainingStopsException::class)
    fun handleNoRemainingStops(ex: RideNoRemainingStopsException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("NO_REMAINING_STOPS", ex.message ?: "Conflict"))

    @ExceptionHandler(RideTripNotFoundException::class)
    fun handleTripNotFound(ex: RideTripNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("RIDE_TRIP_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(RideTripAlreadyClaimedException::class)
    fun handleAlreadyClaimed(ex: RideTripAlreadyClaimedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("RIDE_TRIP_ALREADY_CLAIMED", ex.message ?: "Conflict"))

    @ExceptionHandler(RideDriverAlreadyOnTripException::class)
    fun handleAlreadyOnTrip(ex: RideDriverAlreadyOnTripException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("RIDE_DRIVER_ALREADY_ON_TRIP", ex.message ?: "Conflict"))

    @ExceptionHandler(RideDriverNotAvailableException::class)
    fun handleDriverNotAvailable(ex: RideDriverNotAvailableException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("RIDE_DRIVER_NOT_AVAILABLE", ex.message ?: "Conflict"))

    @ExceptionHandler(InvalidRideTripStatusTransitionException::class)
    fun handleInvalidTransition(ex: InvalidRideTripStatusTransitionException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("INVALID_RIDE_STATUS_TRANSITION", ex.message ?: "Conflict"))

    @ExceptionHandler(RidePinMismatchException::class)
    fun handlePinMismatch(ex: RidePinMismatchException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INCORRECT_RIDE_PIN", ex.message ?: "Bad request"))

    @ExceptionHandler(RideNoActiveOfferException::class)
    fun handleNoActiveOffer(ex: RideNoActiveOfferException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("NO_ACTIVE_OFFER", ex.message ?: "Conflict"))

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
