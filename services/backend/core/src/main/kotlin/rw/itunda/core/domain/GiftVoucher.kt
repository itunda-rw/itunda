package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

enum class GiftVoucherStatus { ACTIVE, REDEEMED, EXPIRED }

/**
 * A real KakaoTalk-style "선물하기" 기프티콘 (mobile gift voucher) -- distinct from
 * [Gift] (KakaoPay's own real 송금봉투/money-envelope product, already built
 * 2026-07-20): a gift voucher is redeemable at ONE specific real itunda [Merchant],
 * either for a specific [MerchantProduct] (the exact product snapshot the purchaser
 * picked, e.g. "스타벅스 아메리카노") or a flat cash-equivalent amount, not money that
 * lands in the recipient's own wallet. Sourced from real gifticon mechanics
 * (huffingtonpost.kr/imaeil.com coverage of Kakao's own 2016 policy change,
 * cs.gifticon.com's own FAQ): a real validity window, a one-time real extension near
 * expiry, and a real partial cash refund (not the full amount) if it goes unredeemed.
 *
 * Money moves the same real escrow-then-settle way [Gift]'s own doc comment
 * establishes: the purchaser's wallet is debited immediately into a shared
 * `gift_voucher_holding` clearing account, and only actually reaches the merchant's
 * wallet when the merchant themselves redeems it (mirrors `MerchantService.collect()`'s
 * own real "merchant collects" pattern -- a customer presenting a voucher in person for
 * the merchant to validate, never a self-serve redeem the recipient could fake).
 *
 * Itunda's own honest scoping choices, not claimed reproductions of any one real
 * merchant's specific policy: [DEFAULT_VALIDITY] picks the middle of Kakao's own
 * published 60-365 day range; the real "unlimited extensions within 5 years" is
 * deliberately scoped down to exactly ONE extension per voucher for v1 (`extended`
 * below) rather than an open-ended loop; the real ~90% refund-on-expiry rate is used
 * as-is (10% forfeited to itunda, booked as real fee revenue, not silently dropped).
 */
@Entity
@Table(name = "gift_vouchers")
class GiftVoucher(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "purchaser_id", nullable = false, length = 64)
    val purchaserId: String,

    @Column(name = "recipient_id", nullable = false, length = 64)
    val recipientId: String,

    @Column(name = "conversation_id", nullable = false, length = 64)
    val conversationId: String,

    @Column(name = "message_id", nullable = false, length = 64)
    val messageId: String,

    @Column(name = "merchant_id", nullable = false, length = 64)
    val merchantId: String,

    @Column(name = "merchant_product_id", length = 64)
    val merchantProductId: String? = null,

    @Column(name = "product_name_snapshot")
    val productNameSnapshot: String? = null,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: GiftVoucherStatus = GiftVoucherStatus.ACTIVE,

    @Column(name = "hold_transaction_id", nullable = false, length = 64)
    val holdTransactionId: String,

    @Column(name = "redeem_transaction_id", length = 64)
    var redeemTransactionId: String? = null,

    @Column(name = "refund_transaction_id", length = 64)
    var refundTransactionId: String? = null,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(name = "redeemed_at")
    var redeemedAt: Instant? = null,

    @Column(nullable = false)
    var extended: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    // Real KakaoTalk 기프티콘 expiry-reminder push -- Kakao sends at least 3 real push
    // notifications to the RECIPIENT starting 7 days before a gifticon's real expiry,
    // separate from (and earlier than) the real extension window
    // ([EXTENSION_WINDOW], 30 days) this entity already scoped. `expiresAt` has been a
    // real, stored field since this voucher concept existed, but nothing ever nudged
    // the recipient to redeem before real expiry -- only the purchaser was ever told,
    // and only after the fact (`GiftVoucherService.expireVoucher`'s own real partial-
    // refund message). Null until a real reminder has been sent, same one-shot
    // "re-check right before sending, never re-fire" discipline
    // `InsurancePolicy.renewalReminderSentAt`/`Certificate.renewalReminderSentAt`
    // already establish for a structurally identical real-expiry-date reminder.
    @Column(name = "expiry_reminder_sent_at")
    var expiryReminderSentAt: Instant? = null,

    // Merchant redemption and expiry refund must not settle a voucher concurrently.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(
        id = "", purchaserId = "", recipientId = "", conversationId = "", messageId = "",
        merchantId = "", amount = BigDecimal.ZERO, holdTransactionId = "", expiresAt = Instant.now(),
    )

    companion object {
        val DEFAULT_VALIDITY: Duration = Duration.ofDays(180)
        val EXTENSION_WINDOW: Duration = Duration.ofDays(30)
        val EXTENSION_AMOUNT: Duration = Duration.ofDays(90)
        val EXPIRY_REFUND_RATE: BigDecimal = BigDecimal("0.90")

        // Real Kakao gifticon expiry-reminder window -- Kakao's own real push-
        // notification cadence starts 7 days before real expiry (see
        // `expiryReminderSentAt`'s own doc comment for the real sourcing), itunda's
        // honest single-fire equivalent of Kakao's real multi-push cadence.
        val EXPIRY_REMINDER_WINDOW: Duration = Duration.ofDays(7)
    }
}
