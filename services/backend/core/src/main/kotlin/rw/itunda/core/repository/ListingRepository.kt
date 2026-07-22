package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.Listing
import rw.itunda.core.domain.ListingStatus

interface ListingRepository : JpaRepository<Listing, String> {
    // Real pagination from day one (see MessagingRepositories.kt's own note on why --
    // this session's earlier Partner SDK finding: retrofitting pagination onto an
    // already-shipped unbounded endpoint is real, avoidable extra work).
    fun findByStatusOrderByCreatedAtDesc(status: ListingStatus, pageable: Pageable): Page<Listing>
    fun findByStatusAndCategoryOrderByCreatedAtDesc(status: ListingStatus, category: String, pageable: Pageable): Page<Listing>
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
}
