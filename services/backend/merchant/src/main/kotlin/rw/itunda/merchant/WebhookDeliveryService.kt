package rw.itunda.merchant

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import rw.itunda.core.domain.WebhookDelivery
import rw.itunda.core.domain.WebhookDeliveryStatus
import rw.itunda.core.repository.WebhookDeliveryRepository
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Real webhook delivery -- see docs/TOSS_PARITY_MATRIX.md's Merchant row. Payload shape
 * matches Toss Payments' real documented event (docs/PAYMENTS.md's sourced research:
 * `{eventType, createdAt, data: Payment}`), adapted to itunda's QR collection result.
 *
 * Real persistent retry (2026-07-13), replacing the previous single-attempt,
 * fire-and-log design docs/PAYMENTS.md explicitly named as the gap versus Toss's actual
 * documented scheme: up to 7 attempts total, intervals 1, 4, 16, 64, 256, 1024, 4096
 * minutes (each 4x the last -- sourced from docs.tosspayments.com/en/webhooks), a
 * ~2.8-day retry window. The first attempt is still made synchronously inline (matches
 * both Toss's own behavior -- it delivers immediately, retries are for failures -- and
 * keeps the fast, common "webhook endpoint is up" path free of extra latency); only a
 * failure gets persisted to [WebhookDeliveryRepository] for [WebhookRetryScheduler] to
 * pick up, same durable-outbox reasoning as [rw.itunda.core.events.OutboxEventEntity].
 * A merchant's unreachable webhook endpoint must never fail the actual payment
 * collection -- this method never throws.
 */
@Service
class WebhookDeliveryService(
    private val objectMapper: ObjectMapper,
    private val webhookDeliveryRepository: WebhookDeliveryRepository,
) {
    private val log = LoggerFactory.getLogger(WebhookDeliveryService::class.java)
    private val httpClient: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    companion object {
        // Real Toss Payments schedule (docs.tosspayments.com/en/webhooks): "resends the
        // webhook up to 7 times" over "exponentially increasing" intervals from 1 to
        // 4096 minutes -- each attempt's wait is 4x the previous one's.
        val RETRY_INTERVALS_MINUTES = listOf(1L, 4L, 16L, 64L, 256L, 1024L, 4096L)
        const val MAX_ATTEMPTS = 7
    }

    fun deliverPaymentStatusChanged(webhookUrl: String?, data: Map<String, Any?>) {
        if (webhookUrl.isNullOrBlank()) return

        val payload = mapOf(
            "eventType" to "PAYMENT_STATUS_CHANGED",
            "createdAt" to Instant.now().toString(),
            "data" to data,
        )
        val payloadJson = objectMapper.writeValueAsString(payload)

        val (success, error) = attempt(webhookUrl, payloadJson)
        if (success) return

        log.warn("Webhook delivery to {} failed (attempt 1/{}): {} -- queued for real retry", webhookUrl, MAX_ATTEMPTS, error)
        webhookDeliveryRepository.save(
            WebhookDelivery(
                id = "whd_${UUID.randomUUID()}",
                webhookUrl = webhookUrl,
                payload = payloadJson,
                attemptCount = 1,
                nextAttemptAt = Instant.now().plusSeconds(RETRY_INTERVALS_MINUTES[0] * 60),
                lastError = error,
            ),
        )
    }

    /** Retries an already-queued delivery. Called only by [WebhookRetryScheduler]. */
    fun retry(delivery: WebhookDelivery) {
        val (success, error) = attempt(delivery.webhookUrl, delivery.payload)
        delivery.attemptCount += 1
        if (success) {
            delivery.status = WebhookDeliveryStatus.DELIVERED
            delivery.deliveredAt = Instant.now()
            webhookDeliveryRepository.save(delivery)
            log.info("Webhook delivery {} to {} succeeded on attempt {}", delivery.id, delivery.webhookUrl, delivery.attemptCount)
            return
        }

        delivery.lastError = error
        if (delivery.attemptCount >= MAX_ATTEMPTS) {
            delivery.status = WebhookDeliveryStatus.EXHAUSTED
            log.warn("Webhook delivery {} to {} exhausted all {} attempts: {}", delivery.id, delivery.webhookUrl, MAX_ATTEMPTS, error)
        } else {
            val intervalMinutes = RETRY_INTERVALS_MINUTES[delivery.attemptCount - 1]
            delivery.nextAttemptAt = Instant.now().plusSeconds(intervalMinutes * 60)
            log.warn(
                "Webhook delivery {} to {} failed (attempt {}/{}): {} -- next retry in {} minutes",
                delivery.id, delivery.webhookUrl, delivery.attemptCount, MAX_ATTEMPTS, error, intervalMinutes,
            )
        }
        webhookDeliveryRepository.save(delivery)
    }

    private fun attempt(webhookUrl: String, payloadJson: String): Pair<Boolean, String?> {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(payloadJson))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
            if (response.statusCode() in 200..299) {
                true to null
            } else {
                false to "HTTP ${response.statusCode()}"
            }
        } catch (e: Exception) {
            false to (e.message ?: e.javaClass.simpleName)
        }
    }
}
