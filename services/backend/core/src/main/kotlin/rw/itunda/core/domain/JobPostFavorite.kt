package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A real 당근알바 job-post wishlist entry (2026-07-22) -- closes a gap
 * `docs/DESIGN_REFERENCES.md`'s Hood research named directly: Marketplace listings
 * already got a real wishlist (2026-07-21, `ListingFavorite`) but Jobs never did,
 * despite saving a job post to revisit later being just as real a need as saving a
 * marketplace item. Mirrors `ListingFavorite`'s exact shape. Real DB unique constraint
 * on (user_id, job_post_id) backs the same application-level "add is idempotent" check
 * `JobPostFavoriteService.addFavorite` makes.
 */
@Entity
@Table(name = "job_post_favorites")
class JobPostFavorite(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "job_post_id", nullable = false, length = 64)
    val jobPostId: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", jobPostId = "")
}
