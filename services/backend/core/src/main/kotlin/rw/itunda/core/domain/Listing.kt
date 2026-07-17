package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.EnumType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

enum class ListingStatus { ACTIVE, SOLD, REMOVED }

/**
 * A real 당근마켓 (Danggeun/Karrot Market)-style secondhand listing -- the second of
 * the three new "super app" phases named in the 2026-07-18 goal expansion (after
 * Kakao-style messaging, built first specifically so this phase could reuse it for real
 * buyer/seller negotiation -- see MarketplaceService's own doc comment).
 *
 * Honestly, no real location/proximity data exists anywhere in this backend (`User`
 * has no address/district field) -- 당근마켓's actual defining feature is hyperlocal
 * discovery by neighborhood, which this can't build for real without inventing location
 * data that doesn't exist. Same "Rwanda adaptation" discipline this document already
 * uses elsewhere (e.g. Toss Shopping reusing itunda's own Merchant directory instead of
 * fabricating an external partner network): this is a real, working general marketplace
 * (list, browse, buy via real in-app chat) without the real proximity ranking a true
 * 당근마켓 clone would need -- an honest, named simplification, not a hidden one.
 */
@Entity
@Table(name = "listings")
class Listing(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "seller_id", nullable = false, length = 64)
    val sellerId: String,

    @Column(nullable = false)
    var title: String,

    @Column(nullable = false, length = 2000)
    var description: String,

    @Column(nullable = false, precision = 18, scale = 2)
    var price: BigDecimal,

    @Column(nullable = false, length = 64)
    var category: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: ListingStatus = ListingStatus.ACTIVE,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(
        id = "", sellerId = "", title = "", description = "", price = BigDecimal.ZERO, category = "",
    )
}
