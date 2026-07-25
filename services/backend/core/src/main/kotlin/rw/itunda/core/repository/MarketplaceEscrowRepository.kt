package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.MarketplaceEscrow
import rw.itunda.core.domain.MarketplaceEscrowStatus

interface MarketplaceEscrowRepository : JpaRepository<MarketplaceEscrow, String> {
    fun findByListingId(listingId: String): MarketplaceEscrow?
    fun findByStatusOrderByCreatedAtAsc(status: MarketplaceEscrowStatus): List<MarketplaceEscrow>
}
