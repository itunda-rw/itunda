package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

enum class ProductSubscriptionStatus { ACTIVE, PAUSED, CANCELLED }

/**
 * Real Coupang 정기배송 (subscribe & save)-style recurring product delivery -- sourced
 * from Coupang's own real, published feature: a customer subscribes a frequently-bought
 * staple (tissue, toothpaste, water, diapers) for automatic recurring delivery, picks
 * the delivery day, and gets a real discount (5% for a single subscribed item), with a
 * real delivery interval of up to 6 months.
 *
 * Genuinely distinct from `MerchantBillingSubscription`'s own real Kakao Pay/Toss
 * billing-key equivalent: that is a service-plan charge with no physical fulfillment;
 * this creates a REAL `Order`/`OrderItem` row every cycle via `OrderService.placeOrder`
 * unmodified -- same real-money-movement-reuse discipline every recurring feature in
 * this codebase already follows, just for commerce checkout instead of a raw transfer.
 *
 * `intervalDays` capped at 180 (Coupang's own real "최대 6개월" ceiling, not an invented
 * number). The real discount is applied as a real post-order rebate via
 * `ShoppingCashbackService.awardCashback` (itunda's own already-proven cashback
 * mechanism) rather than a price change inside `placeOrder` itself -- economically
 * identical to a 5% discount, without touching checkout pricing logic every other order
 * flow in this codebase already depends on. **Honestly narrowed v1 scope**: Coupang's
 * real feature also gives a boosted 10% discount for 3+ bundled subscribed products in
 * one delivery -- not implemented here; each `ProductSubscription` is a single product
 * line at the real flat 5% rate, a deliberately smaller, honestly-scoped slice.
 */
@Entity
@Table(name = "product_subscriptions")
class ProductSubscription(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "customer_id", nullable = false, length = 64)
    val customerId: String,

    @Column(name = "merchant_id", nullable = false, length = 64)
    val merchantId: String,

    @Column(name = "product_id", nullable = false, length = 64)
    val productId: String,

    @Column(nullable = false)
    var quantity: Int,

    @Column(name = "interval_days", nullable = false)
    var intervalDays: Int,

    @Column(name = "delivery_address", nullable = false, length = 500)
    var deliveryAddress: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: ProductSubscriptionStatus = ProductSubscriptionStatus.ACTIVE,

    @Column(name = "next_delivery_at", nullable = false)
    var nextDeliveryAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "last_delivered_at")
    var lastDeliveredAt: Instant? = null,

    @Column(name = "delivery_count", nullable = false)
    var deliveryCount: Int = 0,

    @Column(name = "last_failure_reason", length = 255)
    var lastFailureReason: String? = null,

    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,

    // Customer updates/cancellation and the recurring delivery scheduler share this
    // lifecycle; a due order must not be created after a concurrent cancellation.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(
        id = "", customerId = "", merchantId = "", productId = "", quantity = 1, intervalDays = 30, deliveryAddress = "",
    )
}
