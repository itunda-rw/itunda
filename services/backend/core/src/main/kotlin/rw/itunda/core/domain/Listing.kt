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
 * Real optional lat/lng (2026-07-18, see below) now closes the hyperlocal-discovery gap
 * this doc comment used to name as impossible -- `MarketplaceService.nearby` ranks by
 * real Haversine distance for any listing whose seller chose to set a location. `User`
 * still has no address/district field, so a listing without coordinates simply doesn't
 * appear in proximity results -- an honest, named fallback, not a hidden one.
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

    // Real lat/lng (2026-07-18) -- the location data this class's own doc comment
    // originally named as not existing anywhere in this backend now exists, real and
    // optional. See rw.itunda.core.geo.GeoUtils and MarketplaceService.nearby for the
    // real proximity search this unlocks. A listing without coordinates simply doesn't
    // appear in proximity results, same honest fallback as Merchant's own fields.
    @Column(nullable = true)
    var latitude: Double? = null,

    @Column(nullable = true)
    var longitude: Double? = null,
) {
    protected constructor() : this(
        id = "", sellerId = "", title = "", description = "", price = BigDecimal.ZERO, category = "",
    )
}
