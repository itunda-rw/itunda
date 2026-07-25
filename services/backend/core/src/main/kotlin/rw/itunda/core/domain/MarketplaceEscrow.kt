package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
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
 * Honestly scoped v1: no scheduled auto-release-after-timeout job (a real, named
 * follow-up, same shape `Gift.EXPIRY`'s own auto-refund scheduler already proves is
 * buildable) -- a disputed trade is resolved by a real human admin via
 * `MarketplaceEscrowAdminController`, matching `PropertyOwnershipService`'s own real
 * human-review-queue precedent, not an automated resolution this backend has no real
 * fraud-detection system to drive safely.
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

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    protected constructor() : this(
        id = "", listingId = "", buyerId = "", sellerId = "", amount = BigDecimal.ZERO,
        fee = BigDecimal.ZERO, holdTransactionId = "",
    )
}
