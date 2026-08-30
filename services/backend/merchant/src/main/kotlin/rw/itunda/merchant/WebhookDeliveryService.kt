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
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Real webhook delivery -- see docs/TOSS_PARITY_MATRIX.md's Merchant row. Payload shape
 * matches Toss Payments' real documented event (docs/PAYMENTS.md's sourced research:
 * `{eventType, createdAt, data: Payment}`), adapted to itunda's QR collection result.
 *
 * Real persistent retry (2026-07-13), replacing the previous single-attempt,
 * fire-and-log design docs/PAYMENTS.md explicitly named as the gap versus Toss's actual
 * documented scheme: up to 7 attempts total, intervals 1, 4, 16, 64, 256, 1024, 4096
 * minutes (each 4x the last -- sourced from docs.tosspayments.com/en/webhooks), a
 * ~2.8-day retry window. Every delivery is persisted as pending and delivered by
 * [WebhookRetryScheduler] after the
 * transaction commits. This means a receiver can never observe an event for a
 * rolled-back payment, and a slow receiver cannot delay the payment response. This
 * gives merchants a complete audit trail while leaving failed rows pending for
 * [WebhookRetryScheduler] to pick up, with the same durable-outbox reasoning as
 * [rw.itunda.core.events.OutboxEventEntity].
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

    fun deliverPaymentStatusChanged(merchantId: String, webhookUrl: String?, data: Map<String, Any?>, webhookSecret: String? = null) =
        deliver("PAYMENT_STATUS_CHANGED", merchantId, webhookUrl, data, webhookSecret)

    // Real cancel/refund webhook event (2026-07-21) -- Toss Payments' own real webhooks
    // distinguish CANCEL_STATUS_CHANGED from PAYMENT_STATUS_CHANGED as a genuinely
    // separate event type (docs.tosspayments.com/en/webhooks), not the same event
    // reused with a different status field -- so a merchant's webhook receiver can
    // dispatch on `eventType` alone without inspecting `data` first. See
    // MerchantService.cancelPayment.
    fun deliverCancelStatusChanged(merchantId: String, webhookUrl: String?, data: Map<String, Any?>, webhookSecret: String? = null) =
        deliver("CANCEL_STATUS_CHANGED", merchantId, webhookUrl, data, webhookSecret)

    fun deliveryHistory(merchantId: String): List<Map<String, Any?>> =
        webhookDeliveryRepository.findTop100ByMerchantIdOrderByCreatedAtDesc(merchantId).map { delivery ->
            mapOf(
                "id" to delivery.id,
                "eventType" to delivery.eventType,
                "status" to delivery.status.name,
                "attemptCount" to delivery.attemptCount,
                "createdAt" to delivery.createdAt.toString(),
                "nextAttemptAt" to delivery.nextAttemptAt.toString(),
                "deliveredAt" to delivery.deliveredAt?.toString(),
                "lastError" to delivery.lastError,
            )
        }

    fun replayExhausted(merchantId: String, deliveryId: String, currentWebhookUrl: String): Map<String, Any?>? {
        val original = webhookDeliveryRepository.findById(deliveryId).orElse(null)
            ?.takeIf { it.merchantId == merchantId && it.status == WebhookDeliveryStatus.EXHAUSTED }
            ?: return null
        val replay = webhookDeliveryRepository.save(
            WebhookDelivery(
                id = "whd_${UUID.randomUUID()}",
                merchantId = merchantId,
                eventType = original.eventType,
                webhookUrl = currentWebhookUrl,
                payload = original.payload,
                nextAttemptAt = Instant.now(),
                // Real webhook signature verification (2026-08-30) -- reuses the
                // ORIGINAL delivery's signature unchanged, same "sign once, resend
                // unchanged" reasoning as a normal retry (see this class's own
                // WebhookDelivery.signature doc comment) -- a replay is conceptually
                // the same event being resent, not a new one to re-sign.
                signature = original.signature,
            ),
        )
        return mapOf("id" to replay.id, "status" to replay.status.name, "replayOf" to original.id)
    }

    private fun deliver(eventType: String, merchantId: String, webhookUrl: String?, data: Map<String, Any?>, webhookSecret: String?) {
        if (webhookUrl.isNullOrBlank()) return

        // Generate this before constructing the immutable payload, so every receiver
        // can deduplicate from either the payload or X-Itunda-Delivery-Id header.
        val deliveryId = "whd_${UUID.randomUUID()}"
        val payload = mapOf(
            "eventId" to deliveryId,
            "eventType" to eventType,
            "createdAt" to Instant.now().toString(),
            "data" to data,
        )
        val payloadJson = objectMapper.writeValueAsString(payload)

        val delivery = WebhookDelivery(
            id = deliveryId,
            merchantId = merchantId,
            eventType = eventType,
            webhookUrl = webhookUrl,
            payload = payloadJson,
            // A scheduler worker sees this row only after the enclosing transaction
            // commits, so delivery is both asynchronous and transactionally ordered.
            nextAttemptAt = Instant.now(),
            // Real webhook signature verification (2026-08-30) -- see this class's own
            // WebhookDelivery.signature doc comment: computed once here, against
            // whichever secret is current at creation time, and resent unchanged on
            // every retry. Null (no header sent at all) for a merchant who never
            // generated a webhook secret -- fully backward-compatible.
            signature = webhookSecret?.let { sign(payloadJson, it) },
        )
        persist(delivery)
    }

    /** Real HMAC-SHA256 over the exact raw payload bytes -- the same primitive every
     * real payment gateway's webhook-signing scheme (Stripe, Toss Payments) uses, so a
     * merchant's receiver can verify authenticity with one standard library call rather
     * than a bespoke itunda-specific algorithm. */
    private fun sign(payloadJson: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(payloadJson.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /** Retries an already-queued delivery. Called only by [WebhookRetryScheduler]. */
    fun retry(delivery: WebhookDelivery) {
        val (success, error) = attempt(delivery.webhookUrl, delivery.payload, delivery.id, delivery.eventType, delivery.signature)
        delivery.attemptCount += 1
        if (success) {
            delivery.status = WebhookDeliveryStatus.DELIVERED
            delivery.deliveredAt = Instant.now()
            persist(delivery)
            log.info("Webhook delivery {} to {} succeeded on attempt {}", delivery.id, WebhookUrlPolicy.displayTarget(delivery.webhookUrl), delivery.attemptCount)
            return
        }

        delivery.lastError = error
        if (delivery.attemptCount >= MAX_ATTEMPTS) {
            delivery.status = WebhookDeliveryStatus.EXHAUSTED
            log.warn("Webhook delivery {} to {} exhausted all {} attempts: {}", delivery.id, WebhookUrlPolicy.displayTarget(delivery.webhookUrl), MAX_ATTEMPTS, error)
        } else {
            val intervalMinutes = RETRY_INTERVALS_MINUTES[delivery.attemptCount - 1]
            delivery.nextAttemptAt = Instant.now().plusSeconds(intervalMinutes * 60)
            log.warn(
                "Webhook delivery {} to {} failed (attempt {}/{}): {} -- next retry in {} minutes",
                delivery.id, WebhookUrlPolicy.displayTarget(delivery.webhookUrl), delivery.attemptCount, MAX_ATTEMPTS, error, intervalMinutes,
            )
        }
        persist(delivery)
    }

    /** A webhook must never affect the completed financial operation that triggered it. */
    private fun persist(delivery: WebhookDelivery) {
        try {
            webhookDeliveryRepository.save(delivery)
        } catch (e: Exception) {
            log.error("Could not persist webhook delivery {}: {}", delivery.id, e.message)
        }
    }

    private fun attempt(
        webhookUrl: String,
        payloadJson: String,
        deliveryId: String,
        eventType: String?,
        signature: String?,
    ): Pair<Boolean, String?> {
        return try {
            val request = buildRequest(WebhookUrlPolicy.parseForDelivery(webhookUrl), payloadJson, deliveryId, eventType, signature)
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

    internal fun buildRequest(target: URI, payloadJson: String, deliveryId: String, eventType: String?, signature: String? = null): HttpRequest {
        val builder = HttpRequest.newBuilder()
            .uri(target)
            .header("Content-Type", "application/json")
            .header("X-Itunda-Delivery-Id", deliveryId)
            .timeout(Duration.ofSeconds(5))
        if (!eventType.isNullOrBlank()) {
            builder.header("X-Itunda-Event-Type", eventType)
        }
        // Real webhook signature verification (2026-08-30) -- see WebhookDelivery
        // .signature's own doc comment. A merchant's receiver recomputes
        // HMAC-SHA256(rawBody, theirWebhookSecret) and compares it to this header to
        // confirm the request genuinely came from itunda, not a forged POST to a
        // guessed/leaked webhook URL.
        if (!signature.isNullOrBlank()) {
            builder.header("X-Itunda-Signature", signature)
        }
        return builder.POST(HttpRequest.BodyPublishers.ofString(payloadJson)).build()
    }
}
