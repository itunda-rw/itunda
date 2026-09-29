package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant

enum class PriceOfferStatus { PENDING, ACCEPTED, REJECTED, COUNTERED }

/**
 * A real 당근마켓-style price offer on a real [Listing] -- the last item on the Talk
 * polish roadmap ("당근-style 'message seller with a price offer' negotiation"), built
 * on top of the real 1:1 conversation `MarketplaceService.contactSeller` already
 * establishes between a buyer and a listing's seller.
 *
 * Each row is one real proposed amount, tied to exactly one real chat [Message] so the
 * offer renders inline in the same real conversation thread rather than as a separate
 * surface -- [proposedByUserId] is the buyer for the original offer and the seller for
 * a counter-offer. A counter is a brand-new row, not an edit of the original (the
 * original flips to [PriceOfferStatus.COUNTERED]), so the full negotiation history
 * survives and each amount can be traced back to its own real message.
 *
 * Honestly scoped: accepting an offer only marks it real-agreed in the conversation --
 * no account money moves, no listing status changes automatically. Real 당근마켓 itself
 * treats price agreement and the actual in-person/off-platform exchange as separate
 * steps; a seller who wants to close the listing still uses the existing real
 * `MarketplaceService.markSold`, unmodified by this feature.
 */
@Entity
@Table(name = "price_offers")
class PriceOffer(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "listing_id", nullable = false, length = 64)
    val listingId: String,

    @Column(name = "message_id", nullable = false, length = 64)
    val messageId: String,

    @Column(name = "conversation_id", nullable = false, length = 64)
    val conversationId: String,

    @Column(name = "buyer_id", nullable = false, length = 64)
    val buyerId: String,

    @Column(name = "seller_id", nullable = false, length = 64)
    val sellerId: String,

    @Column(name = "proposed_by_user_id", nullable = false, length = 64)
    val proposedByUserId: String,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: PriceOfferStatus = PriceOfferStatus.PENDING,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "responded_at")
    var respondedAt: Instant? = null,

    // Real optimistic lock (2026-08-02) -- respondToOffer reads-then-mutates status
    // with no concurrency guard; two concurrent responses to the same PENDING offer
    // (e.g. accept racing a counter) could both read PENDING and both win.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(
        id = "", listingId = "", messageId = "", conversationId = "", buyerId = "", sellerId = "", proposedByUserId = "", amount = BigDecimal.ZERO,
    )
}
