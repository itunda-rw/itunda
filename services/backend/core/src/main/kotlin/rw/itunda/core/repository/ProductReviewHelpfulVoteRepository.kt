package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.ProductReviewHelpfulVote

// Mirrors EatsReviewHelpfulVoteRepository exactly.
interface ProductReviewHelpfulVoteRepository : JpaRepository<ProductReviewHelpfulVote, String> {
    fun findByReviewIdAndUserId(reviewId: String, userId: String): ProductReviewHelpfulVote?

    // Real batch "which of these reviews has this viewer already marked helpful" -- one
    // query for a whole review page, not one helpful-lookup query per row. Same
    // discipline EatsReviewHelpfulVoteRepository.findVotedReviewIds already establishes.
    @Query("SELECT v.reviewId FROM ProductReviewHelpfulVote v WHERE v.reviewId IN :reviewIds AND v.userId = :userId")
    fun findVotedReviewIds(@Param("reviewIds") reviewIds: Collection<String>, @Param("userId") userId: String): List<String>
}
