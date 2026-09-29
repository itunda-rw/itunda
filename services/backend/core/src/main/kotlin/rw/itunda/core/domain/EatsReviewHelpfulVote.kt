package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/** A real (review, user) helpful vote -- DB-unique so a concurrent double-tap can't
 * create two rows even without the app-level idempotency check, same discipline
 * `ListingLike`/`CommunityLike` already establish for their own pair. */
@Entity
@Table(name = "eats_review_helpful_votes", uniqueConstraints = [UniqueConstraint(columnNames = ["review_id", "user_id"])])
class EatsReviewHelpfulVote(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "review_id", nullable = false, length = 64)
    val reviewId: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", reviewId = "", userId = "")
}
