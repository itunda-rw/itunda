package rw.itunda.core.geo

import kotlin.math.cos
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

data class GeocodeResult(val latitude: Double, val longitude: Double)
data class GeocodeSuggestion(val displayName: String, val latitude: Double, val longitude: Double)
data class NearbyPlace(val displayName: String, val latitude: Double, val longitude: Double, val distanceKm: Double)

/**
 * A real client for itunda's own self-hosted Nominatim geocoder (see
 * docs/TOSS_PARITY_MATRIX.md's Maps row) -- a real Rwanda-only address index, built from
 * the same Geofabrik OSM extract OsrmRoutingClient's road network uses, served by the
 * standard `mediagis/nominatim` image on itunda's own private cloud. Not a third-party
 * geocoding API.
 *
 * Optional by design, same convention `OsrmRoutingClient`/`MtnMomoSandboxClient` already
 * established: when `itunda.nominatim.base-url` isn't set, unreachable, or finds no
 * match, every caller gets null back and falls back to whatever it was already doing
 * (e.g. requiring a manually-entered coordinate) -- never a fabricated location.
 */
@Component
class NominatimGeocodingClient(
    @Value("\${itunda.nominatim.base-url:}") private val baseUrl: String,
) {
    private val logger = LoggerFactory.getLogger(NominatimGeocodingClient::class.java)
    private val restClient: RestClient? = if (baseUrl.isNotBlank()) RestClient.create(baseUrl) else null

    val isConfigured: Boolean get() = restClient != null

    /**
     * Real multi-result address search: a free-text (possibly partial) query in, up to
     * `limit` real ranked candidates out (each a real display name + coordinate),
     * restricted to Rwanda (`countrycodes=rw`). Empty list if unconfigured/unreachable/
     * no match -- never a fabricated suggestion. Backs both the automatic single-best-
     * match `geocode` below and a real user-facing address-search endpoint.
     */
    fun search(query: String, limit: Int = 5): List<GeocodeSuggestion> {
        val client = restClient ?: return emptyList()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return try {
            @Suppress("UNCHECKED_CAST")
            val response = client.get()
                .uri { builder ->
                    builder.path("/search")
                        .queryParam("q", trimmed)
                        .queryParam("format", "json")
                        .queryParam("limit", limit.toString())
                        .queryParam("countrycodes", "rw")
                        .build()
                }
                .retrieve()
                .body(List::class.java) as List<Map<String, Any?>>?
            response.orEmpty().mapNotNull { row ->
                val lat = (row["lat"] as? String)?.toDoubleOrNull()
                val lon = (row["lon"] as? String)?.toDoubleOrNull()
                val displayName = row["display_name"] as? String
                if (lat == null || lon == null || displayName == null) null else GeocodeSuggestion(displayName, lat, lon)
            }
        } catch (e: RestClientException) {
            logger.warn("Nominatim search request failed: {}", e.message)
            emptyList()
        }
    }

    /**
     * Real forward geocoding: a free-text address in, the single best real coordinate
     * out (or null if unconfigured/unreachable/no match). A thin wrapper over `search`.
     */
    fun geocode(address: String): GeocodeResult? =
        search(address, limit = 1).firstOrNull()?.let { GeocodeResult(it.latitude, it.longitude) }

    /**
     * Real category/nearby-places search (restaurants, hospitals, pharmacies, etc.),
     * matching Naver/Kakao Maps' own category-chip search -- e.g. "restaurants near me".
     * `searchTerm` is a plain free-text category word (see `MapPlaceCategory`) bounded to
     * a real degree-box around the given point (`viewbox` + `bounded=1`), then filtered/
     * sorted by real `GeoUtils.haversineKm` distance since Nominatim's own bounded search
     * ranks by relevance/importance, not proximity -- a corner of the request box can be
     * farther than `radiusKm`, so the box is a coarse pre-filter and the real circular
     * radius is enforced here. Free text, not Nominatim's structured `[key=value]` tag
     * syntax -- that was tried first and confirmed live to return zero results against
     * this deployment (see `MapPlaceCategory`'s own doc comment for why).
     */
    fun searchNearby(
        searchTerm: String,
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
        limit: Int = 20,
    ): List<NearbyPlace> {
        val client = restClient ?: return emptyList()
        val latDelta = radiusKm / 111.0
        val lngDelta = radiusKm / (111.0 * cos(Math.toRadians(latitude)).coerceAtLeast(0.01))
        val minLat = latitude - latDelta
        val maxLat = latitude + latDelta
        val minLng = longitude - lngDelta
        val maxLng = longitude + lngDelta
        return try {
            @Suppress("UNCHECKED_CAST")
            val response = client.get()
                .uri { builder ->
                    builder.path("/search")
                        .queryParam("q", searchTerm)
                        .queryParam("format", "json")
                        .queryParam("limit", (limit * 3).toString())
                        .queryParam("countrycodes", "rw")
                        .queryParam("viewbox", "$minLng,$maxLat,$maxLng,$minLat")
                        .queryParam("bounded", "1")
                        .build()
                }
                .retrieve()
                .body(List::class.java) as List<Map<String, Any?>>?
            response.orEmpty()
                .mapNotNull { row ->
                    val lat = (row["lat"] as? String)?.toDoubleOrNull()
                    val lon = (row["lon"] as? String)?.toDoubleOrNull()
                    val displayName = row["display_name"] as? String
                    if (lat == null || lon == null || displayName == null) {
                        null
                    } else {
                        NearbyPlace(displayName, lat, lon, GeoUtils.haversineKm(latitude, longitude, lat, lon))
                    }
                }
                .filter { it.distanceKm <= radiusKm }
                .sortedBy { it.distanceKm }
                .take(limit)
        } catch (e: RestClientException) {
            logger.warn("Nominatim nearby-category search failed: {}", e.message)
            emptyList()
        }
    }

    /**
     * Real reverse geocoding: a coordinate in, the real neighborhood-level place name out
     * (or null if unconfigured/unreachable/no match) -- closes the "no real hyperlocal
     * auto-filtering by a user's actual neighborhood" gap Marketplace/Community/Jobs/Real
     * Estate's own doc comments all name, without inventing a fake stand-in: this reuses
     * the exact same live Rwanda-only Nominatim deployment `search`/`geocode` already do.
     * Prefers `address.suburb` (confirmed live to return real Kigali sector-level names
     * like "Nyarugenge" against this deployment), falling back through `neighbourhood`/
     * `quarter`/`city_district` for areas OSM tagged differently -- never a fabricated
     * neighborhood name.
     */
    fun reverseGeocode(latitude: Double, longitude: Double): String? {
        val client = restClient ?: return null
        return try {
            @Suppress("UNCHECKED_CAST")
            val response = client.get()
                .uri { builder ->
                    builder.path("/reverse")
                        .queryParam("lat", latitude.toString())
                        .queryParam("lon", longitude.toString())
                        .queryParam("format", "json")
                        .build()
                }
                .retrieve()
                .body(Map::class.java) as Map<String, Any?>?
            @Suppress("UNCHECKED_CAST")
            val address = response?.get("address") as? Map<String, Any?>
            (address?.get("suburb") as? String)
                ?: (address?.get("neighbourhood") as? String)
                ?: (address?.get("quarter") as? String)
                ?: (address?.get("city_district") as? String)
        } catch (e: RestClientException) {
            logger.warn("Nominatim reverse-geocode request failed: {}", e.message)
            null
        }
    }
}
