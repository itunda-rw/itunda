package rw.itunda.merchant

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class SetAvailabilityRequest(val windows: List<AvailabilityWindowDto>)
data class AvailabilityWindowDto(val dayOfWeek: DayOfWeek, val startTime: LocalTime, val endTime: LocalTime)
data class CreateBookingRequest(val merchantId: String, val serviceId: String, val date: LocalDate, val startTime: LocalTime, val notes: String? = null)
data class RespondToBookingRequest(val confirm: Boolean)

// Real local-business appointment booking -- see MerchantBookingService's own doc
// comment. Normal itunda-user JWT gate; not money-moving (no payment at booking time),
// so no Idempotency-Key requirement, same discipline MerchantProductController already
// established for its own non-money-moving catalog writes.
@RestController
@RequestMapping("/api/v1/merchant")
class MerchantBookingController(
    private val merchantBookingService: MerchantBookingService,
) {
    @PostMapping("/booking/availability")
    fun setAvailability(
        @RequestBody request: SetAvailabilityRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val windows = merchantBookingService.setAvailability(
            currentUser.userId,
            request.windows.map { AvailabilityWindowRequest(it.dayOfWeek, it.startTime, it.endTime) },
        )
        return ResponseEntity.ok(mapOf("success" to true, "windows" to windows))
    }

    @GetMapping("/booking/availability")
    fun getMyAvailability(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "windows" to merchantBookingService.getMyAvailability(currentUser.userId)))

    @GetMapping("/{merchantId}/booking-availability")
    fun getAvailability(@PathVariable merchantId: String): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "windows" to merchantBookingService.getAvailability(merchantId)))

    @GetMapping("/{merchantId}/booking-slots")
    fun getAvailableSlots(
        @PathVariable merchantId: String,
        @RequestParam serviceId: String,
        @RequestParam date: LocalDate,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "slots" to merchantBookingService.getAvailableSlots(merchantId, serviceId, date)))

    @PostMapping("/bookings")
    fun createBooking(
        @RequestBody request: CreateBookingRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val booking = merchantBookingService.book(currentUser.userId, request.merchantId, request.serviceId, request.date, request.startTime, request.notes)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "booking" to booking))
    }

    @GetMapping("/bookings/my-bookings")
    fun getMyBookings(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = merchantBookingService.getMyBookings(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "bookings" to page.content) + pageMeta(page))
    }

    @GetMapping("/bookings/merchant-bookings")
    fun getMerchantBookings(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = merchantBookingService.getMerchantBookings(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "bookings" to page.content) + pageMeta(page))
    }

    @PostMapping("/bookings/{bookingId}/respond")
    fun respond(
        @PathVariable bookingId: String,
        @RequestBody request: RespondToBookingRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val booking = merchantBookingService.respond(currentUser.userId, bookingId, request.confirm)
        return ResponseEntity.ok(mapOf("success" to true, "booking" to booking))
    }

    @PostMapping("/bookings/{bookingId}/complete")
    fun markCompleted(
        @PathVariable bookingId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val booking = merchantBookingService.markCompleted(currentUser.userId, bookingId)
        return ResponseEntity.ok(mapOf("success" to true, "booking" to booking))
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    fun cancel(
        @PathVariable bookingId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val booking = merchantBookingService.cancel(currentUser.userId, bookingId)
        return ResponseEntity.ok(mapOf("success" to true, "booking" to booking))
    }

    @GetMapping("/bookings/{bookingId}/deposit")
    fun getDeposit(
        @PathVariable bookingId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "deposit" to merchantBookingService.getBookingDeposit(currentUser.userId, bookingId)))

    // Real convenience endpoint (same "verify a scheduled effect without waiting real
    // wall-clock time" precedent as WeeklySavingsController's own POST /process-due) --
    // processes every real due no-show network-wide, not scoped to the caller.
    @PostMapping("/bookings/process-no-shows")
    @PreAuthorize("hasRole('ADMIN')")
    fun processNoShows(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val processed = merchantBookingService.processNoShows()
        return ResponseEntity.ok(mapOf("success" to true, "processedCount" to processed.size, "bookings" to processed))
    }

    @ExceptionHandler(MerchantNotFoundException::class)
    fun handleMerchantNotFound(ex: MerchantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidAvailabilityWindowException::class)
    fun handleInvalidWindow(ex: InvalidAvailabilityWindowException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_AVAILABILITY_WINDOW", ex.message ?: "Bad request"))

    @ExceptionHandler(ServiceNotBookableException::class)
    fun handleNotBookable(ex: ServiceNotBookableException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("SERVICE_NOT_BOOKABLE", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidBookingSlotException::class)
    fun handleInvalidSlot(ex: InvalidBookingSlotException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_BOOKING_SLOT", ex.message ?: "Bad request"))

    @ExceptionHandler(SlotNoLongerAvailableException::class)
    fun handleSlotTaken(ex: SlotNoLongerAvailableException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("SLOT_NO_LONGER_AVAILABLE", ex.message ?: "Conflict"))

    @ExceptionHandler(MerchantProductNotFoundException::class)
    fun handleServiceNotFound(ex: MerchantProductNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("SERVICE_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(MerchantBookingNotFoundException::class)
    fun handleBookingNotFound(ex: MerchantBookingNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("BOOKING_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidBookingStatusTransitionException::class)
    fun handleInvalidTransition(ex: InvalidBookingStatusTransitionException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("INVALID_BOOKING_STATUS_TRANSITION", ex.message ?: "Conflict"))

    // Real Kakao Hair Shop-style prepay-to-book (2026-07-25) -- found live during this
    // feature's own verification: a customer without enough balance for a
    // requiresPrepay service's deposit got a raw 500, not a real handled error, since
    // this controller never registered a handler for the same InsufficientFundsException
    // MerchantController's own /collect endpoint already handles. The booking itself
    // still correctly rolled back (book() is @Transactional) -- only the HTTP response
    // was wrong.
    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(MerchantNoWalletException::class)
    fun handleNoWallet(ex: MerchantNoWalletException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))
}
