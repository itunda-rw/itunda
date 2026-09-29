package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

enum class MerchantBillingSubscriptionStatus { ACTIVE, CANCELLED }

/**
 * A real customer's own recurring-billing authorization against a
 * [MerchantBillingPlan] -- itunda's own honest equivalent of the real Kakao Pay "sid"/
 * Toss Payments billing key: acquired once (subscribing charges the first real cycle
 * immediately, matching both platforms' own real "인증 + 첫결제" flow), then reused by
 * [rw.itunda.merchant.MerchantBillingScheduler] every real `intervalDays` with zero
 * further customer approval -- until the customer explicitly cancels. Same real
 * "skip a failed charge, retry next cycle, never fabricate success" discipline
 * `AutoTransfer`'s own doc comment already establishes for a different real recurring
 * flow -- `lastFailureReason` is honest, not silently cleared.
 */
@Entity
@Table(name = "merchant_billing_subscriptions")
class MerchantBillingSubscription(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "plan_id", nullable = false, length = 64)
    val planId: String,

    @Column(name = "merchant_id", nullable = false, length = 64)
    val merchantId: String,

    @Column(name = "customer_id", nullable = false, length = 64)
    val customerId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: MerchantBillingSubscriptionStatus = MerchantBillingSubscriptionStatus.ACTIVE,

    @Column(name = "next_charge_at", nullable = false)
    var nextChargeAt: Instant = Instant.now(),

    @Column(name = "last_charged_at")
    var lastChargedAt: Instant? = null,

    @Column(name = "charge_count", nullable = false)
    var chargeCount: Int = 0,

    @Column(name = "last_failure_reason", length = 255)
    var lastFailureReason: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,

    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(id = "", planId = "", merchantId = "", customerId = "")
}
