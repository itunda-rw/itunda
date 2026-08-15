package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.DailyStepReward

interface DailyStepRewardRepository : JpaRepository<DailyStepReward, String> {
    fun findByUserIdAndRewardDate(userId: String, rewardDate: String): DailyStepReward?

    // Real "how many real days has this user engaged with rewards" signal -- see
    // RewardsService.getPet's own doc comment (Naver Pay 페이펫-inspired level).
    fun countByUserId(userId: String): Long
}
