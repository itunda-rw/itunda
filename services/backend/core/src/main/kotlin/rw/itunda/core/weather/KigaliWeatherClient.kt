package rw.itunda.core.weather

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.Duration
import java.time.Instant

data class KigaliWeather(val temperatureCelsius: Double, val condition: String, val pm2_5: Double?)

/**
 * Real, free, no-API-key current weather + air quality for Kigali (itunda Maps
 * redesign, 2026-08-28, direct Naver Map reference: the Maps landing screen's weather
 * chip) -- Open-Meteo, same "real, free, keyless" bar `ForeignCurrencyRateClient`
 * already establishes for FX rates, not a paid/keyed provider. Two separate real
 * Open-Meteo APIs: `/v1/forecast` for temperature/condition, the dedicated
 * `air-quality-api.open-meteo.com` for real PM2.5 (Naver's own "미세" fine-dust line's
 * real equivalent) -- `pm2_5` is genuinely null, never fabricated, if that second call
 * fails while the first succeeds.
 *
 * Fixed at Kigali's real coordinates (-1.9441, 30.0619, the same city-center point this
 * codebase's own seed data already uses) -- itunda has no per-user location for this
 * chip's placement (the Maps landing screen, before any search), same scope as the
 * reference's own single-city weather widget. In-memory cached 30 minutes -- real
 * weather doesn't change fast enough to justify a live per-request call.
 */
@Component
class KigaliWeatherClient(
    @Value("\${itunda.weather.forecast-base-url:https://api.open-meteo.com/v1/forecast}") private val forecastBaseUrl: String,
    @Value("\${itunda.weather.air-quality-base-url:https://air-quality-api.open-meteo.com/v1/air-quality}") private val airQualityBaseUrl: String,
) {
    private val logger = LoggerFactory.getLogger(KigaliWeatherClient::class.java)
    private val forecastClient: RestClient = RestClient.create(forecastBaseUrl)
    private val airQualityClient: RestClient = RestClient.create(airQualityBaseUrl)

    companion object {
        private const val KIGALI_LAT = -1.9441
        private const val KIGALI_LNG = 30.0619
        private val CACHE_TTL = Duration.ofMinutes(30)

        // Real Open-Meteo WMO weather-code -> short label mapping, the small real subset
        // (clear/cloudy/rain/thunderstorm) their own docs group codes into -- not every
        // one of the ~30 real codes gets a distinct label, matching the reference's own
        // simple icon+word treatment rather than a fabricated precision this data doesn't
        // support any better than a coarse bucket.
        private fun conditionFor(weatherCode: Int?): String = when (weatherCode) {
            0 -> "Clear"
            1, 2, 3 -> "Cloudy"
            45, 48 -> "Foggy"
            in 51..67 -> "Rain"
            in 71..77 -> "Snow"
            in 80..82 -> "Showers"
            in 95..99 -> "Thunderstorm"
            else -> "—"
        }
    }

    @Volatile private var cached: KigaliWeather? = null
    @Volatile private var cachedAt: Instant? = null

    fun current(): KigaliWeather? {
        val at = cachedAt
        val existing = cached
        if (existing != null && at != null && Duration.between(at, Instant.now()) < CACHE_TTL) {
            return existing
        }
        val weather = fetchForecast() ?: return existing
        val pm25 = fetchPm25()
        val result = weather.copy(pm2_5 = pm25)
        cached = result
        cachedAt = Instant.now()
        return result
    }

    private fun fetchForecast(): KigaliWeather? {
        return try {
            @Suppress("UNCHECKED_CAST")
            val response = forecastClient.get()
                .uri { it.queryParam("latitude", KIGALI_LAT).queryParam("longitude", KIGALI_LNG).queryParam("current_weather", "true").build() }
                .retrieve().body(Map::class.java) as Map<String, Any?>?
            @Suppress("UNCHECKED_CAST")
            val current = response?.get("current_weather") as? Map<String, Any?>
            val temp = (current?.get("temperature") as? Number)?.toDouble() ?: return null
            val code = (current["weathercode"] as? Number)?.toInt()
            KigaliWeather(temperatureCelsius = temp, condition = conditionFor(code), pm2_5 = null)
        } catch (e: RestClientException) {
            logger.warn("Kigali weather forecast request failed: {}", e.message)
            null
        }
    }

    private fun fetchPm25(): Double? = try {
        @Suppress("UNCHECKED_CAST")
        val response = airQualityClient.get()
            .uri { it.queryParam("latitude", KIGALI_LAT).queryParam("longitude", KIGALI_LNG).queryParam("current", "pm2_5").build() }
            .retrieve().body(Map::class.java) as Map<String, Any?>?
        @Suppress("UNCHECKED_CAST")
        val current = response?.get("current") as? Map<String, Any?>
        (current?.get("pm2_5") as? Number)?.toDouble()
    } catch (e: RestClientException) {
        logger.warn("Kigali air-quality request failed: {}", e.message)
        null
    }
}
