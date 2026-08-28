package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * A real 살아본 후기 (Karrot "lived here" review) -- a public review of what it's
 * like to LIVE in a neighborhood, genuinely distinct from `HoodTransactionReview`
 * (that's a buyer/seller transaction review). No FK to a neighborhoods table exists
 * anywhere in this backend (`User.neighborhood` is itself a plain reverse-geocoded
 * string, not a foreign key) -- matches that same convention rather than inventing a
 * new neighborhoods table this pass doesn't otherwise need.
 *
 * Real DB-enforced one-review-per-user-per-neighborhood constraint (see
 * `NeighborhoodReviewService.submitReview`'s own doc comment) -- a person can review
 * a neighborhood they've genuinely lived in once, not spam it.
 */
@Entity
@Table(name = "neighborhood_reviews", uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "neighborhood"])])
class NeighborhoodReview(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(nullable = false, length = 120)
    val neighborhood: String,

    @Column(name = "residency_years", nullable = true)
    var residencyYears: Int? = null,

    @Column(nullable = false, length = 1000)
    var body: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", neighborhood = "", body = "")
}
