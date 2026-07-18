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

    /**
     * Real road distance in km from one origin to many destinations via a single OSRM
     * `/table` call -- the batched sibling of [routeDistanceKm], for ranking N candidates
     * (e.g. Marketplace proximity search) without N separate `/route` round trips. Each
     * entry in the returned list lines up positionally with `destinations`; an entry is
     * null wherever that one leg has no route, same never-fail contract as
     * [routeDistanceKm] -- callers fall back to GeoUtils.haversineKm per-candidate, not
     * for the whole batch.
     */
    fun routeDistancesKm(fromLat: Double, fromLng: Double, destinations: List<Pair<Double, Double>>): List<Double?> {
        val client = restClient ?: return destinations.map { null }
        if (destinations.isEmpty()) return emptyList()
        return try {
            // Built as one literal path string, not a single templated variable, so the
            // required-literal ',' and ';' separators in OSRM's coordinate syntax aren't
            // percent-encoded -- unlike routeDistanceKm's per-number template variables,
            // there's no fixed placeholder count for a variable-length destination list.
            val coords = buildString {
                append(fromLng).append(',').append(fromLat)
                destinations.forEach { (lat, lng) -> append(';').append(lng).append(',').append(lat) }
            }
            val destinationIndices = (1..destinations.size).joinToString(";")
            val path = "/table/v1/driving/$coords?sources=0&destinations=$destinationIndices&annotations=distance"
            @Suppress("UNCHECKED_CAST")
            val response = client.get().uri(path).retrieve().body(Map::class.java) as Map<String, Any?>?
            val code = response?.get("code") as? String
            @Suppress("UNCHECKED_CAST")
            val distances = response?.get("distances") as? List<List<Any?>>
            val row = distances?.firstOrNull()
            if (code != "Ok" || row == null) {
                logger.warn("OSRM table request returned no usable matrix (code={}) -- falling back to straight-line distances", code)
                destinations.map { null }
            } else {
                row.map { (it as? Number)?.let { meters -> meters.toDouble() / 1000.0 } }
            }
        } catch (e: RestClientException) {
            logger.warn("OSRM table request failed -- falling back to straight-line distances: {}", e.message)
            destinations.map { null }
        }
    }
}
