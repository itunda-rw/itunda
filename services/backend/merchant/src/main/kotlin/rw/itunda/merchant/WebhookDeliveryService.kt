package rw.itunda.merchant

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

/**
 * Real webhook delivery -- see docs/TOSS_PARITY_MATRIX.md's Merchant row. Payload shape
 * matches Toss Payments' real documented event (docs/PAYMENTS.md's sourced research:
 * `{eventType, createdAt, data: Payment}`), adapted to itunda's QR collection result.
 *
 * Deliberately synchronous, single-attempt, fire-and-log-on-failure -- not Toss's real
 * 7-attempt exponential backoff over up to 4096 minutes, which needs a persistent job queue
 * to do honestly (a multi-hour retry can't survive a process restart as an in-memory retry
 * loop). A single best-effort attempt is the honest full extent of what this single Spring
 * process can offer without one; failure is logged, never thrown -- a merchant's unreachable
 * webhook endpoint must never fail the actual payment collection.
 */
@Service
class WebhookDeliveryService(private val objectMapper: ObjectMapper) {
    private val log = LoggerFactory.getLogger(WebhookDeliveryService::class.java)
    private val httpClient: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    fun deliverPaymentStatusChanged(webhookUrl: String?, data: Map<String, Any?>) {
        if (webhookUrl.isNullOrBlank()) return

        val payload = mapOf(
            "eventType" to "PAYMENT_STATUS_CHANGED",
            "createdAt" to Instant.now().toString(),
            "data" to data,
        )

        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
            if (response.statusCode() !in 200..299) {
                log.warn("Webhook delivery to {} returned HTTP {} -- not retried, see this class's own comment", webhookUrl, response.statusCode())
            }
        } catch (e: Exception) {
            log.warn("Webhook delivery to {} failed: {} -- not retried, see this class's own comment", webhookUrl, e.message)
        }
    }
}
