package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Real 쿠팡파트너스 (Coupang Partners)-style affiliate link -- any itunda user
 * self-generates a trackable link for any real Shop product and earns a real
 * commission on any resulting purchase, sourced from Coupang's own real, live
 * program (any user signs up, posts a link, earns ~3% commission on sales through
 * it, paid periodically with a real-time earnings dashboard). Same real light
 * self-service opt-in every other referrer/operator role in this backend already
 * establishes (`RideDriverService.register`, `DesignatedDriverService.register`) --
 * no approval gate, no KYB check.
 *
 * `code` is a short, unique, real trackable identifier (not a full URL -- the
 * client builds the shareable link by embedding this code as a query param,
 * matching how a real short-link affiliate program works). `clickCount` is
 * best-effort, incremented whenever the link is resolved -- purely informational,
 * never gates whether a commission is paid (a purchase without a prior recorded
 * click still earns a real commission; the click counter can't be perfectly
 * attributed across app/web boundaries, same honest limitation any real affiliate
 * tracker has for a first-party, cookie-less system).
 */
@Entity
@Table(name = "affiliate_links")
class AffiliateLink(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "product_id", nullable = false, length = 64)
    val productId: String,

    @Column(nullable = false, unique = true, length = 16)
    val code: String,

    @Column(name = "click_count", nullable = false)
    var clickCount: Long = 0,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", productId = "", code = "")
}

/**
 * One real commission earned when a real order was placed through an
 * [AffiliateLink]'s code. See [AffiliateLink]'s own doc comment for the full
 * sourced account. `commissionAmount` is a real 3% of the order's real total --
 * Coupang's own published regular-partner rate, funded from itunda's own
 * `FEE_REVENUE` house account (the same real economics a real affiliate program
 * uses: the platform funds the commission out of its own margin, not an extra
 * charge to the buyer or a cut from the merchant's own payout).
 */
@Entity
@Table(name = "affiliate_commissions")
class AffiliateCommission(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "link_id", nullable = false, length = 64)
    val linkId: String,

    @Column(name = "referrer_id", nullable = false, length = 64)
    val referrerId: String,

    @Column(name = "order_id", nullable = false, length = 64)
    val orderId: String,

    @Column(name = "buyer_id", nullable = false, length = 64)
    val buyerId: String,

    @Column(name = "commission_amount", nullable = false, precision = 18, scale = 2)
    val commissionAmount: java.math.BigDecimal,

    @Column(name = "payout_transaction_id", nullable = false, length = 64)
    val payoutTransactionId: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(
        id = "", linkId = "", referrerId = "", orderId = "", buyerId = "",
        commissionAmount = java.math.BigDecimal.ZERO, payoutTransactionId = "",
    )
}
