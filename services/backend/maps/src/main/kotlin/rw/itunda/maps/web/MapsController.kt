package rw.itunda.maps.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.geo.TravelMode
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.maps.BookmarkNotFoundException
import rw.itunda.maps.InvalidBookmarkColorException
import rw.itunda.maps.InvalidBookmarkFolderException
import rw.itunda.maps.InvalidBookmarkNameException
import rw.itunda.maps.InvalidMapsCategoryException
import rw.itunda.maps.InvalidMapsCoordinateException
import rw.itunda.maps.InvalidMapsItineraryException
import rw.itunda.maps.InvalidLiveLocationCoordinateException
import rw.itunda.maps.InvalidLiveLocationShareDurationException
import rw.itunda.maps.LiveLocationShareEndedException
import rw.itunda.maps.LiveLocationShareNotFoundException
import rw.itunda.maps.LiveLocationShareRecipientNotFoundException
import rw.itunda.maps.LiveLocationShareSelfException
import rw.itunda.maps.LiveLocationShareService
import rw.itunda.maps.LiveLocationTooManyActiveSharesException
import rw.itunda.maps.MapPlaceCategory
import rw.itunda.maps.MapsService
import rw.itunda.maps.RouteNotFoundException

// Real self-hosted "search this map" + "directions" -- see MapsService's own doc
// comment. Normal itunda-user JWT gate (default SecurityConfig .anyRequest().authenticated()).
@RestController
@RequestMapping("/api/v1/maps")
class MapsController(
    private val mapsService: MapsService,
    private val liveLocationShareService: LiveLocationShareService,
    private val mapsPlaceDetailService: rw.itunda.maps.MapsPlaceDetailService,
    private val idempotencyService: IdempotencyService,
) {
    // Real consolidated place-detail endpoint -- see MapsPlaceDetailService's own doc
    // comment.
    @GetMapping("/places/{merchantId}")
    fun placeDetail(@PathVariable merchantId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "place" to mapsPlaceDetailService.getPlaceDetail(currentUser.userId, merchantId)))

    @GetMapping("/search")
    fun search(@RequestParam q: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "results" to mapsService.searchPlaces(currentUser.userId, q)))

    // Real Kigali weather chip -- see KigaliWeatherClient's own doc comment.
    @GetMapping("/weather")
    fun weather(): ResponseEntity<Map<String, Any?>> {
        val weather = mapsService.getWeather() ?: return ResponseEntity.ok(mapOf("success" to true, "weather" to null))
        return ResponseEntity.ok(mapOf("success" to true, "weather" to weather))
    }

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

    // Real Kigali GTFS-based transit journeys -- see MapsService.getTransitDirections'
    // own doc comment. A real empty list (never an error) means no real direct transit
    // option was found.
    @GetMapping("/directions/transit")
    fun transitDirections(
        @RequestParam fromLat: Double,
        @RequestParam fromLng: Double,
        @RequestParam toLat: Double,
        @RequestParam toLng: Double,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok(
        mapOf("success" to true, "journeys" to mapsService.getTransitDirections(currentUser.userId, fromLat, fromLng, toLat, toLng)),
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
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok(
        mapOf("success" to true, "places" to mapsService.getTrendingSavedPlaces(currentUser.userId, days, limit)),
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

    // Real Naver Map-style public/private folder + share -- see MapBookmark.isPublic's
    // own doc comment.
    @PatchMapping("/bookmarks/folder-visibility")
    fun setFolderVisibility(
        @RequestBody request: SetFolderVisibilityRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok(
        mapOf("success" to true, "updatedCount" to mapsService.setFolderPublic(currentUser.userId, request.folderName, request.isPublic)),
    )

    // Deliberately unauthenticated -- see MapsService.getPublicFolder's own doc comment.
    // Mapped under /api/v1/maps/shared specifically so SecurityConfig can permitAll it
    // without loosening the rest of /api/v1/maps/**.
    @GetMapping("/shared/{userId}/{folderName}")
    fun sharedFolder(@PathVariable userId: String, @PathVariable folderName: String): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "bookmarks" to mapsService.getPublicFolder(userId, folderName)))

    // Real Kakao Map-style "구독" (subscribe) -- see MapsService.subscribeToSharedFolder's
    // own doc comment. Authenticated (normal default gate): unlike the read-only GET
    // above, this writes real bookmark rows into the CALLER's own account, so it can't be
    // anonymous the way viewing a share link is.
    @PostMapping("/shared/{userId}/{folderName}/subscribe")
    fun subscribeToSharedFolder(
        @PathVariable userId: String,
        @PathVariable folderName: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok(
        mapOf("success" to true, "copiedCount" to mapsService.subscribeToSharedFolder(currentUser.userId, userId, folderName)),
    )

    @GetMapping("/bookmarks")
    fun bookmarks(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "bookmarks" to mapsService.getMyBookmarks(currentUser.userId)))

    // Real Kakao Map "친구위치" (Friend Location) live location sharing -- see
    // LiveLocationShareService's own doc comment for the full real sourcing.
    //
    // Idempotency-Key added (Maps product-completeness pass, 2026-09-07) -- unlike
    // addBookmark/subscribeToSharedFolder (already idempotent by DB-unique-constraint
    // or per-place dedup, see MapsService's own doc comments), this creates a brand-new
    // share row every call with no dedup key at all -- a lost response after a real
    // client-side retry used to create two real active shares with the same recipient.
    @PostMapping("/location-share")
    fun startLocationShare(
        @RequestBody request: StartLocationShareRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/maps/location-share", idempotencyKey, request) {
            val share = liveLocationShareService.startSharing(currentUser.userId, request.recipientPhoneNumber, request.durationHours)
            200 to mapOf("success" to true, "share" to share)
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/location-share/{id}/update-location")
    fun updateLocationShare(
        @PathVariable id: String,
        @RequestBody request: UpdateLocationShareRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok(
        // Real "one push updates every active share at once" -- id is accepted here
        // to keep the endpoint shape symmetric with the rest of this section (and
        // future-proof for a per-share update), but the service itself real-fans out
        // to every one of this sharer's active shares, not just this one id.
        mapOf("success" to true, "updatedShareCount" to liveLocationShareService.updateMyLocation(currentUser.userId, request.latitude, request.longitude)),
    )

    // Idempotency-Key added (Maps product-completeness pass, 2026-09-07) -- extendSharing
    // is additive (expiresAt.plus(additionalHours)), so a client retry after a lost
    // response used to silently double-extend the share instead of safely no-op'ing.
    @PostMapping("/location-share/{id}/extend")
    fun extendLocationShare(
        @PathVariable id: String,
        @RequestBody request: ExtendLocationShareRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/maps/location-share/$id/extend", idempotencyKey, request) {
            val share = liveLocationShareService.extendSharing(currentUser.userId, id, request.additionalHours)
            200 to mapOf("success" to true, "share" to share)
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/location-share/{id}/stop")
    fun stopLocationShare(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        liveLocationShareService.stopSharing(currentUser.userId, id)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @GetMapping("/location-share/mine")
    fun myLocationShares(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "shares" to liveLocationShareService.myActiveShares(currentUser.userId)))

    @GetMapping("/location-share/shared-with-me")
    fun locationSharesWithMe(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "shares" to liveLocationShareService.sharedWithMe(currentUser.userId)))

    // Real recipient-side poll -- see LiveLocationShareService's own doc comment on why
    // this is "periodically refreshed," not a persistent push channel: the recipient's
    // client calls this on its own schedule to see the sharer's latest pushed position.
    @GetMapping("/location-share/{id}")
    fun getLocationShare(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "share" to liveLocationShareService.getSharedLocation(currentUser.userId, id)))

    @ExceptionHandler(LiveLocationShareNotFoundException::class)
    fun handleLiveLocationShareNotFound(ex: LiveLocationShareNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("LOCATION_SHARE_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(LiveLocationShareSelfException::class)
    fun handleLiveLocationShareSelf(ex: LiveLocationShareSelfException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("SELF_LOCATION_SHARE_NOT_ALLOWED", ex.message ?: "Bad request"))

    @ExceptionHandler(LiveLocationShareRecipientNotFoundException::class)
    fun handleLiveLocationShareRecipientNotFound(ex: LiveLocationShareRecipientNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("LOCATION_SHARE_RECIPIENT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(LiveLocationTooManyActiveSharesException::class)
    fun handleLiveLocationTooManyActiveShares(ex: LiveLocationTooManyActiveSharesException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("TOO_MANY_ACTIVE_LOCATION_SHARES", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidLiveLocationShareDurationException::class)
    fun handleInvalidLiveLocationShareDuration(ex: InvalidLiveLocationShareDurationException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_LOCATION_SHARE_DURATION", ex.message ?: "Bad request"))

    @ExceptionHandler(LiveLocationShareEndedException::class)
    fun handleLiveLocationShareEnded(ex: LiveLocationShareEndedException) =
        ResponseEntity.status(HttpStatus.GONE).body(ApiError("LOCATION_SHARE_ENDED", ex.message ?: "Gone"))

    @ExceptionHandler(InvalidLiveLocationCoordinateException::class)
    fun handleInvalidLiveLocationCoordinate(ex: InvalidLiveLocationCoordinateException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_COORDINATES", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidMapsCoordinateException::class)
    fun handleInvalidCoordinate(ex: InvalidMapsCoordinateException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_COORDINATES", ex.message ?: "Bad request"))

    @ExceptionHandler(rw.itunda.merchant.MerchantNotFoundException::class)
    fun handleMerchantNotFound(ex: rw.itunda.merchant.MerchantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_NOT_FOUND", ex.message ?: "Not found"))

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

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleIdempotencyConflict(ex: IdempotencyConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleIdempotencyInProgress(ex: IdempotencyInProgressException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))

    // A `mode` value that isn't a real TravelMode name (added 2026-07-22) fails Spring's
    // own enum conversion before this controller's method body ever runs -- same
    // consistent ApiError shape as every other bad-input case here, not Spring's default
    // generic error body.
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException::class)
    fun handleInvalidMode(ex: org.springframework.web.method.annotation.MethodArgumentTypeMismatchException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_TRAVEL_MODE", "mode must be DRIVING, WALKING, or BIKING"))

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
data class SetFolderVisibilityRequest(val folderName: String, val isPublic: Boolean)

data class ItineraryDirectionsRequest(
    val waypoints: List<ItineraryWaypointRequest>,
    val mode: TravelMode = TravelMode.DRIVING,
)

data class ItineraryWaypointRequest(val latitude: Double, val longitude: Double)

data class StartLocationShareRequest(val recipientPhoneNumber: String, val durationHours: Int = 1)
data class UpdateLocationShareRequest(val latitude: Double, val longitude: Double)
data class ExtendLocationShareRequest(val additionalHours: Int = 1)
