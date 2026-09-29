package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.InsurancePolicy

interface InsurancePolicyRepository : JpaRepository<InsurancePolicy, String> {
    fun findByUserId(userId: String): List<InsurancePolicy>

    // Real Kakao Pay/Toss Insurance 갱신 안내 (renewal notice) -- see
    // InsurancePolicy.renewalReminderSentAt's own doc comment for the real sourcing.
    // Every real active policy that hasn't been reminded yet; the scheduler filters this
    // down to policies whose endDate has actually entered the real reminder window.
    fun findByStatusAndRenewalReminderSentAtIsNull(status: String): List<InsurancePolicy>
}
