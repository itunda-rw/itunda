package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.EatsReviewHelpfulVote

interface EatsReviewHelpfulVoteRepository : JpaRepository<EatsReviewHelpfulVote, String> {
    fun findByReviewIdAndUserId(reviewId: String, userId: String): EatsReviewHelpfulVote?

    // Real batch "which of these reviews has this viewer already marked helpful"
    // (2026-08-17) -- same one-query-not-N discipline ListingLikeRepository
    // .findLikedListingIds already establishes, so rendering a page of reviews never
    // issues one helpful-lookup query per row.
    @Query("SELECT v.reviewId FROM EatsReviewHelpfulVote v WHERE v.reviewId IN :reviewIds AND v.userId = :userId")
    fun findVotedReviewIds(@Param("reviewIds") reviewIds: Collection<String>, @Param("userId") userId: String): List<String>
}
