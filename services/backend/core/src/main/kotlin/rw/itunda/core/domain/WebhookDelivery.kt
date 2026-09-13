package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class WebhookDeliveryStatus { PENDING, DELIVERED, EXHAUSTED }

/**
 * Real persistent webhook retry queue -- see docs/PAYMENTS.md's own sourced Toss
 * Payments research ("Toss retries a failing endpoint up to 7 times over exponentially
 * increasing intervals (1 to 4096 minutes)") and docs/TOSS_PARITY_MATRIX.md's Merchant
 * row, which named single-attempt delivery as the honest gap versus that real scheme.
 * A persisted row, not an in-memory retry loop, for the same reason [OutboxEventEntity]
 * exists: a multi-hour (up to ~3.8-day) retry window can't survive a process restart
 * otherwise.
 */
@Entity
@Table(name = "webhook_deliveries")
class WebhookDelivery(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "merchant_id", length = 64)
    val merchantId: String? = null,

    /** Immutable domain-event contract, retained independently of the JSON payload. */
    @Column(name = "event_type", length = 64)
    val eventType: String? = null,

    @Column(name = "webhook_url", nullable = false, length = 2048)
    val webhookUrl: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val payload: String,

    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: WebhookDeliveryStatus = WebhookDeliveryStatus.PENDING,

    @Column(name = "next_attempt_at", nullable = false)
    var nextAttemptAt: Instant,

    @Column(name = "last_error", length = 500)
    var lastError: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "delivered_at")
    var deliveredAt: Instant? = null,

    // Real webhook signature verification (2026-08-30) -- see WebhookDeliveryService's
    // own doc comment. Computed once at creation time (using the merchant's
    // webhookSecret at that moment) and resent unchanged on every retry -- a merchant
    // rotating their secret mid-retry-window can never invalidate an already-queued
    // delivery's signature. Null when the merchant never generated a webhook secret.
    @Column(length = 64)
    val signature: String? = null,

    // Real admin-accountability gap closed (2026-09-13) -- WebhookDeliveryAdminController
    // .replay had zero record of which admin acted, the exact same gap class this
    // codebase already closed for Merchant/Vehicle-Inspection/Partner (see Partner.kt's
    // own statusChangedBy doc comment) but missed for this endpoint, which lives in
    // that same merchant module. Null for every OTHER row (a normal WebhookRetryScheduler
    // retry, or a merchant's own self-service replay via MerchantController) -- only an
    // admin-triggered cross-merchant replay ever sets this.
    @Column(name = "replayed_by_admin_id", length = 64)
    val replayedByAdminId: String? = null,
) {
    protected constructor() : this(
        id = "", merchantId = null, eventType = null, webhookUrl = "", payload = "", nextAttemptAt = Instant.now(),
    )
}
