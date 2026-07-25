package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.Listing
import rw.itunda.core.domain.ListingStatus
import java.time.Instant

interface ListingRepository : JpaRepository<Listing, String> {
    // Real pagination from day one (see MessagingRepositories.kt's own note on why --
    // this session's earlier Partner SDK finding: retrofitting pagination onto an
    // already-shipped unbounded endpoint is real, avoidable extra work).
    fun findByStatusOrderByCreatedAtDesc(status: ListingStatus, pageable: Pageable): Page<Listing>
    fun findByStatusAndCategoryOrderByCreatedAtDesc(status: ListingStatus, category: String, pageable: Pageable): Page<Listing>

    // Real seller-paid sponsored placement (2026-07-25) -- see MarketplaceService
    // .boostListing's own doc comment. A currently-boosted (paid, not-yet-expired)
    // listing sorts first, ties broken by createdAt desc same as the unboosted browse
    // queries above -- this is the only real ranking signal `browse()` uses now, not a
    // separate endpoint, since real Coupang/Baemin sponsored placement always surfaces
    // inside the same search results a buyer already sees, never a separate feed.
    @Query(
        "SELECT l FROM Listing l WHERE l.status = :status " +
            "ORDER BY CASE WHEN l.boostedUntil IS NOT NULL AND l.boostedUntil > :now THEN 0 ELSE 1 END, l.createdAt DESC",
    )
    fun findByStatusOrderByBoostedThenCreatedAtDesc(@Param("status") status: ListingStatus, @Param("now") now: Instant, pageable: Pageable): Page<Listing>

    @Query(
        "SELECT l FROM Listing l WHERE l.status = :status AND l.category = :category " +
            "ORDER BY CASE WHEN l.boostedUntil IS NOT NULL AND l.boostedUntil > :now THEN 0 ELSE 1 END, l.createdAt DESC",
    )
    fun findByStatusAndCategoryOrderByBoostedThenCreatedAtDesc(
        @Param("status") status: ListingStatus,
        @Param("category") category: String,
        @Param("now") now: Instant,
        pageable: Pageable,
    ): Page<Listing>
    fun findBySellerIdOrderByCreatedAtDesc(sellerId: String, pageable: Pageable): Page<Listing>

    // Real proximity search input (2026-07-18) -- see MarketplaceService.nearby. No real
    // geospatial index (e.g. MySQL spatial types) exists yet, so distance is computed in
    // application code via GeoUtils.haversineKm over this bounded candidate set -- fine
    // at current listing volumes, a named follow-up once volume grows.
    fun findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(status: ListingStatus): List<Listing>

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see MarketplaceService.
    // myNeighborhood and User.neighborhood's own doc comments.
    fun findByStatusAndNeighborhoodOrderByCreatedAtDesc(status: ListingStatus, neighborhood: String, pageable: Pageable): Page<Listing>
    fun findByStatusAndNeighborhoodAndCategoryOrderByCreatedAtDesc(
        status: ListingStatus,
        neighborhood: String,
        category: String,
        pageable: Pageable,
    ): Page<Listing>

    // Real Karrot-Score-style trust badge input (2026-07-21) -- see
    // rw.itunda.core.trust.TrustScoreService's own doc comment. A cheap COUNT query, not
    // a full listing load, since only the count of the seller's own completed (SOLD)
    // listings matters for the score.
    fun countBySellerIdAndStatus(sellerId: String, status: ListingStatus): Long

    // Real "My purchases" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
    // recommendation #6: Karrot's real screen splits a user's own activity into
    // labeled sales/purchases/wishlist tabs instead of one flat list, "specifically to
    // avoid one overloaded list mixing different user intents." itunda already had
    // sales (getMyListings) and wishlist separately -- this is the first time
    // "purchases" is even queryable, now that buyerId is captured (see Listing.kt's
    // own doc comment on why that was the real structural blocker until now).
    fun findByBuyerIdOrderByCreatedAtDesc(buyerId: String, pageable: Pageable): Page<Listing>
}
