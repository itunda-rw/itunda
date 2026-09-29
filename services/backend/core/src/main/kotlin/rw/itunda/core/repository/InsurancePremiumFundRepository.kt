package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.InsurancePremiumFund
import rw.itunda.core.domain.InsurancePremiumFundStatus

interface InsurancePremiumFundRepository : JpaRepository<InsurancePremiumFund, String> {
    fun findByUserId(userId: String): List<InsurancePremiumFund>
    fun findByPolicyIdAndStatus(policyId: String, status: InsurancePremiumFundStatus): InsurancePremiumFund?
}
