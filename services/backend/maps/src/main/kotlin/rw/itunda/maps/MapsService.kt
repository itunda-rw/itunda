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
import java.time.Duration
import java.util.UUID

class InvalidMapsCoordinateException(message: String) : RuntimeException(message)
class RouteNotFoundException(message: String) : RuntimeException(message)
class InvalidMapsCategoryException(message: String) : RuntimeException(message)
class InvalidBookmarkNameException(message: String) : RuntimeException(message)
class InvalidBookmarkFolderException(message: String) : RuntimeException(message)
class InvalidBookmarkColorException(message: String) : RuntimeException(message)
class BookmarkNotFoundException(message: String) : RuntimeException(message)

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
) {
    // Real anti-spam limit -- same convention every other user-facing endpoint in this
    // codebase already has (search-as-you-type is easy to hammer otherwise).
    fun searchPlaces(userId: String, query: String): List<GeocodeSuggestion> {
        rateLimiter.checkLimit("maps:search:$userId", limit = 60, window = Duration.ofMinutes(1))
        return nominatimGeocodingClient.search(query, limit = 8)
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
        mapBookmarkRepository.findByUserIdAndLatitudeAndLongitude(userId, latitude, longitude)?.let { return it }
        return mapBookmarkRepository.save(
            MapBookmark(
                id = "map_bookmark_${UUID.randomUUID()}",
                userId = userId,
                displayName = trimmedName,
                latitude = latitude,
                longitude = longitude,
                folderName = trimmedFolder,
                color = color,
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
                createdAt = existing.createdAt,
            ),
        )
    }

    @Transactional
    fun removeBookmark(userId: String, latitude: Double, longitude: Double) {
        mapBookmarkRepository.deleteByUserIdAndLatitudeAndLongitude(userId, latitude, longitude)
    }

    fun getMyBookmarks(userId: String): List<MapBookmark> = mapBookmarkRepository.findByUserIdOrderByCreatedAtDesc(userId)

    companion object {
        const val DEFAULT_BOOKMARK_FOLDER = "Saved places"
        const val DEFAULT_BOOKMARK_COLOR = "#F5A623"
        private val HEX_COLOR_REGEX = Regex("^#[0-9A-Fa-f]{6}$")
    }
}
