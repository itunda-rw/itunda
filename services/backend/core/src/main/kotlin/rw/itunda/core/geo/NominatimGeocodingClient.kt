package rw.itunda.core.geo

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

data class GeocodeResult(val latitude: Double, val longitude: Double)

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
     * Real forward geocoding: a free-text address in, a real coordinate out (or null if
     * unconfigured/unreachable/no match). Restricted to Rwanda (`countrycodes=rw`) --
     * itunda's own Nominatim instance only has Rwanda data indexed anyway, but this makes
     * the intent explicit and avoids Nominatim's fallback global search behavior.
     */
    fun geocode(address: String): GeocodeResult? {
        val client = restClient ?: return null
        val trimmed = address.trim()
        if (trimmed.isEmpty()) return null
        return try {
            @Suppress("UNCHECKED_CAST")
            val response = client.get()
                .uri { builder ->
                    builder.path("/search")
                        .queryParam("q", trimmed)
                        .queryParam("format", "json")
                        .queryParam("limit", "1")
                        .queryParam("countrycodes", "rw")
                        .build()
                }
                .retrieve()
                .body(List::class.java) as List<Map<String, Any?>>?
            val first = response?.firstOrNull()
            val lat = (first?.get("lat") as? String)?.toDoubleOrNull()
            val lon = (first?.get("lon") as? String)?.toDoubleOrNull()
            if (lat == null || lon == null) {
                logger.info("Nominatim found no match for a real delivery address -- falling back to no coordinates")
                null
            } else {
                GeocodeResult(lat, lon)
            }
        } catch (e: RestClientException) {
            logger.warn("Nominatim geocode request failed -- falling back to no coordinates: {}", e.message)
            null
        }
    }
}
