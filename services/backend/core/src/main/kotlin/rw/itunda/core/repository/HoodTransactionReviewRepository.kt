package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.HoodTransactionReview
import rw.itunda.core.domain.HoodTransactionType

interface HoodTransactionReviewRepository : JpaRepository<HoodTransactionReview, String> {
    fun findByTransactionTypeAndTransactionId(transactionType: HoodTransactionType, transactionId: String): List<HoodTransactionReview>

    fun existsByTransactionTypeAndTransactionIdAndReviewerId(
        transactionType: HoodTransactionType,
        transactionId: String,
        reviewerId: String,
    ): Boolean

    // Real public "good points" aggregate (2026-07-24) -- backs a user's own public
    // review summary; only ever reads goodPoints (uncomfortablePoints stay private to
    // the two parties, see HoodReviewService's own doc comment).
    fun findByRevieweeId(revieweeId: String): List<HoodTransactionReview>

    fun countByRevieweeIdAndGoodPointsNot(revieweeId: String, emptyValue: String): Long
}
