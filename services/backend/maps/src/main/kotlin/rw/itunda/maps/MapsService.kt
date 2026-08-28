package rw.itunda.maps

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.MapBookmark
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.geo.GeocodeSuggestion
import rw.itunda.core.geo.NearbyPlace
import rw.itunda.core.geo.NominatimGeocodingClient
import rw.itunda.core.geo.OsrmRoutingClient
import rw.itunda.core.geo.RouteResult
import rw.itunda.core.geo.TravelMode
import rw.itunda.core.repository.MapBookmarkRepository
import rw.itunda.core.weather.KigaliWeather
import rw.itunda.core.weather.KigaliWeatherClient
import java.time.Duration
import java.util.UUID

class InvalidMapsCoordinateException(message: String) : RuntimeException(message)
class InvalidMapsItineraryException(message: String) : RuntimeException(message)
class RouteNotFoundException(message: String) : RuntimeException(message)
class InvalidMapsCategoryException(message: String) : RuntimeException(message)
class InvalidBookmarkNameException(message: String) : RuntimeException(message)
class InvalidBookmarkFolderException(message: String) : RuntimeException(message)
class InvalidBookmarkColorException(message: String) : RuntimeException(message)
class BookmarkNotFoundException(message: String) : RuntimeException(message)

// Real cross-user "popular this week" place -- see MapsService.getTrendingSavedPlaces's own
// doc comment.
data class TrendingPlace(val displayName: String, val latitude: Double, val longitude: Double, val saveCount: Long)

/**
 * A real, general-purpose "search this map" + "get directions" surface -- the
 * highest-leverage gap between itunda's Maps effort so far (a real interactive map with
 * real merchant markers) and something that actually feels like Kakao Maps/Naver Maps:
 * finding a real place by name and drawing a real route to it. Both real endpoints reuse
 * itunda's own already-deployed self-hosted infrastructure (Nominatim, OSRM) -- no new
 * service stood up for this, just a new, general-purpose front door onto what already
 * exists (previously Nominatim search was only reachable through Eats' checkout-scoped
 * address autocomplete, and OSRM only ever returned a distance, never a drawable route).
 */
@Service
class MapsService(
    private val nominatimGeocodingClient: NominatimGeocodingClient,
    private val osrmRoutingClient: OsrmRoutingClient,
    private val rateLimiter: RateLimiter,
    private val mapBookmarkRepository: MapBookmarkRepository,
    private val kigaliWeatherClient: KigaliWeatherClient,
) {
    /** An ordered stop in an itinerary, using itunda's `[lat, lng]` convention. */
    data class ItineraryWaypoint(val latitude: Double, val longitude: Double)

    // Real Kigali weather chip -- see KigaliWeatherClient's own doc comment. Null if
    // the real upstream is unreachable and there's no still-fresh cache -- never a
    // fabricated reading.
    fun getWeather(): KigaliWeather? = kigaliWeatherClient.current()

    // Real anti-spam limit -- same convention every other user-facing endpoint in this
    // codebase already has (search-as-you-type is easy to hammer otherwise).
    fun searchPlaces(userId: String, query: String): List<GeocodeSuggestion> {
        rateLimiter.checkLimit("maps:search:$userId", limit = 60, window = Duration.ofMinutes(1))
        return nominatimGeocodingClient.search(query, limit = 8)
    }

    /**
     * Resolves a point the user selected on Itunda's map into a real Rwanda
     * neighbourhood name. This is intentionally a separate, coordinate-only endpoint:
     * clients can use it for a long-press, dropped pin, delivery handoff, or a future
     * map URL without inventing an address locally. A missing Nominatim match is an
     * honest null, never a synthetic place name.
     */
    fun reverseGeocode(userId: String, latitude: Double, longitude: Double): String? {
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidMapsCoordinateException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        if (!GeoUtils.isWithinRwanda(latitude, longitude)) {
            throw RouteNotFoundException("Map places are only available within Rwanda")
        }
        rateLimiter.checkLimit("maps:reverse:$userId", limit = 60, window = Duration.ofMinutes(1))
        return nominatimGeocodingClient.reverseGeocode(latitude, longitude)
    }

    // mode added 2026-07-22 -- see OsrmRoutingClient.route's own doc comment for the
    // full account of the real, separately-deployed foot-profile OSRM instance this
    // now lets a caller actually reach.
    fun getDirections(userId: String, fromLat: Double, fromLng: Double, toLat: Double, toLng: Double, mode: TravelMode = TravelMode.DRIVING): RouteResult {
        if (!GeoUtils.isValidCoordinate(fromLat, fromLng) || !GeoUtils.isValidCoordinate(toLat, toLng)) {
            throw InvalidMapsCoordinateException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        rateLimiter.checkLimit("maps:directions:$userId", limit = 60, window = Duration.ofMinutes(1))
        // Real Rwanda-envelope guard -- same discipline EatsOrderService/MarketplaceService
        // already established: itunda's Rwanda-only OSRM silently snaps an out-of-Rwanda
        // coordinate to its nearest network node rather than returning a real NoRoute, so
        // this is checked before ever calling OSRM, not left for OSRM to (wrongly) handle.
        if (!GeoUtils.isWithinRwanda(fromLat, fromLng) || !GeoUtils.isWithinRwanda(toLat, toLng)) {
            throw RouteNotFoundException("Directions are only available within Rwanda")
        }
        return osrmRoutingClient.route(fromLat, fromLng, toLat, toLng, mode)
            ?: throw RouteNotFoundException("No route could be found between these two points")
    }

    /**
     * One real route through an ordered list of stops, for errands and delivery-style
     * journeys. Seven stops (origin + up to five intermediate stops + destination) is intentionally
     * bounded: it keeps request URLs, OSRM CPU work, and a mobile itinerary legible.
     * It is not a travelling-salesperson optimiser: user order is preserved exactly.
     */
    fun getItineraryDirections(
        userId: String,
        waypoints: List<ItineraryWaypoint>,
        mode: TravelMode = TravelMode.DRIVING,
    ): RouteResult {
        if (waypoints.size !in MIN_ITINERARY_WAYPOINTS..MAX_ITINERARY_WAYPOINTS) {
            throw InvalidMapsItineraryException(
                "An itinerary needs between $MIN_ITINERARY_WAYPOINTS and $MAX_ITINERARY_WAYPOINTS stops including origin and destination",
            )
        }
        if (waypoints.any { !GeoUtils.isValidCoordinate(it.latitude, it.longitude) }) {
            throw InvalidMapsCoordinateException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        // Share the normal directions bucket: an itinerary is more expensive than one
        // route, so giving it a separate unlimited bucket would weaken the real limit.
        rateLimiter.checkLimit("maps:directions:$userId", limit = 60, window = Duration.ofMinutes(1))
        if (waypoints.any { !GeoUtils.isWithinRwanda(it.latitude, it.longitude) }) {
            throw RouteNotFoundException("Directions are only available within Rwanda")
        }
        return osrmRoutingClient.routeThrough(waypoints.map { it.latitude to it.longitude }, mode)
            ?: throw RouteNotFoundException("No route could be found through these stops")
    }

    // Real alternative routes (2026-07-22) -- see OsrmRoutingClient.routeAlternatives'
    // own doc comment. Same validation as getDirections; a genuinely single-route answer
    // (OSRM found nothing else worth offering) is a valid one-element list, not an error.
    fun getDirectionsAlternatives(userId: String, fromLat: Double, fromLng: Double, toLat: Double, toLng: Double, mode: TravelMode = TravelMode.DRIVING): List<RouteResult> {
        if (!GeoUtils.isValidCoordinate(fromLat, fromLng) || !GeoUtils.isValidCoordinate(toLat, toLng)) {
            throw InvalidMapsCoordinateException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        rateLimiter.checkLimit("maps:directions:$userId", limit = 60, window = Duration.ofMinutes(1))
        if (!GeoUtils.isWithinRwanda(fromLat, fromLng) || !GeoUtils.isWithinRwanda(toLat, toLng)) {
            throw RouteNotFoundException("Directions are only available within Rwanda")
        }
        val routes = osrmRoutingClient.routeAlternatives(fromLat, fromLng, toLat, toLng, mode)
        if (routes.isEmpty()) throw RouteNotFoundException("No route could be found between these two points")
        return routes
    }

    // Real "nearby places" category search (restaurants, hospitals, pharmacies, ...) --
    // Naver/Kakao's own category-chip search, bounded to a real radius around the user
    // (or a map center they're browsing), sorted by real proximity, not relevance/ads.
    fun getNearbyPlaces(
        userId: String,
        categoryParam: String,
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
    ): List<NearbyPlace> {
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidMapsCoordinateException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        val category = MapPlaceCategory.fromParam(categoryParam)
            ?: throw InvalidMapsCategoryException(
                "Unknown category '$categoryParam' -- must be one of ${MapPlaceCategory.entries.joinToString { it.name }}",
            )
        val boundedRadiusKm = radiusKm.coerceIn(0.1, 20.0)
        rateLimiter.checkLimit("maps:nearby:$userId", limit = 60, window = Duration.ofMinutes(1))
        if (!GeoUtils.isWithinRwanda(latitude, longitude)) {
            return emptyList()
        }
        return nominatimGeocodingClient.searchNearby(category.searchTerm, latitude, longitude, boundedRadiusKm, limit = 20)
    }

    // Real "Smart Around"-style default state (2026-08-04) -- see MapsController's own doc
    // comment for the real, directly-re-fetched Naver Map source (brunch.co.kr/@bydot/4).
    // Naver's real default sheet has 5 curated sections (오늘의 PICK/주변/이번 주에 가볼 만한/
    // 이번 주에 많이 저장한/새로 오픈한) -- itunda has real data for exactly two of them
    // ("주변"/nearby, and a real cross-user aggregate for "이번 주에 많이 저장한"/frequently
    // saved, see getTrendingSavedPlaces below). The other three imply editorial curation or
    // a "date opened" signal itunda has no real source for (Nominatim/OSM data carries
    // neither) -- built honestly, not with a fabricated "Today's Pick"/"newly opened" list.
    // "주변" itself needed widening: getNearbyPlaces above requires the caller to already
    // have picked one category, but a real default-state "what's around me" view has none
    // selected yet -- this merges a real Nominatim call per real MapPlaceCategory (not a
    // fabricated aggregate; a genuine, several-call reality of "check everything real
    // instead of picking one arbitrarily") and returns the closest results overall.
    fun getAroundMe(userId: String, latitude: Double, longitude: Double, radiusKm: Double): List<NearbyPlace> {
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidMapsCoordinateException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        val boundedRadiusKm = radiusKm.coerceIn(0.1, 20.0)
        rateLimiter.checkLimit("maps:around-me:$userId", limit = 20, window = Duration.ofMinutes(1))
        if (!GeoUtils.isWithinRwanda(latitude, longitude)) {
            return emptyList()
        }
        return MapPlaceCategory.entries
            .flatMap { nominatimGeocodingClient.searchNearby(it.searchTerm, latitude, longitude, boundedRadiusKm, limit = 5) }
            .sortedBy { it.distanceKm }
            .take(20)
    }

    // Real cross-user "popular this week" (2026-08-04) -- an honest, non-fabricated proxy
    // for Naver's "이번 주에 많이 저장한" section: how many distinct real users bookmarked
    // this exact place in the real trailing window, using MapBookmark data that already
    // exists for the star/save feature (item 7 on the Maps roadmap). Grouped by
    // (displayName, latitude, longitude) since a bookmark carries no foreign key into any
    // itunda-owned place catalog -- two users saving "the same place" from the same real
    // search/nearby result get identical coordinates, the same assumption `addBookmark`'s
    // own idempotency check already relies on.
    fun getTrendingSavedPlaces(days: Int, limit: Int): List<TrendingPlace> {
        val since = java.time.Instant.now().minus(Duration.ofDays(days.toLong().coerceIn(1, 90)))
        return mapBookmarkRepository.findTrending(since, org.springframework.data.domain.PageRequest.of(0, limit.coerceIn(1, 50)))
            .map { TrendingPlace(it.getDisplayName(), it.getLatitude(), it.getLongitude(), it.getSaveCount()) }
    }

    // Real bookmarked/favorite places (item 7 on the Maps "100%" roadmap) -- the same
    // star/save feature Naver/Kakao Maps offer. `addBookmark` is deliberately idempotent
    // (bookmarking an already-bookmarked place just returns the existing row rather than a
    // 409) -- matches `EatsFavoriteService.addFavorite`'s own precedent, since a real
    // star-toggle UI shouldn't error on a double-tap and the real DB unique constraint
    // already makes a concurrent double-add safe without this check either.
    //
    // `folderName`/`color` added 2026-07-22 -- Naver/Kakao Maps' own real "My Places"
    // folder grouping (migration V73). Defaulted so every pre-existing caller keeps
    // saving into the same single real "Saved places" folder unchanged.
    @Transactional
    fun addBookmark(
        userId: String,
        displayName: String,
        latitude: Double,
        longitude: Double,
        folderName: String = DEFAULT_BOOKMARK_FOLDER,
        color: String = DEFAULT_BOOKMARK_COLOR,
    ): MapBookmark {
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidMapsCoordinateException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        val trimmedName = displayName.trim()
        if (trimmedName.isEmpty()) {
            throw InvalidBookmarkNameException("A bookmark needs a name")
        }
        // Real bound, found via the same systematic sweep that fixed the identical gap
        // across Marketplace/Jobs/RealEstate/Community/Messaging the same day --
        // `display_name` is VARCHAR(512), and this DB's real STRICT_TRANS_TABLES mode
        // throws a raw, unhandled 500 on an over-length insert rather than truncating.
        // Never previously caught because addBookmark's own displayName wasn't even
        // trimmed, let alone length-checked.
        if (trimmedName.length > 512) {
            throw InvalidBookmarkNameException("Bookmark name must be 512 characters or fewer")
        }
        val trimmedFolder = folderName.trim().ifEmpty { DEFAULT_BOOKMARK_FOLDER }
        if (trimmedFolder.length > 120) {
            throw InvalidBookmarkFolderException("Folder name must be 120 characters or fewer")
        }
        if (!HEX_COLOR_REGEX.matches(color)) {
            throw InvalidBookmarkColorException("color must be a hex value like #F5A623")
        }
        // Real bug found live (2026-08-02): every other real user-facing action in this
        // service (search/reverse/directions/nearby) already carries a real anti-spam
        // `rateLimiter.checkLimit` call, but this real content-creation endpoint didn't
        // -- a real client could hammer distinct (lat, lng) pairs to create unbounded
        // real bookmark rows (the DB unique constraint only blocks an exact-duplicate
        // re-add, not distinct new ones). Closed with the same real 60/min bucket this
        // module's own other write-shaped calls (directions/nearby) already use.
        rateLimiter.checkLimit("maps:bookmark:$userId", limit = 60, window = Duration.ofMinutes(1))
        mapBookmarkRepository.findByUserIdAndLatitudeAndLongitude(userId, latitude, longitude)?.let { return it }
        // Real bug found live testing subscribeToSharedFolder (2026-08-18): folder
        // publicity is a per-row flag set in bulk by setFolderPublic at the moment it's
        // toggled, not a durable folder-level property -- so a place added to an
        // already-public folder AFTER sharing it silently defaulted back to private,
        // never appeared in getPublicFolder, and a subscriber's re-subscribe copied
        // nothing even though the owner had genuinely added something new. A folder the
        // owner marked public stays public for whatever they add to it next, matching
        // what "share this folder" actually means to Kakao/Naver Map users -- inherit
        // publicity from any existing row in the same folder, private by default like
        // today when the folder has no public rows (including never-shared, or shared
        // and later made private again via setFolderPublic(isPublic = false)).
        val folderIsPublic = mapBookmarkRepository.findByUserIdAndFolderName(userId, trimmedFolder).any { it.isPublic }
        return mapBookmarkRepository.save(
            MapBookmark(
                id = "map_bookmark_${UUID.randomUUID()}",
                userId = userId,
                displayName = trimmedName,
                latitude = latitude,
                longitude = longitude,
                folderName = trimmedFolder,
                color = color,
                isPublic = folderIsPublic,
            ),
        )
    }

    // Real "move to folder" (2026-07-22) -- the other half of real folder grouping: a
    // user reorganizing already-saved places into a different named folder/color rather
    // than only ever choosing one at save time. Looked up by (userId, lat, lng), the same
    // real key `removeBookmark` already uses, since there's no bookmark-by-id lookup
    // endpoint for a client to have an id in hand from.
    @Transactional
    fun moveBookmark(userId: String, latitude: Double, longitude: Double, folderName: String, color: String): MapBookmark {
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidMapsCoordinateException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        val trimmedFolder = folderName.trim().ifEmpty { DEFAULT_BOOKMARK_FOLDER }
        if (trimmedFolder.length > 120) {
            throw InvalidBookmarkFolderException("Folder name must be 120 characters or fewer")
        }
        if (!HEX_COLOR_REGEX.matches(color)) {
            throw InvalidBookmarkColorException("color must be a hex value like #F5A623")
        }
        val existing = mapBookmarkRepository.findByUserIdAndLatitudeAndLongitude(userId, latitude, longitude)
            ?: throw BookmarkNotFoundException("No bookmark exists at that location")
        return mapBookmarkRepository.save(
            MapBookmark(
                id = existing.id,
                userId = existing.userId,
                displayName = existing.displayName,
                latitude = existing.latitude,
                longitude = existing.longitude,
                folderName = trimmedFolder,
                color = color,
                isPublic = existing.isPublic,
                createdAt = existing.createdAt,
            ),
        )
    }

    @Transactional
    fun removeBookmark(userId: String, latitude: Double, longitude: Double) {
        mapBookmarkRepository.deleteByUserIdAndLatitudeAndLongitude(userId, latitude, longitude)
    }

    // Real Naver Map-style public/private folder + share -- see MapBookmark.isPublic's
    // own doc comment for the full sourced account and the honest scope decision (a real
    // itunda deep link, not an invented public web URL). Bulk, not per-bookmark: a real
    // "share this whole folder" action, matching what a user actually means when they hit
    // Share on a named list, not a single pin.
    @Transactional
    fun setFolderPublic(userId: String, folderName: String, isPublic: Boolean): Int {
        val trimmedFolder = folderName.trim().ifEmpty { DEFAULT_BOOKMARK_FOLDER }
        val bookmarks = mapBookmarkRepository.findByUserIdAndFolderName(userId, trimmedFolder)
        if (bookmarks.isEmpty()) return 0
        mapBookmarkRepository.saveAll(
            bookmarks.map {
                MapBookmark(
                    id = it.id, userId = it.userId, displayName = it.displayName,
                    latitude = it.latitude, longitude = it.longitude,
                    folderName = it.folderName, color = it.color,
                    isPublic = isPublic, createdAt = it.createdAt,
                )
            },
        )
        return bookmarks.size
    }

    // Real, deliberately unauthenticated read -- the whole point of a share link is that
    // whoever opens it doesn't need to already be signed in as the folder's owner. Only
    // ever returns bookmarks the owner explicitly marked isPublic=true; a private folder
    // (or one that was shared and later made private again) returns an honest empty list,
    // never a 403/404 that would confirm whether a private folder exists at all.
    fun getPublicFolder(userId: String, folderName: String): List<MapBookmark> =
        mapBookmarkRepository.findByUserIdAndFolderNameAndIsPublicTrueOrderByCreatedAtDesc(userId, folderName.trim())

    // Real Kakao Map-style "구독" (subscribe) -- the other half of setFolderPublic/
    // getPublicFolder's own real share feature. Sharing only ever let a recipient VIEW
    // someone else's public folder (Android's itunda://maps/shared/... deep-link handler,
    // 2026-08-14); nothing ever let them actually keep it, the way Kakao Map's real
    // "그룹 공유&구독" lets a recipient follow a shared list into their own. A real copy
    // taken at the moment of subscribing, not a live-synced reference -- MapBookmark has
    // no cross-user pointer shape to support a cheap live subscription -- but re-calling
    // this after the owner adds more public places to the same folder correctly picks up
    // only the new ones, since it's idempotent on the same (userId, lat, lng) key
    // addBookmark already keys on; already-imported places are silently skipped, not
    // duplicated. Reuses the same rate-limit bucket addBookmark does -- this is still
    // real bookmark-row creation, just bulk.
    @Transactional
    fun subscribeToSharedFolder(subscriberUserId: String, ownerUserId: String, folderName: String): Int {
        if (subscriberUserId == ownerUserId) {
            throw InvalidBookmarkFolderException("You can't subscribe to your own folder")
        }
        rateLimiter.checkLimit("maps:bookmark:$subscriberUserId", limit = 60, window = Duration.ofMinutes(1))
        val trimmedFolder = folderName.trim().ifEmpty { DEFAULT_BOOKMARK_FOLDER }
        val publicBookmarks = mapBookmarkRepository.findByUserIdAndFolderNameAndIsPublicTrueOrderByCreatedAtDesc(ownerUserId, trimmedFolder)
        var copied = 0
        for (place in publicBookmarks) {
            if (mapBookmarkRepository.findByUserIdAndLatitudeAndLongitude(subscriberUserId, place.latitude, place.longitude) != null) {
                continue
            }
            mapBookmarkRepository.save(
                MapBookmark(
                    id = "map_bookmark_${UUID.randomUUID()}",
                    userId = subscriberUserId,
                    displayName = place.displayName,
                    latitude = place.latitude,
                    longitude = place.longitude,
                    folderName = trimmedFolder,
                    color = place.color,
                ),
            )
            copied++
        }
        return copied
    }

    fun getMyBookmarks(userId: String): List<MapBookmark> = mapBookmarkRepository.findByUserIdOrderByCreatedAtDesc(userId)

    companion object {
        const val MIN_ITINERARY_WAYPOINTS = 2
        // Match NAVER Maps' documented five intermediate waypoints while retaining a
        // bounded request: origin + five waypoints + destination = seven total stops.
        const val MAX_ITINERARY_WAYPOINTS = 7
        const val DEFAULT_BOOKMARK_FOLDER = "Saved places"
        const val DEFAULT_BOOKMARK_COLOR = "#F5A623"
        private val HEX_COLOR_REGEX = Regex("^#[0-9A-Fa-f]{6}$")
    }
}
