package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.InsuranceClaim
import rw.itunda.core.domain.InsuranceClaimStatus

interface InsuranceClaimRepository : JpaRepository<InsuranceClaim, String> {
    fun findByUserIdOrderBySubmittedAtDesc(userId: String): List<InsuranceClaim>
    fun findByStatusOrderBySubmittedAtAsc(status: InsuranceClaimStatus): List<InsuranceClaim>
}
