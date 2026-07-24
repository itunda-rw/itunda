package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class HoodTransactionType { LISTING, JOB_POST, PROPERTY_LISTING }

/**
 * A real post-transaction review with Karrot's own asymmetric public/private
 * visibility -- closes docs/DESIGN_REFERENCES.md Section 4 recommendation #2: itunda
 * has star reviews for Shop/Eats but Marketplace/Jobs/Property had zero review step,
 * "mark sold" just flipped status. Deliberately a preset checklist
 * (`HoodReviewService.GOOD_POINTS`/`UNCOMFORTABLE_POINTS`), not free text, mirroring
 * Karrot's own real review UX (docs/DESIGN_REFERENCES.md's Hood section: "Post-
 * transaction review is a preset checklist... 'good points' shown publicly,
 * 'uncomfortable points' kept private between the two parties -- deliberate asymmetric
 * visibility to avoid public-negative-review churn").
 *
 * One shared entity/table across all three Hood verticals rather than three near-
 * identical ones -- same "more than one module needs this" reasoning
 * `TrustScoreService`'s own doc comment already established for living in `:core`.
 * `transactionType`/`transactionId` together identify the real completed transaction
 * (a `Listing.SOLD`, `JobPost.FILLED`, or `PropertyListing.TAKEN` row) this review is
 * about; `reviewerId`/`revieweeId` are the two real parties to that transaction,
 * resolved from the transaction's own `sellerId`/`buyerId` (or `posterId`/`workerId`,
 * or `listerId`/`counterpartyId`) pair -- see `HoodReviewService.resolveParties`'s own
 * doc comment for why this is what makes the whole system non-exploitable: a review
 * can only be submitted by, and about, someone who was a REAL recorded party to that
 * specific transaction, never an arbitrary user.
 *
 * `goodPoints`/`uncomfortablePoints` are pipe-separated preset tag ids (never free
 * text, matching Karrot's real UX) -- a plain VARCHAR rather than a join table, same
 * "keep it simple, this isn't a hot query path" discipline already applied elsewhere
 * in this schema where a small enumerated set doesn't need real relational modeling.
 */
@Entity
@Table(name = "hood_transaction_reviews")
class HoodTransactionReview(
    @Id
    @Column(length = 64)
    val id: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 32)
    val transactionType: HoodTransactionType,

    @Column(name = "transaction_id", nullable = false, length = 64)
    val transactionId: String,

    @Column(name = "reviewer_id", nullable = false, length = 64)
    val reviewerId: String,

    @Column(name = "reviewee_id", nullable = false, length = 64)
    val revieweeId: String,

    @Column(name = "good_points", nullable = false, length = 500)
    val goodPoints: String,

    @Column(name = "uncomfortable_points", nullable = false, length = 500)
    val uncomfortablePoints: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(
        id = "", transactionType = HoodTransactionType.LISTING, transactionId = "",
        reviewerId = "", revieweeId = "", goodPoints = "", uncomfortablePoints = "",
    )

    fun goodPointList(): List<String> = goodPoints.split("|").filter { it.isNotBlank() }
    fun uncomfortablePointList(): List<String> = uncomfortablePoints.split("|").filter { it.isNotBlank() }
}
