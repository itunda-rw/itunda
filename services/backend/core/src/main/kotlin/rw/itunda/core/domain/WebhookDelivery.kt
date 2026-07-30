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
 * exists: a multi-hour (up to ~2.8-day) retry window can't survive a process restart
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
) {
    protected constructor() : this(
        id = "", merchantId = null, eventType = null, webhookUrl = "", payload = "", nextAttemptAt = Instant.now(),
    )
}
