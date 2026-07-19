package rw.itunda.maps.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.maps.InvalidMapsCoordinateException
import rw.itunda.maps.MapsService
import rw.itunda.maps.RouteNotFoundException

// Real self-hosted "search this map" + "directions" -- see MapsService's own doc
// comment. Normal itunda-user JWT gate (default SecurityConfig .anyRequest().authenticated()).
@RestController
@RequestMapping("/api/v1/maps")
class MapsController(private val mapsService: MapsService) {

    @GetMapping("/search")
    fun search(@RequestParam q: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "results" to mapsService.searchPlaces(currentUser.userId, q)))

    @GetMapping("/directions")
    fun directions(
        @RequestParam fromLat: Double,
        @RequestParam fromLng: Double,
        @RequestParam toLat: Double,
        @RequestParam toLng: Double,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok(
        mapOf("success" to true, "route" to mapsService.getDirections(currentUser.userId, fromLat, fromLng, toLat, toLng)),
    )

    @ExceptionHandler(InvalidMapsCoordinateException::class)
    fun handleInvalidCoordinate(ex: InvalidMapsCoordinateException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_COORDINATES", ex.message ?: "Bad request"))

    @ExceptionHandler(RouteNotFoundException::class)
    fun handleRouteNotFound(ex: RouteNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ROUTE_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}
