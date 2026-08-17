package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.MarketplaceListingReport

interface MarketplaceListingReportRepository : JpaRepository<MarketplaceListingReport, String> {
    fun findByListingIdAndReporterId(listingId: String, reporterId: String): MarketplaceListingReport?
    fun countByListingId(listingId: String): Long
}
