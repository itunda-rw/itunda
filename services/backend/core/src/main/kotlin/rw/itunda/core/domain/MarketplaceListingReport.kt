package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

enum class MarketplaceReportReason { SCAM, PROHIBITED_ITEM, INAPPROPRIATE, SPAM_OR_DUPLICATE, OTHER }

/**
 * Real 당근마켓 (Karrot) 신고하기 (report a listing) -- sourced from Karrot's own real,
 * documented moderation behavior (multiple real seller threads on daangn.com/kr/community
 * describe it): when a listing accumulates reports for suspected commercial/prohibited
 * selling, Karrot's own system automatically hides it from the marketplace, with no
 * notification to the seller about the action at all. `MarketplaceService.reportListing`
 * mirrors this honestly -- once a listing collects `REPORT_THRESHOLD` distinct reporters,
 * its status flips straight to the pre-existing `ListingStatus.REMOVED` (the same real
 * effect a seller's own manual removal already has: gone from browse/search/nearby/
 * pay-escrow), and deliberately sends no notification to the seller, matching the
 * sourced real silence rather than inventing a friendlier flow Karrot's own product
 * doesn't have.
 *
 * DB-unique on (listing, reporter) -- one real report per person per listing, same
 * concurrency-safe discipline `ListingLike`'s own doc comment already establishes for
 * this exact (listing, user) shape.
 */
@Entity
@Table(name = "marketplace_listing_reports", uniqueConstraints = [UniqueConstraint(columnNames = ["listing_id", "reporter_id"])])
class MarketplaceListingReport(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "listing_id", nullable = false, length = 64)
    val listingId: String,

    @Column(name = "reporter_id", nullable = false, length = 64)
    val reporterId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    val reason: MarketplaceReportReason,

    @Column(nullable = true, length = 500)
    val details: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", listingId = "", reporterId = "", reason = MarketplaceReportReason.OTHER)
}
