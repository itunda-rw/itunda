package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A real "이 글 숨기기" (hide this post) entry (2026-08-24) -- see
 * `ListingHideService`'s own doc comment for the full sourced Karrot account.
 * Deliberately minimal, mirroring [ListingFavorite]'s exact shape: the (user,
 * listing) pair and when it was hidden, nothing else. Real DB unique constraint on
 * (user_id, listing_id) backs the same application-level "hide is idempotent" check
 * `ListingHideService.hideListing` makes.
 */
@Entity
@Table(name = "listing_hides")
class ListingHide(
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
