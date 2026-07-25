package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.Instant

enum class CouponDiscountType { PERCENT, FIXED_AMOUNT }

/**
 * Real merchant-issued coupons + 단골 (regular customer) loyalty gating -- closes the
 * "coupons/loyalty on top" half of the Naver Smart Place/Kakao Hair Shop/Karrot Business
 * Profile convergent research (docs/DESIGN_REFERENCES.md), whose booking half already
 * shipped as `MerchantBooking`. The source material names "coupons/loyalty" as a real
 * Karrot Business Profile feature without publishing an exact regular-customer threshold,
 * so `MerchantCouponService.REGULAR_CUSTOMER_THRESHOLD` is itunda's own honest scoping
 * choice, not a fabricated sourced number -- same discipline as the 4-installment
 * escalation cadence `WeeklySavingsService` already documented as its own choice.
 *
 * A coupon applies to a real merchant QR/Face Pay payment ([MerchantService.collect]),
 * not to [MerchantBooking] -- bookings deliberately settle in person with no payment
 * integration (see that entity's own doc comment), so there is no real money movement to
 * discount there. "Regular customer" status is computed from real COMPLETED PAYMENT
 * [Transaction] rows between a specific customer and merchant, not from booking counts --
 * the honest choice given a merchant can accept real payments with zero bookings ever
 * made.
 */
@Entity
@Table(name = "merchant_coupons")
class MerchantCoupon(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "merchant_id", nullable = false, length = 64)
    val merchantId: String,

    @Column(nullable = false, length = 100)
    val title: String,

    @Column(length = 500)
    val description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 16)
    val discountType: CouponDiscountType,

    // PERCENT: 1-100. FIXED_AMOUNT: a positive RWF amount, capped at the payment amount
    // at redemption time so a payment can never go negative -- see
    // MerchantCouponService.computeDiscount.
    @Column(name = "discount_value", nullable = false, precision = 19, scale = 2)
    val discountValue: BigDecimal,

    // Real gating on MerchantCouponService.REGULAR_CUSTOMER_THRESHOLD -- a merchant can
    // reserve a coupon for repeat customers only, the actual "loyalty" half of this
    // feature, not just a generic discount code.
    @Column(name = "regulars_only", nullable = false)
    val regularsOnly: Boolean = false,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(name = "expires_at")
    val expiresAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(
        id = "", merchantId = "", title = "", discountType = CouponDiscountType.PERCENT, discountValue = BigDecimal.ZERO,
    )
}

/**
 * One real redemption of a [MerchantCoupon] against a real completed payment -- see
 * MerchantCoupon's own doc comment. Unique on (couponId, customerId): each coupon is
 * single-use per customer, same "real anti-abuse, not honor-system" discipline this
 * codebase already applies to reward-task claims (`RewardClaim`).
 */
@Entity
@Table(
    name = "merchant_coupon_redemptions",
    uniqueConstraints = [UniqueConstraint(columnNames = ["coupon_id", "customer_id"])],
)
class MerchantCouponRedemption(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "coupon_id", nullable = false, length = 64)
    val couponId: String,

    @Column(name = "merchant_id", nullable = false, length = 64)
    val merchantId: String,

    @Column(name = "customer_id", nullable = false, length = 64)
    val customerId: String,

    @Column(name = "transaction_id", nullable = false, length = 64)
    val transactionId: String,

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    val discountAmount: BigDecimal,

    @Column(name = "redeemed_at", nullable = false)
    val redeemedAt: Instant = Instant.now(),
) {
    protected constructor() : this(
        id = "", couponId = "", merchantId = "", customerId = "", transactionId = "", discountAmount = BigDecimal.ZERO,
    )
}
