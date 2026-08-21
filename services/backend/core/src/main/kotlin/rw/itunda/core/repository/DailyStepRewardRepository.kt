package rw.itunda.core.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.DailyStepReward

interface DailyStepRewardRepository : JpaRepository<DailyStepReward, String> {
    fun findByUserIdAndRewardDate(userId: String, rewardDate: String): DailyStepReward?

    // Real fix (concurrency audit, 2026-08-21): reportSteps checks tier/lottery-win
    // flags then posts real ledger money before writing them back -- only one real
    // caller per (user, date) may claim a newly-crossed tier or lottery draw.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from DailyStepReward r where r.userId = :userId and r.rewardDate = :rewardDate")
    fun findByUserIdAndRewardDateForUpdate(@Param("userId") userId: String, @Param("rewardDate") rewardDate: String): DailyStepReward?

    // Real "how many real days has this user engaged with rewards" signal -- see
    // RewardsService.getPet's own doc comment (Naver Pay 페이펫-inspired level).
    fun countByUserId(userId: String): Long
}
