package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.DailyStepReward

interface DailyStepRewardRepository : JpaRepository<DailyStepReward, String> {
    fun findByUserIdAndRewardDate(userId: String, rewardDate: String): DailyStepReward?
}
