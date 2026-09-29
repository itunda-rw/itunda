package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.ListingLike

interface ListingLikeRepository : JpaRepository<ListingLike, String> {
    fun findByListingIdAndUserId(listingId: String, userId: String): ListingLike?

    // Real batch "which of these listings has this viewer already liked" (2026-08-03) --
    // same one-query-not-N discipline trustScores()/ListingFavoriteService's own batch
    // resolve already established, so rendering a page of listings never issues one
    // like-lookup query per row.
    @Query("SELECT l.listingId FROM ListingLike l WHERE l.listingId IN :listingIds AND l.userId = :userId")
    fun findLikedListingIds(@Param("listingIds") listingIds: Collection<String>, @Param("userId") userId: String): List<String>
}
