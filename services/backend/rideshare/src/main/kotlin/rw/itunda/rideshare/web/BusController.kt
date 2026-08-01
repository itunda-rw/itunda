package rw.itunda.rideshare.web

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
import rw.itunda.rideshare.BusBookingAlreadyCancelledException
import rw.itunda.rideshare.BusBookingNotFoundException
import rw.itunda.rideshare.BusNoWalletException
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
class BusController(private val busService: BusService) {

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

    @PostMapping("/bookings")
    fun bookSeats(@RequestBody request: BookBusSeatsRequest, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val booking = busService.bookSeats(currentUser.userId, request.tripId, request.seatCount)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "booking" to booking))
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    fun cancelBooking(@PathVariable bookingId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "booking" to busService.cancelBooking(currentUser.userId, bookingId)))

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

    @ExceptionHandler(BusNoWalletException::class)
    fun handleNoWallet(ex: BusNoWalletException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

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
}
