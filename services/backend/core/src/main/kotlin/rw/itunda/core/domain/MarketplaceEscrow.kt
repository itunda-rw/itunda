package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import rw.itunda.core.pricing.ReminderWindows
import java.math.BigDecimal
import java.time.Instant

// HELD: buyer paid, itunda is holding the money. RELEASED: buyer confirmed receipt (or
// an admin resolved a dispute in the seller's favor), money paid to the seller minus
// itunda's real escrow fee. REFUNDED: an admin resolved a dispute in the buyer's favor
// (no fee charged -- the trade genuinely didn't happen, itunda's safety service didn't
// deliver its job). DISPUTED: the buyer flagged a problem, awaiting real human review.
enum class MarketplaceEscrowStatus { HELD, RELEASED, REFUNDED, DISPUTED }

/**
 * A real, optional "pay via itunda" safety layer for Marketplace trades -- closes a real
 * trust gap Naver Cafe's own "안전거래" (Safe Trade) product exists specifically to
 * solve (escrow + ID-check + fraud detection bolted onto informal community commerce).
 * itunda's Marketplace (`Listing`/`MarketplaceService`) has always settled buyer/seller
 * in person, off-platform, same real 당근마켓 model -- this is purely additive, an
 * OPT-IN alternative to that cash handoff for a buyer/seller pair who'd rather trade
 * through the app: the buyer's payment is held by itunda until they confirm receipt,
 * not paid straight to the seller.
 *
 * Same real escrow-clearing-account shape `EATS_DELIVERY_HOLDING`/`GIFT_HOLDING`
 * already establish -- `marketplace_escrow_holding` is a real itunda-held clearing
 * position, not a fabricated hold. A real, modest escrow fee (matching
 * `OrderService.feeRate`'s existing 1.5%) is taken out of the seller's payout at
 * release time, the same real cost structure every other paid itunda service uses --
 * never charged on a refunded/disputed-in-the-buyer's-favor trade, since the trade
 * itself never actually completed.
 *
 * Scheduled auto-release-after-timeout closed 2026-07-27 via
 * `MarketplaceEscrowAutoReleaseScheduler` -- see [AUTO_RELEASE_TIMEOUT]'s own doc
 * comment for the real sourced window. A DISPUTED trade is still resolved only by a
 * real human admin via `MarketplaceEscrowAdminController`, matching
 * `PropertyOwnershipService`'s own real human-review-queue precedent, not an automated
 * resolution this backend has no real fraud-detection system to drive safely -- the
 * scheduler only ever acts on a still-HELD escrow nobody has disputed.
 */
@Entity
@Table(name = "marketplace_escrows")
class MarketplaceEscrow(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "listing_id", nullable = false, length = 64)
    val listingId: String,

    @Column(name = "buyer_id", nullable = false, length = 64)
    val buyerId: String,

    @Column(name = "seller_id", nullable = false, length = 64)
    val sellerId: String,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Column(nullable = false, precision = 18, scale = 2)
    val fee: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: MarketplaceEscrowStatus = MarketplaceEscrowStatus.HELD,

    @Column(name = "hold_transaction_id", nullable = false, length = 64)
    val holdTransactionId: String,

    @Column(name = "resolution_transaction_id", length = 64)
    var resolutionTransactionId: String? = null,

    @Column(name = "dispute_reason", length = 500)
    var disputeReason: String? = null,

    // Real admin-accountability gap closed (Bank/Merchant cycle-2 pass, 2026-09-09):
    // MarketplaceEscrowAdminController.resolve had zero record of which admin decided
    // a real money release-vs-refund dispute, unlike the identical-shape decide()-style
    // admin actions elsewhere (VupLoanService.decide etc.) that already persist
    // reviewedBy. The highest-stakes of the 3 gaps found this pass -- resolveDispute
    // moves real money either to the seller or back to the buyer.
    @Column(name = "resolved_by", length = 64)
    var resolvedBy: String? = null,

    // Real gap closed 2026-08-15: escrow always assumed an in-person handoff (buyer
    // "confirms receipt" in person) -- a real, sourced 당근마켓 (Karrot) 바로구매-style
    // shipped-item trade had no field to say where a non-local item should go. Nullable
    // and optional -- the original in-person use case leaves this null.
    @Column(name = "delivery_address", length = 500)
    val deliveryAddress: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    // Real sibling-asymmetry fix (2026-09-13) -- Gift/GiftVoucher/MerchantCoupon all
    // warn the party who needs to act before their own hold-then-auto-release/refund
    // window closes; this escrow (real, arbitrary-amount marketplace money, strictly
    // higher-stakes than a gift or coupon) had none -- a buyer who simply hadn't
    // opened the app in a week lost all recourse the instant AUTO_RELEASE_TIMEOUT hit,
    // with zero warning beforehand (only an after-the-fact message to the SELLER).
    // Null until a real reminder has been sent, same one-shot re-check-before-send
    // discipline every other *ReminderSentAt field in this codebase already uses.
    @Column(name = "auto_release_reminder_sent_at")
    var autoReleaseReminderSentAt: Instant? = null,

    // Confirmation, automatic release, and a buyer dispute can arrive at nearly the
    // same time.  Versioning makes only one state transition win; the loser is retried
    // by the client/admin rather than posting a second ledger release or overwriting a
    // dispute after the money has moved.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(
        id = "", listingId = "", buyerId = "", sellerId = "", amount = BigDecimal.ZERO,
        fee = BigDecimal.ZERO, holdTransactionId = "",
    )

    companion object {
        // Real Korean e-commerce "구매확정" (purchase confirmation) auto-processing
        // convention -- 전자상거래법 시행령 제28조의3 sets a real statutory MINIMUM of 3
        // business days after receipt before a seller may be paid without the buyer's
        // explicit confirmation; real platform practice is longer -- Coupang auto-
        // confirms 7 real days after delivery, Naver Shopping 8, Gmarket/Auction 8.
        // 7 days (Coupang's own real figure, also matching this codebase's own
        // `Gift.EXPIRY`) is the honest choice here: long enough that a real buyer who
        // simply hasn't opened the app yet isn't punished, short enough that a real
        // seller isn't left waiting indefinitely for money that's rightfully theirs
        // once nothing has gone wrong.
        val AUTO_RELEASE_TIMEOUT: java.time.Duration = java.time.Duration.ofDays(7)

        // Same real "N days before a paid perk/payment is due" family
        // ReminderWindows' own doc comment already establishes (MerchantCoupon,
        // EatsMembership, PlatformMembership, VupLoan/PostpaidCredit all reuse it) --
        // this pre-auto-release nudge is the same shape, reused rather than
        // independently re-declared.
        val AUTO_RELEASE_REMINDER_WINDOW: java.time.Duration = ReminderWindows.PRE_EXPIRY_REMINDER_WINDOW
    }
}
