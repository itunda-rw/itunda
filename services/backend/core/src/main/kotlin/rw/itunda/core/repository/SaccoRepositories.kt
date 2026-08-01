package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.SaccoDividendDistribution
import rw.itunda.core.domain.SaccoDividendPayout
import rw.itunda.core.domain.SaccoShareholding

interface SaccoShareholdingRepository : JpaRepository<SaccoShareholding, String> {
    fun findByUserId(userId: String): SaccoShareholding?
}

interface SaccoDividendDistributionRepository : JpaRepository<SaccoDividendDistribution, String> {
    fun findAllByOrderByDistributionDateDesc(): List<SaccoDividendDistribution>
}

interface SaccoDividendPayoutRepository : JpaRepository<SaccoDividendPayout, String> {
    fun findByShareholdingIdOrderByCreatedAtDesc(shareholdingId: String): List<SaccoDividendPayout>
}
