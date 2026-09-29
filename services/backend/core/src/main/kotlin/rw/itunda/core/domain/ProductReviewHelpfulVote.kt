package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/** A real (review, user) helpful vote on a [ProductReview] -- mirrors
 * [EatsReviewHelpfulVote] exactly, same DB-unique concurrency guard. */
@Entity
@Table(name = "product_review_helpful_votes", uniqueConstraints = [UniqueConstraint(columnNames = ["review_id", "user_id"])])
class ProductReviewHelpfulVote(
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
