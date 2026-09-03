package rw.itunda.core.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.Cooperative
import rw.itunda.core.domain.CooperativeMembership
import rw.itunda.core.domain.HarvestAdvance
import java.util.Optional

interface CooperativeRepository : JpaRepository<Cooperative, String>

interface CooperativeMembershipRepository : JpaRepository<CooperativeMembership, String> {
    fun findByCooperativeIdAndUserId(cooperativeId: String, userId: String): CooperativeMembership?
    fun findByUserIdAndActiveTrue(userId: String): List<CooperativeMembership>
    fun findByCooperativeIdAndActiveTrue(cooperativeId: String): List<CooperativeMembership>
}

interface HarvestAdvanceRepository : JpaRepository<HarvestAdvance, String> {
    fun findByMembershipIdOrderByCreatedAtDesc(membershipId: String): List<HarvestAdvance>
    fun findByMembershipIdIn(membershipIds: List<String>): List<HarvestAdvance>

    // Real lost-update fix (2026-09-03): HarvestAdvance carries no @Version, and
    // disburseAdvance/repayAdvance mutate `status` after posting real ledger money with a
    // plain unlocked findById -- two concurrent calls could both pass the status check,
    // double-disbursing itunda's own capital or double-charging a repayment.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from HarvestAdvance a where a.id = :id")
    fun findByIdForUpdate(@Param("id") id: String): Optional<HarvestAdvance>
}
