package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.MarketplaceEscrow
import rw.itunda.core.domain.MarketplaceEscrowStatus

interface MarketplaceEscrowRepository : JpaRepository<MarketplaceEscrow, String> {
    fun findByListingId(listingId: String): MarketplaceEscrow?
    fun findByStatusOrderByCreatedAtAsc(status: MarketplaceEscrowStatus): List<MarketplaceEscrow>

    // Real scheduled auto-release-after-timeout sweep (2026-07-27) -- see
    // MarketplaceEscrowAutoReleaseScheduler's own doc comment. Coarse repo filter
    // (every real HELD escrow), exact due-or-not logic in the service, same discipline
    // this codebase's other scheduled sweeps already use.
    fun findByStatus(status: MarketplaceEscrowStatus): List<MarketplaceEscrow>
}
