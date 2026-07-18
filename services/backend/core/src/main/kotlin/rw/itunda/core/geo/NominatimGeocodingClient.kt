package rw.itunda.core.geo

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

data class GeocodeResult(val latitude: Double, val longitude: Double)
data class GeocodeSuggestion(val displayName: String, val latitude: Double, val longitude: Double)

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
}
