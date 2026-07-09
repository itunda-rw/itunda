package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.InsurancePolicy

interface InsurancePolicyRepository : JpaRepository<InsurancePolicy, String> {
    fun findByUserId(userId: String): List<InsurancePolicy>
}
