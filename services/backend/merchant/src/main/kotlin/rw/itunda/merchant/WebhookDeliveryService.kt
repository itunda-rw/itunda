package rw.itunda.merchant

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import rw.itunda.core.domain.WebhookDelivery
import rw.itunda.core.domain.WebhookDeliveryStatus
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.WebhookDeliveryRepository
import java.net.URI
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Real webhook delivery -- see docs/TOSS_PARITY_MATRIX.md's Merchant row. Payload shape
 * matches Toss Payments' real documented event (docs/PAYMENTS.md's sourced research:
 * `{eventType, createdAt, data: Payment}`), adapted to itunda's QR collection result.
 *
 * Real persistent retry (2026-07-13), replacing the previous single-attempt,
 * fire-and-log design docs/PAYMENTS.md explicitly named as the gap versus Toss's actual
 * documented scheme: the initial delivery plus up to 7 retries (8 attempts total),
 * intervals 1, 4, 16, 64, 256, 1024, 4096 minutes apart (each 4x the last -- sourced from
 * docs.tosspayments.com/en/webhooks), a ~3.8-day retry window. Every delivery is
 * persisted as pending and delivered by
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
    private val merchantRepository: MerchantRepository,
) {
    private val log = LoggerFactory.getLogger(WebhookDeliveryService::class.java)

    // Real DNS-rebinding SSRF fix (2026-09-05) -- see WebhookSafeDns's own doc comment.
    // This used to be a plain java.net.http.HttpClient, which re-resolves a hostname
    // URI itself at connection time -- a second, independent DNS query from the one
    // WebhookUrlPolicy.parseForDelivery used to validate it, and therefore a real
    // TOCTOU gap (CWE-918) a merchant controlling their webhook hostname's DNS could
    // exploit. OkHttp's `dns()` hook makes WebhookSafeDns the ONLY resolution used to
    // open the connection, so the safety check and the connection share one lookup.
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(WebhookSafeDns)
        .connectTimeout(5, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    companion object {
        // Real Toss Payments schedule (docs.tosspayments.com/en/webhooks): "resends the
        // webhook up to 7 times" over "exponentially increasing" intervals from 1 to
        // 4096 minutes -- each attempt's wait is 4x the previous one's. "Resends... up
        // to 7 times" means 7 retries AFTER the initial delivery, so MAX_ATTEMPTS is 8
        // (1 initial + 7 retries) -- every one of the 7 configured intervals must
        // actually get used, including the final 4096-minute one, or the real retry
        // window silently shrinks from ~3.8 days to under 24 hours.
        val RETRY_INTERVALS_MINUTES = listOf(1L, 4L, 16L, 64L, 256L, 1024L, 4096L)
        const val MAX_ATTEMPTS = 8
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

    // Real Commerce order-status webhook (2026-08-30, market-readiness audit) -- a
    // merchant integrating their own inventory/fulfillment system with itunda Shop had
    // no way to learn about a new order or a status change except polling
    // GET /api/v1/commerce/merchant-orders. Reuses this exact same real delivery/
    // retry/signature mechanism PAYMENT_STATUS_CHANGED already established -- itunda
    // Pay and itunda Shop are two different money-moving products sharing one honest
    // webhook infrastructure, not two bespoke ones. See OrderService's own call sites.
    fun deliverOrderStatusChanged(merchantId: String, webhookUrl: String?, data: Map<String, Any?>, webhookSecret: String? = null) =
        deliver("ORDER_STATUS_CHANGED", merchantId, webhookUrl, data, webhookSecret)

    // Real Eats order-status webhook (2026-08-30, same market-readiness audit that
    // added ORDER_STATUS_CHANGED for Commerce right above) -- a restaurant's own
    // Merchant row (same real entity Commerce/QR payments already use) integrating
    // their own kitchen-display/POS system had the identical gap: no way to learn about
    // a new order or a status change except polling GET /orders/restaurant-orders.
    // Genuinely separate event type from ORDER_STATUS_CHANGED (Eats' own richer status
    // set -- ACCEPTED/PREPARING/READY_FOR_PICKUP/RIDER_ASSIGNED/PICKED_UP -- is not the
    // same domain as Commerce's PLACED/PACKED/SHIPPED/DELIVERED), same reasoning
    // PAYMENT_STATUS_CHANGED vs CANCEL_STATUS_CHANGED already established for staying
    // genuinely distinct rather than reusing one event with a different status field.
    fun deliverEatsOrderStatusChanged(merchantId: String, webhookUrl: String?, data: Map<String, Any?>, webhookSecret: String? = null) =
        deliver("EATS_ORDER_STATUS_CHANGED", merchantId, webhookUrl, data, webhookSecret)

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

    /**
     * Real ops visibility closing the gap this class's own doc comment names above:
     * EXHAUSTED deliveries had zero admin surface ACROSS merchants -- a merchant's
     * own self-service [deliveryHistory] is scoped to just that merchant, useless
     * for spotting a systemic problem (e.g. a shared downstream outage) shared
     * across several. Same real mapping shape as [deliveryHistory], plus
     * `merchantId` since this spans merchants.
     */
    fun getExhaustedQueue(pageable: Pageable): Page<Map<String, Any?>> =
        webhookDeliveryRepository.findByStatusOrderByCreatedAtDesc(WebhookDeliveryStatus.EXHAUSTED, pageable).map { delivery ->
            mapOf(
                "id" to delivery.id,
                "merchantId" to delivery.merchantId,
                "eventType" to delivery.eventType,
                "attemptCount" to delivery.attemptCount,
                "createdAt" to delivery.createdAt.toString(),
                "lastError" to delivery.lastError,
            )
        }

    /**
     * Real admin replay -- reuses [replayExhausted]'s exact retry-row logic, but
     * looks up the merchant's CURRENT `webhookUrl` server-side (an admin has no
     * reason to know or supply it) rather than trusting a client-provided one, same
     * "never trust a client for a value the server already owns" discipline every
     * other admin action in this codebase follows.
     */
    fun replayExhaustedAsAdmin(deliveryId: String): Map<String, Any?> {
        val original = webhookDeliveryRepository.findById(deliveryId).orElse(null)
            ?.takeIf { it.status == WebhookDeliveryStatus.EXHAUSTED }
            ?: throw WebhookDeliveryNotFoundException("Delivery not found or not exhausted")
        val merchantId = original.merchantId ?: throw WebhookDeliveryNotFoundException("Delivery not found or not exhausted")
        val currentWebhookUrl = merchantRepository.findById(merchantId).orElse(null)?.webhookUrl
            ?: throw WebhookUrlNotConfiguredException("This merchant no longer has a webhook URL configured")
        return replayExhausted(merchantId, deliveryId, currentWebhookUrl)
            ?: throw WebhookDeliveryNotFoundException("Delivery not found or not exhausted")
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
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    true to null
                } else {
                    false to "HTTP ${response.code}"
                }
            }
        } catch (e: Exception) {
            false to (e.message ?: e.javaClass.simpleName)
        }
    }

    internal fun buildRequest(target: URI, payloadJson: String, deliveryId: String, eventType: String?, signature: String? = null): Request {
        val builder = Request.Builder()
            .url(target.toString())
            .header("X-Itunda-Delivery-Id", deliveryId)
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
        return builder.post(payloadJson.toRequestBody("application/json".toMediaType())).build()
    }
}
