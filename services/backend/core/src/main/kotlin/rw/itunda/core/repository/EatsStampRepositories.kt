package rw.itunda.core.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.EatsStampCampaign
import rw.itunda.core.domain.EatsStampEarning
import rw.itunda.core.domain.EatsStampReward
import rw.itunda.core.domain.EatsStampEvent
import java.util.Optional

interface EatsStampCampaignRepository : JpaRepository<EatsStampCampaign, String> {
    fun findByRestaurantIdAndActiveTrue(restaurantId: String): List<EatsStampCampaign>
    fun findByRestaurantIdOrderByCreatedAtDesc(restaurantId: String): List<EatsStampCampaign>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from EatsStampCampaign c where c.id = :id")
    fun findByIdForUpdate(@Param("id") id: String): Optional<EatsStampCampaign>
}

interface EatsStampEarningRepository : JpaRepository<EatsStampEarning, String> {
    fun existsByCampaignIdAndOrderId(campaignId: String, orderId: String): Boolean
    fun existsByRestaurantIdAndBuyerIdAndEarningDay(restaurantId: String, buyerId: String, earningDay: java.time.LocalDate): Boolean
    fun findByCampaignIdAndBuyerIdAndReversedAtIsNull(campaignId: String, buyerId: String): List<EatsStampEarning>
    fun findByOrderIdAndReversedAtIsNull(orderId: String): List<EatsStampEarning>
}

interface EatsStampEventRepository : JpaRepository<EatsStampEvent, String> {
    fun findByEarningIdOrderByCreatedAtAsc(earningId: String): List<EatsStampEvent>
    fun existsByIdempotencyKey(idempotencyKey: String): Boolean
}

interface EatsStampRewardRepository : JpaRepository<EatsStampReward, String> {
    fun findByBuyerIdAndStatusOrderByIssuedAtDesc(buyerId: String, status: String): List<EatsStampReward>
    fun findByCampaignIdAndBuyerIdOrderByIssuedAtDesc(campaignId: String, buyerId: String): List<EatsStampReward>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from EatsStampReward r where r.id = :id")
    fun findByIdForUpdate(@Param("id") id: String): Optional<EatsStampReward>
}
