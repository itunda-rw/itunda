package rw.itunda.marketplace.web

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
import rw.itunda.core.ledger.AccountFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.marketplace.BuyerNoAccountException
import rw.itunda.marketplace.InspectionBookingNotFoundException
import rw.itunda.marketplace.InvalidInspectionFeeException
import rw.itunda.marketplace.InvalidInspectionStatusTransitionException
import rw.itunda.marketplace.ListingNotFoundException
import rw.itunda.marketplace.MechanicAlreadyRegisteredException
import rw.itunda.marketplace.MechanicNoAccountException
import rw.itunda.marketplace.MechanicNotRegisteredException
import rw.itunda.marketplace.MechanicSuspendedException
import rw.itunda.marketplace.SelfInspectionException
import rw.itunda.marketplace.VehicleInspectionService
import java.math.BigDecimal
import java.time.Instant

data class RegisterMechanicRequest(val businessName: String)
data class SetMechanicAvailabilityRequest(val available: Boolean)
data class RequestInspectionRequest(val listingId: String, val mechanicId: String, val fee: BigDecimal, val scheduledFor: Instant)
data class CompleteInspectionRequest(val findings: String? = null)

// Real 당근마켓 중고차 정비소 동행 (used-car mechanic-inspection accompaniment) -- see
// VehicleInspectionService's own doc comment. Normal itunda-user JWT gate.
@RestController
@RequestMapping("/api/v1/marketplace/inspections")
class VehicleInspectionController(
    private val vehicleInspectionService: VehicleInspectionService,
    private val idempotencyService: IdempotencyService,
) {
    // Real gap found 2026-09-05, same class as StudentLoanController.apply's
    // identical fix (see feedback_idempotency_key_sweep memory) -- a lost response
    // after a successful register would resubmit here and hit
    // MechanicAlreadyRegisteredException on the retry. Booking creation below was
    // already protected; this register endpoint was the outlier.
    @PostMapping("/mechanics/register")
    fun registerAsMechanic(
        @RequestBody request: RegisterMechanicRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/marketplace/inspections/mechanics/register", idempotencyKey, request) {
            val mechanic = vehicleInspectionService.registerAsMechanic(currentUser.userId, request.businessName)
            HttpStatus.CREATED.value() to mapOf("success" to true, "mechanic" to mechanic)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/mechanics/me")
    fun getMyMechanicProfile(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "mechanic" to vehicleInspectionService.getMyMechanicProfile(currentUser.userId)))

    @GetMapping("/mechanics")
    fun getAvailableMechanics(): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "mechanics" to vehicleInspectionService.getAvailableMechanics()))

    @PostMapping("/mechanics/availability")
    fun setAvailability(
        @RequestBody request: SetMechanicAvailabilityRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "mechanic" to vehicleInspectionService.setAvailability(currentUser.userId, request.available)))

    @PostMapping
    fun requestInspection(
        @RequestBody request: RequestInspectionRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/marketplace/inspections", idempotencyKey, request) {
            val booking = vehicleInspectionService.requestInspection(
                currentUser.userId, request.listingId, request.mechanicId, request.fee, request.scheduledFor,
            )
            201 to mapOf("success" to true, "booking" to booking)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/my-bookings")
    fun getMyBookings(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "bookings" to vehicleInspectionService.getMyBookings(currentUser.userId)))

    @GetMapping("/my-mechanic-bookings")
    fun getMyMechanicBookings(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "bookings" to vehicleInspectionService.getMyMechanicBookings(currentUser.userId)))

    @PostMapping("/{bookingId}/accept")
    fun acceptInspection(@PathVariable bookingId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "booking" to vehicleInspectionService.acceptInspection(currentUser.userId, bookingId)))

    @PostMapping("/{bookingId}/complete")
    fun completeInspection(
        @PathVariable bookingId: String,
        @RequestBody request: CompleteInspectionRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "booking" to vehicleInspectionService.completeInspection(currentUser.userId, bookingId, request.findings)))

    @PostMapping("/{bookingId}/cancel")
    fun cancelInspection(@PathVariable bookingId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "booking" to vehicleInspectionService.cancelInspection(currentUser.userId, bookingId)))

    @ExceptionHandler(MechanicAlreadyRegisteredException::class)
    fun handleMechanicAlreadyRegistered(ex: MechanicAlreadyRegisteredException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("MECHANIC_ALREADY_REGISTERED", ex.message ?: "Conflict"))

    @ExceptionHandler(MechanicNotRegisteredException::class)
    fun handleMechanicNotRegistered(ex: MechanicNotRegisteredException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MECHANIC_NOT_REGISTERED", ex.message ?: "Not found"))

    @ExceptionHandler(MechanicNoAccountException::class)
    fun handleMechanicNoAccount(ex: MechanicNoAccountException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(BuyerNoAccountException::class)
    fun handleBuyerNoAccount(ex: BuyerNoAccountException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidInspectionFeeException::class)
    fun handleInvalidFee(ex: InvalidInspectionFeeException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(ListingNotFoundException::class)
    fun handleListingNotFound(ex: ListingNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("LISTING_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(SelfInspectionException::class)
    fun handleSelfInspection(ex: SelfInspectionException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("SELF_INSPECTION_NOT_ALLOWED", ex.message ?: "Bad request"))

    @ExceptionHandler(MechanicSuspendedException::class)
    fun handleMechanicSuspended(ex: MechanicSuspendedException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("MECHANIC_SUSPENDED", ex.message ?: "Forbidden"))

    @ExceptionHandler(InspectionBookingNotFoundException::class)
    fun handleBookingNotFound(ex: InspectionBookingNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("INSPECTION_BOOKING_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidInspectionStatusTransitionException::class)
    fun handleInvalidTransition(ex: InvalidInspectionStatusTransitionException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("INVALID_INSPECTION_STATUS_TRANSITION", ex.message ?: "Conflict"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(AccountFrozenException::class)
    fun handleAccountFrozen(ex: AccountFrozenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("ACCOUNT_FROZEN", ex.message ?: "Account is frozen"))

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
