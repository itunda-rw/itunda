package rw.itunda.vehicle.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.vehicle.InvalidVehicleException
import rw.itunda.vehicle.VehicleNotFoundException
import rw.itunda.vehicle.VehicleValuationService
import java.math.BigDecimal
import java.time.LocalDate

data class RegisterVehicleRequest(
    val make: String, val model: String, val modelYear: Int, val purchasePrice: BigDecimal, val purchaseDate: LocalDate, val mileageKm: Int,
)
data class UpdateVehicleMileageRequest(val mileageKm: Int)

// Real Toss 내 차 시세 (my car's market value)-style vehicle value estimator -- see
// VehicleValuationService's own doc comment for the full sourced account and honest
// scope boundary.
@RestController
@RequestMapping("/api/v1/vehicles")
class VehicleController(
    private val vehicleValuationService: VehicleValuationService,
    private val idempotencyService: IdempotencyService,
) {

    // Idempotency-Key added 2026-09-07 (Vehicle product-completeness pass) -- same
    // bug class VehicleInspectionController.registerAsMechanic already got fixed
    // for (2026-09-05): a lost response after a successful registration previously
    // resubmitted here and created a real duplicate Vehicle row (no uniqueness
    // constraint exists). Wraps the existing request body, same shape
    // CardController.charge already establishes for a real-bodied idempotent POST.
    @PostMapping
    fun register(
        @RequestBody request: RegisterVehicleRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/vehicles", idempotencyKey, request) {
            val vehicle = vehicleValuationService.registerVehicle(
                currentUser.userId, request.make, request.model, request.modelYear, request.purchasePrice, request.purchaseDate, request.mileageKm,
            )
            HttpStatus.CREATED.value() to mapOf("success" to true, "vehicle" to vehicle)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping
    fun getMine(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "vehicles" to vehicleValuationService.getMyVehicles(currentUser.userId)))

    @GetMapping("/{id}/valuation")
    fun getValuation(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "valuation" to vehicleValuationService.getValuation(currentUser.userId, id)))

    @PostMapping("/{id}/mileage")
    fun updateMileage(
        @PathVariable id: String,
        @RequestBody request: UpdateVehicleMileageRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "vehicle" to vehicleValuationService.updateMileage(currentUser.userId, id, request.mileageKm)))

    @DeleteMapping("/{id}")
    fun remove(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        vehicleValuationService.removeVehicle(currentUser.userId, id)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @ExceptionHandler(VehicleNotFoundException::class)
    fun handleNotFound(ex: VehicleNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("VEHICLE_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidVehicleException::class)
    fun handleInvalid(ex: InvalidVehicleException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_VEHICLE", ex.message ?: "Bad request"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))
}
