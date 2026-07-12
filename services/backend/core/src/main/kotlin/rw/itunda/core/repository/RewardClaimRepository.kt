package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.RewardClaim

interface RewardClaimRepository : JpaRepository<RewardClaim, String> {
    fun findByUserId(userId: String): List<RewardClaim>
    fun existsByUserIdAndTaskId(userId: String, taskId: String): Boolean
}
