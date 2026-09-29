package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.ShoppingMissionReward
import rw.itunda.core.domain.ShoppingWelcomeBonusClaim

interface ShoppingMissionRewardRepository : JpaRepository<ShoppingMissionReward, String> {
    fun findByUserIdAndMissionDate(userId: String, missionDate: String): ShoppingMissionReward?
}

interface ShoppingWelcomeBonusClaimRepository : JpaRepository<ShoppingWelcomeBonusClaim, String>
