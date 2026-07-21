package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A real 당근부동산 property-listing wishlist entry (2026-07-22) -- closes the same
 * `docs/DESIGN_REFERENCES.md`-named gap as [JobPostFavorite]: Marketplace listings
 * already got a real wishlist (2026-07-21, `ListingFavorite`) but Property never did.
 * Mirrors `ListingFavorite`'s exact shape. Real DB unique constraint on (user_id,
 * property_listing_id) backs the same application-level "add is idempotent" check
 * `PropertyListingFavoriteService.addFavorite` makes.
 */
@Entity
@Table(name = "property_listing_favorites")
class PropertyListingFavorite(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "property_listing_id", nullable = false, length = 64)
    val propertyListingId: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", propertyListingId = "")
}
