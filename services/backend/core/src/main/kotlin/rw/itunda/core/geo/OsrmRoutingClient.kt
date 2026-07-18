package rw.itunda.core.geo

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * A real client for itunda's own self-hosted OSRM routing service (see
 * docs/TOSS_PARITY_MATRIX.md's Maps row) -- a real Rwanda-only road network, built from
 * a real Geofabrik OSM extract, served by the standard `osrm-routed` MLD engine on
 * itunda's own private cloud. Not a third-party API (no Google/Mapbox key involved).
 *
 * Optional by design, same "isConfigured" convention MtnMomoSandboxClient already
 * established: when `itunda.osrm.base-url` isn't set, or the service is unreachable or
 * returns no route, every caller falls back to GeoUtils.haversineKm's straight-line
 * approximation rather than failing the request. A real road distance is strictly
 * better than a straight line, but a straight line is still honest and correct when the
 * routing service isn't available -- never block a real order on infra that may not be
 * deployed everywhere yet.
 */
@Component
class OsrmRoutingClient(
    @Value("\${itunda.osrm.base-url:}") private val baseUrl: String,
) {
    private val logger = LoggerFactory.getLogger(OsrmRoutingClient::class.java)
    private val restClient: RestClient? = if (baseUrl.isNotBlank()) RestClient.create(baseUrl) else null

    val isConfigured: Boolean get() = restClient != null

    /**
     * Real road distance in km between two real coordinates via a real OSRM `/route`
     * call, or null if the service isn't configured, unreachable, or found no route --
     * callers must fall back to GeoUtils.haversineKm, never fail the caller's request on
     * this alone.
     */
    fun routeDistanceKm(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): Double? {
        val client = restClient ?: return null
        return try {
            // OSRM's coordinate order is lng,lat (GeoJSON convention), the reverse of
            // this codebase's own lat,lng convention everywhere else -- easy to get
            // backwards, kept explicit here rather than relying on argument order alone.
            @Suppress("UNCHECKED_CAST")
            val response = client.get()
                .uri("/route/v1/driving/{fromLng},{fromLat};{toLng},{toLat}?overview=false", fromLng, fromLat, toLng, toLat)
                .retrieve()
                .body(Map::class.java) as Map<String, Any?>?
            val code = response?.get("code") as? String
            @Suppress("UNCHECKED_CAST")
            val routes = response?.get("routes") as? List<Map<String, Any?>>
            val distanceMeters = (routes?.firstOrNull()?.get("distance") as? Number)?.toDouble()
            if (code != "Ok" || distanceMeters == null) {
                logger.warn("OSRM route request returned no usable route (code={}) -- falling back to straight-line distance", code)
                null
            } else {
                distanceMeters / 1000.0
            }
        } catch (e: RestClientException) {
            logger.warn("OSRM route request failed -- falling back to straight-line distance: {}", e.message)
            null
        }
    }
}
