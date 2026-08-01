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
import rw.itunda.core.domain.BikeType
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import rw.itunda.rideshare.BikeNoWalletException
import rw.itunda.rideshare.BikeNotAvailableException
import rw.itunda.rideshare.BikeNotFoundException
import rw.itunda.rideshare.BikeRentalAlreadyEndedException
import rw.itunda.rideshare.BikeRentalNotFoundException
import rw.itunda.rideshare.BikeRentalService
import rw.itunda.rideshare.BikeSelfRentalException
import rw.itunda.rideshare.InvalidBikeLocationException

data class RegisterBikeRequest(val type: BikeType, val latitude: Double, val longitude: Double)
data class SetBikeAvailabilityRequest(val available: Boolean)
data class UpdateBikeLocationRequest(val latitude: Double, val longitude: Double)
data class StartBikeRentalRequest(val bikeId: String, val startLatitude: Double, val startLongitude: Double)
data class EndBikeRentalRequest(val endLatitude: Double, val endLongitude: Double)

// Real Kakao T 바이크 (Kakao T Bike) -- see BikeRentalService's own doc comment for the
// full sourced account. Normal itunda-user JWT gate.
@RestController
@RequestMapping("/api/v1/bikeshare")
class BikeRentalController(private val bikeRentalService: BikeRentalService) {

    @PostMapping("/bikes")
    fun registerBike(@RequestBody request: RegisterBikeRequest, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val bike = bikeRentalService.registerBike(currentUser.userId, request.type, request.latitude, request.longitude)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "bike" to bike))
    }

    @GetMapping("/bikes/mine")
    fun getMyBikes(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "bikes" to bikeRentalService.getMyBikes(currentUser.userId)))

    @PostMapping("/bikes/{bikeId}/availability")
    fun setAvailability(
        @PathVariable bikeId: String,
        @RequestBody request: SetBikeAvailabilityRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "bike" to bikeRentalService.setAvailability(currentUser.userId, bikeId, request.available)))

    @PostMapping("/bikes/{bikeId}/location")
    fun updateLocation(
        @PathVariable bikeId: String,
        @RequestBody request: UpdateBikeLocationRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "bike" to bikeRentalService.updateLocation(currentUser.userId, bikeId, request.latitude, request.longitude)))

    @GetMapping("/bikes/nearby")
    fun getNearbyBikes(
        @RequestParam latitude: Double,
        @RequestParam longitude: Double,
        @RequestParam(defaultValue = "5.0") radiusKm: Double,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "bikes" to bikeRentalService.getNearbyBikes(latitude, longitude, radiusKm)))

    @PostMapping("/rentals")
    fun startRental(@RequestBody request: StartBikeRentalRequest, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val session = bikeRentalService.startRental(currentUser.userId, request.bikeId, request.startLatitude, request.startLongitude)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "rental" to session))
    }

    @PostMapping("/rentals/{sessionId}/end")
    fun endRental(
        @PathVariable sessionId: String,
        @RequestBody request: EndBikeRentalRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "rental" to bikeRentalService.endRental(currentUser.userId, sessionId, request.endLatitude, request.endLongitude)))

    @GetMapping("/rentals/my-history")
    fun getMyRentalHistory(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = bikeRentalService.getMyRentalHistory(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "rentals" to page.content) + pageMeta(page))
    }

    @ExceptionHandler(BikeNotFoundException::class)
    fun handleNotFound(ex: BikeNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("BIKE_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(BikeNotAvailableException::class)
    fun handleNotAvailable(ex: BikeNotAvailableException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("BIKE_NOT_AVAILABLE", ex.message ?: "Conflict"))

    @ExceptionHandler(BikeSelfRentalException::class)
    fun handleSelfRental(ex: BikeSelfRentalException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("SELF_RENTAL_NOT_ALLOWED", ex.message ?: "Bad request"))

    @ExceptionHandler(BikeNoWalletException::class)
    fun handleNoWallet(ex: BikeNoWalletException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidBikeLocationException::class)
    fun handleInvalidLocation(ex: InvalidBikeLocationException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_LOCATION", ex.message ?: "Bad request"))

    @ExceptionHandler(BikeRentalNotFoundException::class)
    fun handleRentalNotFound(ex: BikeRentalNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("BIKE_RENTAL_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(BikeRentalAlreadyEndedException::class)
    fun handleAlreadyEnded(ex: BikeRentalAlreadyEndedException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("BIKE_RENTAL_ALREADY_ENDED", ex.message ?: "Conflict"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}
