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

/**
 * A real 당근-style price offer on a real [PropertyListing] -- the one domain among the
 * three new neighborhood-services products (Community, Jobs, Real Estate) where real
 * price-haggling genuinely belongs (unlike a job's fixed pay rate). Mirrors
 * `PriceOffer`'s own exact design field-for-field, built on top of the real 1:1
 * conversation `PropertyPriceOfferService.makeOffer` establishes via
 * `PropertyListingService.contactLister`'s same `MessagingService` call.
 *
 * Reuses [PriceOfferStatus] directly (PENDING/ACCEPTED/REJECTED/COUNTERED is a real,
 * domain-generic negotiation-state shape, not marketplace-specific) rather than
 * declaring a duplicate enum. [proposedByUserId] is the inquirer for the original offer
 * and the lister for a counter-offer; a counter is a brand-new row, not an edit, so the
 * full negotiation history survives. Honestly scoped exactly like `PriceOffer`:
 * accepting only marks the negotiation agreed in the conversation -- no account money
 * moves, no listing status changes automatically. A lister who wants to close the
 * listing still uses the existing real `PropertyListingService.markTaken`, unmodified.
 */
@Entity
@Table(name = "property_price_offers")
class PropertyPriceOffer(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "property_listing_id", nullable = false, length = 64)
    val propertyListingId: String,

    @Column(name = "message_id", nullable = false, length = 64)
    val messageId: String,

    @Column(name = "conversation_id", nullable = false, length = 64)
    val conversationId: String,

    @Column(name = "inquirer_id", nullable = false, length = 64)
    val inquirerId: String,

    @Column(name = "lister_id", nullable = false, length = 64)
    val listerId: String,

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

    // Real optimistic lock (2026-08-02) -- mirrors PriceOffer.version's own doc
    // comment; respond flow reads-then-mutates status with no concurrency guard.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(
        id = "", propertyListingId = "", messageId = "", conversationId = "", inquirerId = "", listerId = "", proposedByUserId = "", amount = BigDecimal.ZERO,
    )
}
