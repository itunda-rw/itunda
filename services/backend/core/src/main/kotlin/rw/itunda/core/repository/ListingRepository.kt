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
    // Real bump-to-top input (2026-08-10, see MarketplaceService.bumpListing) --
    // COALESCE(l.bumpedAt, l.createdAt) is the same "effective recency" every real
    // 당근마켓 feed sorts by; a never-bumped listing (bumpedAt null) sorts by its real
    // createdAt exactly as before.
    @Query(
        "SELECT l FROM Listing l WHERE l.status = :status " +
            "ORDER BY CASE WHEN l.boostedUntil IS NOT NULL AND l.boostedUntil > :now THEN 0 ELSE 1 END, COALESCE(l.bumpedAt, l.createdAt) DESC",
    )
    fun findByStatusOrderByBoostedThenCreatedAtDesc(@Param("status") status: ListingStatus, @Param("now") now: Instant, pageable: Pageable): Page<Listing>

    @Query(
        "SELECT l FROM Listing l WHERE l.status = :status AND l.category = :category " +
            "ORDER BY CASE WHEN l.boostedUntil IS NOT NULL AND l.boostedUntil > :now THEN 0 ELSE 1 END, COALESCE(l.bumpedAt, l.createdAt) DESC",
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

    // Real dual-neighborhood support (2026-08-04) -- see User.secondNeighborhood's own
    // doc comment. `In` variants so a caller with a second neighborhood set sees listings
    // from either real place, not just their primary one. Converted from derived
    // queries to @Query (2026-08-10) so this feed -- the real default landing feed for
    // Hood, per SuperAppTabs.kt -- also respects a real bump, same as browse() above;
    // COALESCE isn't expressible via Spring Data's method-name convention.
    @Query("SELECT l FROM Listing l WHERE l.status = :status AND l.neighborhood IN :neighborhoods ORDER BY COALESCE(l.bumpedAt, l.createdAt) DESC")
    fun findByStatusAndNeighborhoodInOrderByCreatedAtDesc(
        @Param("status") status: ListingStatus,
        @Param("neighborhoods") neighborhoods: Collection<String>,
        pageable: Pageable,
    ): Page<Listing>

    @Query(
        "SELECT l FROM Listing l WHERE l.status = :status AND l.neighborhood IN :neighborhoods AND l.category = :category " +
            "ORDER BY COALESCE(l.bumpedAt, l.createdAt) DESC",
    )
    fun findByStatusAndNeighborhoodInAndCategoryOrderByCreatedAtDesc(
        @Param("status") status: ListingStatus,
        @Param("neighborhoods") neighborhoods: Collection<String>,
        @Param("category") category: String,
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

    // Real short-query fallback (2026-08-14) -- see FullTextSearchUtil's own doc
    // comment: MySQL's FULLTEXT structurally can't match anything under 3 characters,
    // so a plain substring match covers that case instead of silently returning
    // nothing (or everything).
    @Query("SELECT l FROM Listing l WHERE l.status = :status AND LOWER(l.title) LIKE LOWER(CONCAT('%', :q, '%'))")
    fun searchShort(@Param("status") status: ListingStatus, @Param("q") q: String, pageable: Pageable): Page<Listing>

    // Real relevance-ranked full-text search (2026-08-14) -- see
    // MerchantProductRepository.searchFullText's own doc comment for the full "why"
    // (a native query, not JPQL, since Hibernate has no portable MATCH...AGAINST
    // equivalent) and rw.itunda.core.search.FullTextSearchUtil for the boolean-mode
    // query string this expects as input.
    @Query(
        value = "SELECT l.* FROM listings l WHERE l.status = :#{#status.name()} " +
            "AND MATCH(l.title, l.description) AGAINST (:booleanQuery IN BOOLEAN MODE) " +
            "ORDER BY MATCH(l.title, l.description) AGAINST (:booleanQuery IN BOOLEAN MODE) DESC",
        countQuery = "SELECT COUNT(*) FROM listings l WHERE l.status = :#{#status.name()} " +
            "AND MATCH(l.title, l.description) AGAINST (:booleanQuery IN BOOLEAN MODE)",
        nativeQuery = true,
    )
    fun searchFullText(@Param("status") status: ListingStatus, @Param("booleanQuery") booleanQuery: String, pageable: Pageable): Page<Listing>

    // Real 조회수 (view count) increment (2026-08-16) -- an atomic JPQL bulk update,
    // same discipline EmailVerificationTokenRepository.invalidateUnusedByUserId already
    // establishes, so two concurrent detail-page views can never lose one increment to
    // a read-modify-write race on the fetched entity.
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Listing l SET l.viewCount = l.viewCount + 1 WHERE l.id = :id")
    fun incrementViewCount(@Param("id") id: String): Int
}
