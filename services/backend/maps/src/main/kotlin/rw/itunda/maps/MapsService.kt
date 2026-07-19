package rw.itunda.maps

import org.springframework.stereotype.Service
import rw.itunda.auth.RateLimiter
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.geo.GeocodeSuggestion
import rw.itunda.core.geo.NominatimGeocodingClient
import rw.itunda.core.geo.OsrmRoutingClient
import rw.itunda.core.geo.RouteResult
import java.time.Duration

class InvalidMapsCoordinateException(message: String) : RuntimeException(message)
class RouteNotFoundException(message: String) : RuntimeException(message)

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
) {
    // Real anti-spam limit -- same convention every other user-facing endpoint in this
    // codebase already has (search-as-you-type is easy to hammer otherwise).
    fun searchPlaces(userId: String, query: String): List<GeocodeSuggestion> {
        rateLimiter.checkLimit("maps:search:$userId", limit = 60, window = Duration.ofMinutes(1))
        return nominatimGeocodingClient.search(query, limit = 8)
    }

    fun getDirections(userId: String, fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): RouteResult {
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
        return osrmRoutingClient.route(fromLat, fromLng, toLat, toLng)
            ?: throw RouteNotFoundException("No route could be found between these two points")
    }
}
