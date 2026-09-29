package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.MerchantAd
import java.time.Instant

interface MerchantAdRepository : JpaRepository<MerchantAd, String> {
    fun findByMerchantId(merchantId: String): MerchantAd?

    // Coarse DB-level filter (real, still-active ads with a merchant location to
    // target from) -- same "coarse repo filter, exact Haversine check in application
    // code" pattern AgentService.nearby/MarketplaceService.nearby/JobPostService.nearby/
    // PropertyListingService.nearby all already establish; this codebase has no raw-SQL
    // Haversine anywhere.
    fun findByActiveUntilAfter(now: Instant): List<MerchantAd>
}
