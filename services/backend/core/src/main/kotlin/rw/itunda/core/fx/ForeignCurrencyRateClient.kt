package rw.itunda.core.fx

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.Duration
import java.time.Instant

/**
 * Real, free, no-API-key live exchange rates (ECB-sourced, published by
 * exchangerate-api.com's free tier via open.er-api.com, updated ~daily) -- backs
 * `ForeignCurrencyAccountService`'s real conversion between a user's own RWF and
 * foreign-currency accounts. RWF isn't a reference currency for keyless ECB-only
 * providers (Frankfurter's own `/currencies` list confirmed RWF absent, checked before
 * picking this client) -- open.er-api.com's free tier does carry RWF, confirmed live.
 *
 * In-memory cached with a real, honest TTL (1 hour): the upstream itself only refreshes
 * ~once a day, so re-fetching on every request would be pure waste, not more "live." A
 * failed/unreachable fetch returns null from every caller -- never a fabricated rate,
 * same optional-external-service discipline `NominatimGeocodingClient`/`OsrmRoutingClient`
 * already established.
 */
@Component
class ForeignCurrencyRateClient(
    @Value("\${itunda.fx.base-url:https://open.er-api.com/v6/latest/USD}") private val baseUrl: String,
) {
    private val logger = LoggerFactory.getLogger(ForeignCurrencyRateClient::class.java)
    private val restClient: RestClient = RestClient.create(baseUrl)

    companion object {
        private val CACHE_TTL = Duration.ofHours(1)
    }

    @Volatile
    private var cachedUsdRates: Map<String, Double>? = null

    @Volatile
    private var cachedAt: Instant? = null

    private fun usdRates(): Map<String, Double>? {
        val cached = cachedUsdRates
        val at = cachedAt
        if (cached != null && at != null && Duration.between(at, Instant.now()) < CACHE_TTL) {
            return cached
        }
        return try {
            @Suppress("UNCHECKED_CAST")
            val response = restClient.get().retrieve().body(Map::class.java) as Map<String, Any?>?
            if (response?.get("result") != "success") {
                logger.warn("Exchange-rate provider returned a non-success result: {}", response?.get("result"))
                return cached
            }
            @Suppress("UNCHECKED_CAST")
            val rates = (response["rates"] as? Map<String, Any?>)?.mapNotNull { (k, v) ->
                (v as? Number)?.toDouble()?.let { k to it }
            }?.toMap()
            if (rates.isNullOrEmpty()) return cached
            cachedUsdRates = rates
            cachedAt = Instant.now()
            rates
        } catch (e: RestClientException) {
            logger.warn("Exchange-rate request failed: {}", e.message)
            cached
        }
    }

    /** Real live mid-market rate: 1 unit of [from] converts to how many units of [to].
     * Null if either currency is unknown to the provider or the provider is
     * unreachable and there's no still-fresh-enough cache -- never a fabricated rate. */
    fun getRate(from: String, to: String): Double? {
        if (from == to) return 1.0
        val rates = usdRates() ?: return null
        val fromPerUsd = if (from == "USD") 1.0 else rates[from] ?: return null
        val toPerUsd = if (to == "USD") 1.0 else rates[to] ?: return null
        return toPerUsd / fromPerUsd
    }
}
