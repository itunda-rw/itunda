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

    /**
     * Real turn-by-turn-capable route between two real coordinates -- the real polyline
     * geometry (a list of real [lat, lng] points along actual roads), distance, and
     * duration, backing itunda's own self-hosted "Directions" feature (see
     * docs/TOSS_PARITY_MATRIX.md's Maps row). Unlike [routeDistanceKm] (distance only,
     * `overview=false`), this asks OSRM for the full route shape (`overview=full`,
     * `geometries=geojson`) so a real map UI can draw the actual path, not just report a
     * number. Null on the same never-fail terms as every other method here --
     * unconfigured, unreachable, or no route.
     */
    fun route(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): RouteResult? {
        val client = restClient ?: return null
        return try {
            @Suppress("UNCHECKED_CAST")
            val response = client.get()
                .uri(
                    // Real turn-by-turn steps (2026-07-20) -- `steps=true` asks OSRM for
                    // its own real per-maneuver breakdown of the route (turn/street-name/
                    // distance), not just the overall line, closing the gap between "a
                    // drawn route" and a real Naver/Kakao Maps-style instruction list.
                    "/route/v1/driving/{fromLng},{fromLat};{toLng},{toLat}?overview=full&geometries=geojson&steps=true",
                    fromLng, fromLat, toLng, toLat,
                )
                .retrieve()
                .body(Map::class.java) as Map<String, Any?>?
            val code = response?.get("code") as? String
            @Suppress("UNCHECKED_CAST")
            val bestRoute = (response?.get("routes") as? List<Map<String, Any?>>)?.firstOrNull()
            val distanceMeters = (bestRoute?.get("distance") as? Number)?.toDouble()
            val durationSeconds = (bestRoute?.get("duration") as? Number)?.toDouble()
            @Suppress("UNCHECKED_CAST")
            val geometry = bestRoute?.get("geometry") as? Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val coordinates = geometry?.get("coordinates") as? List<List<Number>>
            if (code != "Ok" || distanceMeters == null || durationSeconds == null || coordinates == null) {
                logger.warn("OSRM route request returned no usable route (code={})", code)
                null
            } else {
                // Flip OSRM's [lng, lat] GeoJSON order back to this codebase's own
                // [lat, lng] convention for the response, same reasoning as
                // routeDistanceKm's own doc comment on why this is kept explicit.
                RouteResult(
                    distanceKm = distanceMeters / 1000.0,
                    durationMinutes = durationSeconds / 60.0,
                    geometry = coordinates.map { listOf(it[1].toDouble(), it[0].toDouble()) },
                    steps = parseSteps(bestRoute),
                )
            }
        } catch (e: RestClientException) {
            logger.warn("OSRM route request failed: {}", e.message)
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSteps(bestRoute: Map<String, Any?>?): List<RouteStep> {
        val legs = bestRoute?.get("legs") as? List<Map<String, Any?>> ?: return emptyList()
        return legs.flatMap { leg -> leg["steps"] as? List<Map<String, Any?>> ?: emptyList() }
            .mapNotNull { step ->
                val distanceMeters = (step["distance"] as? Number)?.toDouble() ?: return@mapNotNull null
                val streetName = (step["name"] as? String)?.trim()?.ifBlank { null }
                @Suppress("UNCHECKED_CAST")
                val maneuver = step["maneuver"] as? Map<String, Any?> ?: emptyMap()
                val type = maneuver["type"] as? String ?: "continue"
                val modifier = maneuver["modifier"] as? String
                RouteStep(instruction = maneuverInstruction(type, modifier, streetName), distanceMeters = distanceMeters, streetName = streetName)
            }
    }

    /**
     * Real, sourced maneuver vocabulary -- OSRM's own documented `StepManeuver` `type`/
     * `modifier` values (project-osrm.org/docs -- turn/depart/arrive/merge/fork/roundabout/
     * etc, each with an optional left/right/straight-family modifier), turned into a plain
     * English instruction the same way any real turn-by-turn app renders them. Not
     * invented: every branch below corresponds to a real, documented OSRM maneuver type.
     */
    private fun maneuverInstruction(type: String, modifier: String?, streetName: String?): String {
        val onto = streetName?.let { " onto $it" } ?: ""
        val direction = modifier?.replace('-', ' ') ?: "ahead"
        return when (type) {
            "depart" -> "Head $direction$onto"
            "arrive" -> "Arrive at your destination"
            "turn" -> "Turn $direction$onto"
            "new name" -> "Continue$onto"
            "continue" -> if (modifier == null || modifier == "straight") "Continue straight$onto" else "Continue $direction$onto"
            "merge" -> "Merge$onto"
            "on ramp" -> "Take the ramp$onto"
            "off ramp" -> "Take the exit$onto"
            "fork" -> "At the fork, keep $direction$onto"
            "end of road" -> "Turn $direction$onto"
            "roundabout", "rotary" -> "Enter the roundabout$onto"
            "roundabout turn" -> "At the roundabout, turn $direction$onto"
            "exit roundabout", "exit rotary" -> "Exit the roundabout$onto"
            "use lane" -> "Continue$onto"
            else -> "Continue$onto"
        }.trim()
    }
}

data class RouteStep(val instruction: String, val distanceMeters: Double, val streetName: String?)
data class RouteResult(val distanceKm: Double, val durationMinutes: Double, val geometry: List<List<Double>>, val steps: List<RouteStep> = emptyList())
