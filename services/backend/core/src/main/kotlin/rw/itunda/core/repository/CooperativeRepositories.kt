package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.Cooperative
import rw.itunda.core.domain.CooperativeMembership
import rw.itunda.core.domain.HarvestAdvance

interface CooperativeRepository : JpaRepository<Cooperative, String>

interface CooperativeMembershipRepository : JpaRepository<CooperativeMembership, String> {
    fun findByCooperativeIdAndUserId(cooperativeId: String, userId: String): CooperativeMembership?
    fun findByUserIdAndActiveTrue(userId: String): List<CooperativeMembership>
    fun findByCooperativeIdAndActiveTrue(cooperativeId: String): List<CooperativeMembership>
}

interface HarvestAdvanceRepository : JpaRepository<HarvestAdvance, String> {
    fun findByMembershipIdOrderByCreatedAtDesc(membershipId: String): List<HarvestAdvance>
    fun findByMembershipIdIn(membershipIds: List<String>): List<HarvestAdvance>
}
