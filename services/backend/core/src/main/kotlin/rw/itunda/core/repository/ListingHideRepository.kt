package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.ListingHide

interface ListingHideRepository : JpaRepository<ListingHide, String> {
    fun findByUserIdAndListingId(userId: String, listingId: String): ListingHide?

    fun deleteByUserIdAndListingId(userId: String, listingId: String): Long

    // Real "Hidden listings" list (Hood product-completeness pass, 2026-09-07) --
    // hideListing/unhideListing existed with no way to ever see or undo what was
    // hidden, on any of the 3 real clients that already call both. Same shape
    // ListingFavoriteRepository.findByUserIdOrderByCreatedAtDesc already establishes.
    fun findByUserIdOrderByCreatedAtDesc(userId: String, pageable: Pageable): Page<ListingHide>

    // Real hidden-listing-id set for the current browser -- fetched once per browse
    // call and passed to ListingRepository's own NOT IN queries, same "batch-resolve,
    // never per-row" discipline this codebase's other per-viewer state (e.g.
    // MarketplaceService.likedListingIds) already establishes.
    @Query("SELECT lh.listingId FROM ListingHide lh WHERE lh.userId = :userId")
    fun findListingIdsByUserId(@Param("userId") userId: String): List<String>
}
