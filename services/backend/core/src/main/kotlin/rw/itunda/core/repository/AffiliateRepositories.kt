package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.AffiliateCommission
import rw.itunda.core.domain.AffiliateLink

interface AffiliateLinkRepository : JpaRepository<AffiliateLink, String> {
    fun findByCode(code: String): AffiliateLink?
    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<AffiliateLink>
}

interface AffiliateCommissionRepository : JpaRepository<AffiliateCommission, String> {
    fun findByReferrerIdOrderByCreatedAtDesc(referrerId: String): List<AffiliateCommission>
}
