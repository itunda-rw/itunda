package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Restaurant-owned Eats stamp campaign. Campaigns are deliberately separate from
 * MerchantLoyaltyAccount: that balance is accrued through merchant payment collection,
 * while these stamps must be earned by qualifying Eats orders only.
 */
@Entity
@Table(name = "eats_stamp_campaigns")
class EatsStampCampaign(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "restaurant_id", nullable = false, length = 64)
    val restaurantId: String,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(name = "stamps_required", nullable = false)
    var stampsRequired: Int,

    @Column(name = "reward_description", nullable = false, length = 240)
    var rewardDescription: String,

    @Column(name = "reward_discount_amount", precision = 18, scale = 2)
    var rewardDiscountAmount: BigDecimal? = null,

    @Column(name = "starts_at", nullable = false)
    var startsAt: Instant,

    @Column(name = "ends_at")
    var endsAt: Instant? = null,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Version
    @Column(nullable = false)
    var version: Long = 0,
)

/**
 * One qualifying order earns at most one stamp per restaurant/local day. The database
 * uniqueness constraints are the final guard against duplicate delivery callbacks and
 * duplicate qualifying orders racing each other.
 */
@Entity
@Table(name = "eats_stamp_earnings")
class EatsStampEarning(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "campaign_id", nullable = false, length = 64)
    val campaignId: String,

    @Column(name = "restaurant_id", nullable = false, length = 64)
    val restaurantId: String,

    @Column(name = "buyer_id", nullable = false, length = 64)
    val buyerId: String,

    @Column(name = "order_id", nullable = false, length = 64)
    val orderId: String,

    @Column(name = "earning_day", nullable = false)
    val earningDay: LocalDate,

    @Column(name = "earned_at", nullable = false)
    val earnedAt: Instant = Instant.now(),

    @Column(name = "reversed_at")
    var reversedAt: Instant? = null,

    @Column(name = "reversal_reason", length = 240)
    var reversalReason: String? = null,
)

/**
 * A reward is a claimable entitlement, not a discount until a checkout redemption
 * succeeds. Unique campaign/buyer/threshold prevents retries from issuing it twice.
 */
@Entity
@Table(name = "eats_stamp_rewards")
class EatsStampReward(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "campaign_id", nullable = false, length = 64)
    val campaignId: String,

    @Column(name = "restaurant_id", nullable = false, length = 64)
    val restaurantId: String,

    @Column(name = "buyer_id", nullable = false, length = 64)
    val buyerId: String,

    @Column(name = "stamp_threshold", nullable = false)
    val stampThreshold: Int,

    @Column(name = "reward_description", nullable = false, length = 240)
    val rewardDescription: String,

    @Column(name = "discount_amount", precision = 18, scale = 2)
    val discountAmount: BigDecimal? = null,

    @Column(nullable = false, length = 16)
    var status: String = "AVAILABLE",

    @Column(name = "issued_at", nullable = false)
    val issuedAt: Instant = Instant.now(),

    @Column(name = "expires_at")
    var expiresAt: Instant? = null,

    @Column(name = "redeemed_at")
    var redeemedAt: Instant? = null,

    @Column(name = "redeemed_order_id", length = 64)
    var redeemedOrderId: String? = null,

    @Version
    @Column(nullable = false)
    var version: Long = 0,
)
