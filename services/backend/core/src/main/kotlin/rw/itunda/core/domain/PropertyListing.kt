package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

enum class PropertyListingStatus { AVAILABLE, TAKEN, REMOVED }
enum class PropertyListingType { SALE, RENT }

/**
 * A real 당근부동산 (Danggeun/Karrot "Real Estate")-style property listing -- the third
 * and last of the three explicitly-named 당근-style neighborhood-services products
 * (alongside the already-real 당근마켓/`Listing` and 당근생활/`CommunityPost`,
 * 당근알바/`JobPost`).
 *
 * Closer in shape to `Listing` than `JobPost` was (a real property IS "an item for
 * sale/rent" -- price, mark-taken, real haggling is genuinely common) but still kept as
 * its own entity rather than widening `Listing`: real-estate-specific fields
 * (`propertyType`, `listingType`, `bedrooms`, `sizeSqm`) would otherwise pollute
 * `Listing`'s general secondhand-goods schema with columns only ever populated for one
 * narrow category of item, and `status` semantics genuinely differ (TAKEN, not SOLD --
 * a rental isn't "sold"). Matches this project's established precedent of separate
 * modules for structurally-similar-but-semantically-distinct capabilities.
 *
 * `price` is the sale price for `SALE` or the monthly rent amount for `RENT` -- a single
 * field, disambiguated by `listingType`, mirroring how `JobPost.payAmount` is
 * disambiguated by `payType`.
 */
@Entity
@Table(name = "property_listings")
class PropertyListing(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "lister_id", nullable = false, length = 64)
    val listerId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "listing_type", nullable = false, length = 16)
    var listingType: PropertyListingType,

    @Column(name = "property_type", nullable = false, length = 32)
    var propertyType: String,

    @Column(nullable = false, length = 200)
    var title: String,

    @Column(nullable = false, length = 2000)
    var description: String,

    @Column(nullable = false, precision = 18, scale = 2)
    var price: BigDecimal,

    @Column(nullable = true)
    var bedrooms: Int? = null,

    @Column(name = "size_sqm", nullable = true)
    var sizeSqm: Double? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: PropertyListingStatus = PropertyListingStatus.AVAILABLE,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(nullable = true)
    var latitude: Double? = null,

    @Column(nullable = true)
    var longitude: Double? = null,

    // Real hyperlocal neighborhood (2026-07-20) -- see Listing.neighborhood's own doc
    // comment for the full account; identical cached-at-creation shape here.
    @Column(nullable = true, length = 120)
    var neighborhood: String? = null,

    // Real optional buyer/tenant identification at mark-taken time (2026-07-24) -- see
    // Listing.buyerId's own doc comment for the full account; identical shape here.
    // Named "counterparty" rather than "buyer" since RENT's other party is a tenant,
    // not a buyer.
    @Column(name = "counterparty_id", nullable = true, length = 64)
    var counterpartyId: String? = null,

    // Real ownership verification (2026-07-25) -- NONE/PENDING/VERIFIED, driven by
    // PropertyOwnershipSubmission's real submit/review workflow. See that entity's own
    // doc comment for why this is document-upload + human-review, not an automated
    // registry check.
    @Column(name = "ownership_verification_status", nullable = false, length = 16)
    var ownershipVerificationStatus: String = "NONE",
) {
    protected constructor() : this(
        id = "", listerId = "", listingType = PropertyListingType.RENT, propertyType = "",
        title = "", description = "", price = BigDecimal.ZERO,
    )
}
