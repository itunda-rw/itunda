package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A real Marketplace listing wishlist entry (2026-07-21) -- the real 관심목록/wishlist
 * Karrot's own real product gives secondhand listings, mirroring [ProductFavorite]'s
 * exact shape (just a listing instead of a product). Deliberately minimal: the (user,
 * listing) pair and when it was saved, nothing else. Real DB unique constraint on
 * (user_id, listing_id) backs the same application-level "add is idempotent" check
 * `ListingFavoriteService.addFavorite` makes.
 */
@Entity
@Table(name = "listing_favorites")
class ListingFavorite(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "listing_id", nullable = false, length = 64)
    val listingId: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", listingId = "")
}
