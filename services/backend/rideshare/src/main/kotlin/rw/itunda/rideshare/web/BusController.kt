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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import rw.itunda.rideshare.BusBookingAlreadyCancelledException
import rw.itunda.rideshare.BusBookingNotFoundException
import rw.itunda.rideshare.BusNoAccountException
import rw.itunda.rideshare.BusService
import rw.itunda.rideshare.BusTripAlreadyDepartedException
import rw.itunda.rideshare.BusTripNotFoundException
import rw.itunda.rideshare.InsufficientSeatsException
import rw.itunda.rideshare.InvalidBusTripException
import java.math.BigDecimal
import java.time.Instant

data class PostBusTripRequest(
    val origin: String, val destination: String, val departureTime: Instant,
    val totalSeats: Int, val farePerSeat: BigDecimal,
)
data class BookBusSeatsRequest(val tripId: String, val seatCount: Int)

// Real Kakao T 시외버스 (intercity bus booking) -- see BusService's own doc comment
// for the full sourced account. Normal itunda-user JWT gate.
@RestController
@RequestMapping("/api/v1/bus")
class BusController(
    private val busService: BusService,
    private val idempotencyService: IdempotencyService,
) {

    @PostMapping("/trips")
    fun postTrip(@RequestBody request: PostBusTripRequest, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val trip = busService.postTrip(
            currentUser.userId, request.origin, request.destination, request.departureTime,
            request.totalSeats, request.farePerSeat,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "trip" to trip))
    }

    @GetMapping("/trips/mine")
    fun getMyTrips(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "trips" to busService.getMyTrips(currentUser.userId)))

    @GetMapping("/trips/{tripId}/bookings")
    fun getTripBookings(@PathVariable tripId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "bookings" to busService.getTripBookings(currentUser.userId, tripId)))

    @GetMapping("/trips/search")
    fun searchTrips(
        @RequestParam(required = false) origin: String?,
        @RequestParam(required = false) destination: String?,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "trips" to busService.searchTrips(origin, destination)))

    // Real bug found live (2026-08-02): unlike RideController's own requestTrip (the
    // established template every trip/booking-creation endpoint this session added was
    // supposed to match), this had no Idempotency-Key requirement -- bookSeats() creates
    // a brand-new BusBooking row and decrements the real @Version-guarded
    // BusTrip.availableSeats on every call, but nothing stops the SAME rider from
    // booking the same trip twice on a naive client retry (no "already booked this
    // trip" guard exists). @Version on BusTrip only protects against two DIFFERENT
    // concurrent requests racing for the same seats -- it does nothing for one rider's
    // sequential retry, which would just successfully book twice as long as seats remain.
    @PostMapping("/bookings")
    fun bookSeats(
        @RequestBody request: BookBusSeatsRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/bus/bookings", idempotencyKey, request) {
            val booking = busService.bookSeats(currentUser.userId, request.tripId, request.seatCount)
            HttpStatus.CREATED.value() to mapOf("success" to true, "booking" to booking)
        }
        return ResponseEntity.status(status).body(body)
    }

    // Real gap found 2026-09-05 (feedback_idempotency_key_sweep re-audit) --
    // cancelBooking is real money movement (a real refund posted via
    // ledgerService.postLedgerTransaction) guarded by BusBookingAlreadyCancelledException,
    // with no Idempotency-Key protection. A lost-response retry after a successful
    // cancel used to hit a confusing conflict for a cancellation that already succeeded.
    @PostMapping("/bookings/{bookingId}/cancel")
    fun cancelBooking(
        @PathVariable bookingId: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/bus/bookings/$bookingId/cancel", idempotencyKey, currentUser.userId) {
            200 to mapOf("success" to true, "booking" to busService.cancelBooking(currentUser.userId, bookingId))
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/bookings/my-history")
    fun getMyBookings(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = busService.getMyBookings(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "bookings" to page.content) + pageMeta(page))
    }

    @ExceptionHandler(BusTripNotFoundException::class)
    fun handleTripNotFound(ex: BusTripNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("BUS_TRIP_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidBusTripException::class)
    fun handleInvalidTrip(ex: InvalidBusTripException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_BUS_TRIP", ex.message ?: "Bad request"))

    @ExceptionHandler(BusNoAccountException::class)
    fun handleNoAccount(ex: BusNoAccountException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InsufficientSeatsException::class)
    fun handleInsufficientSeats(ex: InsufficientSeatsException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("INSUFFICIENT_SEATS", ex.message ?: "Conflict"))

    @ExceptionHandler(BusBookingNotFoundException::class)
    fun handleBookingNotFound(ex: BusBookingNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("BUS_BOOKING_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(BusBookingAlreadyCancelledException::class)
    fun handleAlreadyCancelled(ex: BusBookingAlreadyCancelledException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("BUS_BOOKING_ALREADY_CANCELLED", ex.message ?: "Conflict"))

    @ExceptionHandler(BusTripAlreadyDepartedException::class)
    fun handleAlreadyDeparted(ex: BusTripAlreadyDepartedException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("BUS_TRIP_ALREADY_DEPARTED", ex.message ?: "Conflict"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))

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
