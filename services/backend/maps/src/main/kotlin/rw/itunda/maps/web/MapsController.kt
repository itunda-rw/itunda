package rw.itunda.maps.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.geo.TravelMode
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.maps.BookmarkNotFoundException
import rw.itunda.maps.InvalidBookmarkColorException
import rw.itunda.maps.InvalidBookmarkFolderException
import rw.itunda.maps.InvalidBookmarkNameException
import rw.itunda.maps.InvalidMapsCategoryException
import rw.itunda.maps.InvalidMapsCoordinateException
import rw.itunda.maps.InvalidMapsItineraryException
import rw.itunda.maps.MapPlaceCategory
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

    @GetMapping("/reverse")
    fun reverse(
        @RequestParam lat: Double,
        @RequestParam lng: Double,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok(
        mapOf("success" to true, "placeName" to mapsService.reverseGeocode(currentUser.userId, lat, lng)),
    )

    // mode added 2026-07-22 (default DRIVING, matching the pre-existing behavior for
    // every caller that doesn't pass it) -- see MapsService.getDirections' own doc
    // comment. An unrecognized value real-400s via the enum-conversion failure handler
    // below rather than silently falling back to DRIVING.
    @GetMapping("/directions")
    fun directions(
        @RequestParam fromLat: Double,
        @RequestParam fromLng: Double,
        @RequestParam toLat: Double,
        @RequestParam toLng: Double,
        @RequestParam(required = false, defaultValue = "DRIVING") mode: TravelMode,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok(
        mapOf("success" to true, "route" to mapsService.getDirections(currentUser.userId, fromLat, fromLng, toLat, toLng, mode)),
    )

    // POST rather than encoding an arbitrary ordered array into query parameters. The
    // server accepts a deliberately bounded itinerary (2–7 stops) and returns the same
    // `route` shape as ordinary directions, so clients can reuse their route renderer.
    @PostMapping("/directions/itinerary")
    fun itineraryDirections(
        @RequestBody request: ItineraryDirectionsRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok(
        mapOf(
            "success" to true,
            "route" to mapsService.getItineraryDirections(
                currentUser.userId,
                request.waypoints.map { MapsService.ItineraryWaypoint(it.latitude, it.longitude) },
                request.mode,
            ),
        ),
    )

    // Real alternative-routes list (2026-07-22) -- see MapsService.getDirectionsAlternatives'
    // own doc comment. A separate endpoint rather than a `?alternatives=true` flag on
    // `/directions` above: that endpoint's real response shape is a single `route` object,
    // and every existing caller (web/mobile) already depends on that shape unchanged.
    @GetMapping("/directions/alternatives")
    fun directionsAlternatives(
        @RequestParam fromLat: Double,
        @RequestParam fromLng: Double,
        @RequestParam toLat: Double,
        @RequestParam toLng: Double,
        @RequestParam(required = false, defaultValue = "DRIVING") mode: TravelMode,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok(
        mapOf("success" to true, "routes" to mapsService.getDirectionsAlternatives(currentUser.userId, fromLat, fromLng, toLat, toLng, mode)),
    )

    @GetMapping("/categories")
    fun categories(): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok(
        mapOf(
            "success" to true,
            "categories" to MapPlaceCategory.entries.map { mapOf("id" to it.name, "label" to it.label) },
        ),
    )

    @GetMapping("/nearby")
    fun nearby(
        @RequestParam category: String,
        @RequestParam lat: Double,
        @RequestParam lng: Double,
        @RequestParam(defaultValue = "2.0") radiusKm: Double,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok(
        mapOf("success" to true, "places" to mapsService.getNearbyPlaces(currentUser.userId, category, lat, lng, radiusKm)),
    )

    // Real "Smart Around"-style default state -- see MapsService.getAroundMe/
    // getTrendingSavedPlaces's own doc comments for the real, re-verified Naver Map
    // sourcing and the honest scope decision (2 of Naver's 5 real sections, not a
    // fabricated 5-for-5).
    @GetMapping("/around-me")
    fun aroundMe(
        @RequestParam lat: Double,
        @RequestParam lng: Double,
        @RequestParam(defaultValue = "2.0") radiusKm: Double,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok(
        mapOf("success" to true, "places" to mapsService.getAroundMe(currentUser.userId, lat, lng, radiusKm)),
    )

    @GetMapping("/trending")
    fun trending(
        @RequestParam(defaultValue = "7") days: Int,
        @RequestParam(defaultValue = "10") limit: Int,
    ): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok(
        mapOf("success" to true, "places" to mapsService.getTrendingSavedPlaces(days, limit)),
    )

    @PostMapping("/bookmarks")
    fun addBookmark(
        @RequestBody request: AddBookmarkRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok(
        mapOf(
            "success" to true,
            "bookmark" to mapsService.addBookmark(
                currentUser.userId,
                request.displayName,
                request.latitude,
                request.longitude,
                request.folderName ?: MapsService.DEFAULT_BOOKMARK_FOLDER,
                request.color ?: MapsService.DEFAULT_BOOKMARK_COLOR,
            ),
        ),
    )

    // Real "move to folder" (2026-07-22) -- see MapsService.moveBookmark's own doc
    // comment. Keyed by (lat, lng) via query params, same real key `/bookmarks` DELETE
    // already uses, not a path id this API has never exposed.
    @PatchMapping("/bookmarks")
    fun moveBookmark(
        @RequestParam lat: Double,
        @RequestParam lng: Double,
        @RequestBody request: MoveBookmarkRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok(
        mapOf("success" to true, "bookmark" to mapsService.moveBookmark(currentUser.userId, lat, lng, request.folderName, request.color)),
    )

    @DeleteMapping("/bookmarks")
    fun removeBookmark(
        @RequestParam lat: Double,
        @RequestParam lng: Double,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        mapsService.removeBookmark(currentUser.userId, lat, lng)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @GetMapping("/bookmarks")
    fun bookmarks(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "bookmarks" to mapsService.getMyBookmarks(currentUser.userId)))

    @ExceptionHandler(InvalidMapsCoordinateException::class)
    fun handleInvalidCoordinate(ex: InvalidMapsCoordinateException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_COORDINATES", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidMapsItineraryException::class)
    fun handleInvalidItinerary(ex: InvalidMapsItineraryException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_ITINERARY", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidMapsCategoryException::class)
    fun handleInvalidCategory(ex: InvalidMapsCategoryException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_CATEGORY", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidBookmarkNameException::class)
    fun handleInvalidBookmarkName(ex: InvalidBookmarkNameException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_BOOKMARK_NAME", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidBookmarkFolderException::class)
    fun handleInvalidBookmarkFolder(ex: InvalidBookmarkFolderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_BOOKMARK_FOLDER", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidBookmarkColorException::class)
    fun handleInvalidBookmarkColor(ex: InvalidBookmarkColorException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_BOOKMARK_COLOR", ex.message ?: "Bad request"))

    @ExceptionHandler(BookmarkNotFoundException::class)
    fun handleBookmarkNotFound(ex: BookmarkNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("BOOKMARK_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(RouteNotFoundException::class)
    fun handleRouteNotFound(ex: RouteNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ROUTE_NOT_FOUND", ex.message ?: "Not found"))

    // A `mode` value that isn't a real TravelMode name (added 2026-07-22) fails Spring's
    // own enum conversion before this controller's method body ever runs -- same
    // consistent ApiError shape as every other bad-input case here, not Spring's default
    // generic error body.
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException::class)
    fun handleInvalidMode(ex: org.springframework.web.method.annotation.MethodArgumentTypeMismatchException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_TRAVEL_MODE", "mode must be DRIVING or WALKING"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}

data class AddBookmarkRequest(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val folderName: String? = null,
    val color: String? = null,
)

data class MoveBookmarkRequest(val folderName: String, val color: String)

data class ItineraryDirectionsRequest(
    val waypoints: List<ItineraryWaypointRequest>,
    val mode: TravelMode = TravelMode.DRIVING,
)

data class ItineraryWaypointRequest(val latitude: Double, val longitude: Double)
