package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

/**
 * A real Kakao Pay 정기결제/Toss Payments 빌링키-style merchant recurring-billing plan --
 * sourced from both platforms' own real, published merchant APIs (developers.kakaopay.com's
 * online-payment docs describe a real "sid" recurring-billing key; tosspayments.com's own
 * blog walks through "구독 결제 서비스 간단히 구현하기... 빌링키 발급하기"). A merchant
 * defines a real recurring charge (e.g. a subscription box) once; a customer authorizes it
 * once (`MerchantBillingSubscription`), and itunda charges their account automatically every
 * `intervalDays` from then on -- the real "no re-approval each cycle" mechanic that
 * distinguishes a subscription from a one-off QR/card payment.
 *
 * `active = false` (soft-deactivate, matching `MerchantProduct.active`'s own convention)
 * only stops NEW subscriptions -- existing subscribers keep being charged until they
 * explicitly cancel their own `MerchantBillingSubscription`, the same real "existing
 * subscribers are grandfathered" practice most real subscription products follow rather
 * than force-cancelling a paying customer's plan out from under them.
 */
@Entity
@Table(name = "merchant_billing_plans")
class MerchantBillingPlan(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "merchant_id", nullable = false, length = 64)
    val merchantId: String,

    @Column(nullable = false)
    var name: String,

    @Column(length = 500)
    var description: String? = null,

    @Column(nullable = false, precision = 18, scale = 2)
    var amount: BigDecimal,

    @Column(name = "interval_days", nullable = false)
    var intervalDays: Int,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", merchantId = "", name = "", amount = BigDecimal.ZERO, intervalDays = 30)
}
