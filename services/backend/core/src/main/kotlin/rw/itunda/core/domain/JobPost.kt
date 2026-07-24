package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

enum class JobPostStatus { OPEN, FILLED, REMOVED }
enum class JobPayType { HOURLY, FIXED }

/**
 * A real 당근알바 (Danggeun/Karrot "Alba"/part-time-job board)-style local job posting --
 * the second of the three explicitly-named 당근-style neighborhood-services products
 * (alongside the already-real 당근마켓/`Listing` and 당근생활/`CommunityPost`). A real
 * local gig/part-time-job listing, not a marketplace item: no price/buy/mark-sold
 * semantics, a real hourly-or-fixed pay rate instead, and "message poster" (reusing
 * `MessagingService`, same as Marketplace's `contactSeller`) IS the real apply
 * mechanism -- matches real 당근알바's own UX, where messaging the poster is literally
 * how a worker applies, no separate structured "application" object needed for v1.
 *
 * Deliberately its own entity rather than widening `Listing` with a type discriminator
 * -- the two domains have genuinely different real actions (browse-and-buy vs
 * browse-and-apply, mark-sold vs mark-filled, no price-offer-negotiation equivalent for
 * a job), matching this project's own established precedent of separate modules for
 * distinct capabilities even where the shape overlaps (Commerce vs Eats).
 *
 * Real optional lat/lng, same `GeoUtils` foundation Marketplace/Community/Eats already
 * use -- a real "near me" filter matters for local gigs (delivery, cleaning) the same
 * way it does for marketplace items.
 */
@Entity
@Table(name = "job_posts")
class JobPost(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "poster_id", nullable = false, length = 64)
    val posterId: String,

    @Column(nullable = false, length = 32)
    var category: String,

    @Column(nullable = false, length = 200)
    var title: String,

    @Column(nullable = false, length = 2000)
    var description: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_type", nullable = false, length = 16)
    var payType: JobPayType,

    @Column(name = "pay_amount", nullable = false, precision = 18, scale = 2)
    var payAmount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: JobPostStatus = JobPostStatus.OPEN,

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

    // Real optional worker identification at mark-filled time (2026-07-24) -- see
    // Listing.buyerId's own doc comment for the full account; identical shape here.
    @Column(name = "worker_id", nullable = true, length = 64)
    var workerId: String? = null,
) {
    protected constructor() : this(
        id = "", posterId = "", category = "", title = "", description = "",
        payType = JobPayType.FIXED, payAmount = BigDecimal.ZERO,
    )
}
