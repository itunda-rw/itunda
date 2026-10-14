package rw.itunda.eats

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import rw.itunda.core.domain.EatsOrderStatus
import rw.itunda.core.domain.EatsStampCampaign
import rw.itunda.core.domain.EatsStampEarning
import rw.itunda.core.domain.EatsStampEvent
import rw.itunda.core.domain.EatsStampReward
import rw.itunda.core.repository.EatsOrderRepository
import rw.itunda.core.repository.EatsStampCampaignRepository
import rw.itunda.core.repository.EatsStampEarningRepository
import rw.itunda.core.repository.EatsStampEventRepository
import rw.itunda.core.repository.EatsStampRewardRepository
import rw.itunda.core.repository.MerchantRepository
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Order-scoped Eats stamps. This service never touches the general merchant payment
 * loyalty balance. Reward records are entitlements only; checkout redemption is a
 * separate, deliberately unimplemented integration and must not be advertised yet.
 */
@Service
class EatsStampService(
    private val campaignRepository: EatsStampCampaignRepository,
    private val earningRepository: EatsStampEarningRepository,
    private val eventRepository: EatsStampEventRepository,
    private val rewardRepository: EatsStampRewardRepository,
    private val orderRepository: EatsOrderRepository,
    private val merchantRepository: MerchantRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val rwandaZone = ZoneId.of("Africa/Kigali")

    @Transactional
    fun createCampaign(
        ownerUserId: String,
        restaurantId: String,
        name: String,
        stampsRequired: Int,
        rewardDescription: String,
        discountAmount: BigDecimal?,
        startsAt: Instant,
        endsAt: Instant?,
    ): EatsStampCampaign {
        val restaurant = merchantRepository.findById(restaurantId).orElseThrow {
            IllegalArgumentException("Restaurant not found")
        }
        require(restaurant.ownerUserId == ownerUserId) { "Only this restaurant's owner can manage its stamp campaign" }
        require(name.isNotBlank() && name.length <= 100) { "Campaign name must be 1-100 characters" }
        require(stampsRequired in 2..50) { "Stamps required must be between 2 and 50" }
        require(rewardDescription.isNotBlank() && rewardDescription.length <= 240) { "Reward description must be 1-240 characters" }
        require(discountAmount == null || discountAmount > BigDecimal.ZERO) { "Discount amount must be positive" }
        require(endsAt == null || endsAt > startsAt) { "Campaign end must be after its start" }
        val now = clock.instant()
        return campaignRepository.save(
            EatsStampCampaign(
                id = UUID.randomUUID().toString(),
                restaurantId = restaurantId,
                name = name.trim(),
                stampsRequired = stampsRequired,
                rewardDescription = rewardDescription.trim(),
                rewardDiscountAmount = discountAmount,
                startsAt = startsAt,
                endsAt = endsAt,
                active = true,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /**
     * Update an existing campaign without allowing a restaurant owner to mutate another
     * restaurant's campaign. The row lock serializes edits with stamp awards.
     */
    @Transactional
    fun updateCampaign(
        ownerUserId: String,
        campaignId: String,
        name: String,
        stampsRequired: Int,
        rewardDescription: String,
        discountAmount: BigDecimal?,
        startsAt: Instant,
        endsAt: Instant?,
        active: Boolean,
    ): EatsStampCampaign {
        val campaign = campaignRepository.findByIdForUpdate(campaignId).orElseThrow {
            IllegalArgumentException("Stamp campaign not found")
        }
        val restaurant = merchantRepository.findById(campaign.restaurantId).orElseThrow {
            IllegalArgumentException("Restaurant not found")
        }
        require(restaurant.ownerUserId == ownerUserId) { "Only this restaurant's owner can manage its stamp campaign" }
        require(name.isNotBlank() && name.length <= 100) { "Campaign name must be 1-100 characters" }
        require(stampsRequired in 2..50) { "Stamps required must be between 2 and 50" }
        require(rewardDescription.isNotBlank() && rewardDescription.length <= 240) { "Reward description must be 1-240 characters" }
        require(discountAmount == null || discountAmount > BigDecimal.ZERO) { "Discount amount must be positive" }
        require(endsAt == null || endsAt > startsAt) { "Campaign end must be after its start" }

        campaign.name = name.trim()
        campaign.stampsRequired = stampsRequired
        campaign.rewardDescription = rewardDescription.trim()
        campaign.rewardDiscountAmount = discountAmount
        campaign.startsAt = startsAt
        campaign.endsAt = endsAt
        campaign.active = active
        campaign.updatedAt = clock.instant()
        return campaignRepository.save(campaign)
    }

    /** Deactivation is reversible through updateCampaign; existing rewards are untouched. */
    @Transactional
    fun deactivateCampaign(ownerUserId: String, campaignId: String): EatsStampCampaign {
        val campaign = campaignRepository.findByIdForUpdate(campaignId).orElseThrow {
            IllegalArgumentException("Stamp campaign not found")
        }
        val restaurant = merchantRepository.findById(campaign.restaurantId).orElseThrow {
            IllegalArgumentException("Restaurant not found")
        }
        require(restaurant.ownerUserId == ownerUserId) { "Only this restaurant's owner can manage its stamp campaign" }
        if (campaign.active) {
            campaign.active = false
            campaign.updatedAt = clock.instant()
            return campaignRepository.save(campaign)
        }
        return campaign
    }

    @Transactional
    fun awardForCompletedOrder(orderId: String): Int {
        val order = orderRepository.findById(orderId).orElse(null) ?: return 0
        if (order.status != EatsOrderStatus.DELIVERED) return 0
        // Lock the restaurant, not just an individual campaign: locking campaigns
        // alone does not serialize awards when two orders race through different
        // active campaigns for the same restaurant.
        if (merchantRepository.findByIdForUpdate(order.restaurantId).isEmpty) return 0
        val now = clock.instant()
        val today = LocalDate.ofInstant(now, rwandaZone)
        var awarded = 0
        val campaigns = campaignRepository.findByRestaurantIdAndActiveTrue(order.restaurantId)
        for (candidate in campaigns) {
            val campaign = campaignRepository.findByIdForUpdate(candidate.id).orElse(null) ?: continue
            if (!campaign.active || campaign.startsAt.isAfter(now) || (campaign.endsAt != null && !campaign.endsAt!!.isAfter(now))) continue
            if (earningRepository.existsByCampaignIdAndOrderId(campaign.id, order.id)) continue
            // Coupang Eats' rule is one stamp per buyer / restaurant / local day.
            if (earningRepository.existsByRestaurantIdAndBuyerIdAndEarningDay(order.restaurantId, order.buyerId, today)) continue

            val earning = earningRepository.save(
                EatsStampEarning(
                    id = UUID.randomUUID().toString(),
                    campaignId = campaign.id,
                    restaurantId = order.restaurantId,
                    buyerId = order.buyerId,
                    orderId = order.id,
                    earningDay = today,
                    earnedAt = now,
                ),
            )
            eventRepository.save(
                EatsStampEvent(
                    id = UUID.randomUUID().toString(),
                    earningId = earning.id,
                    campaignId = campaign.id,
                    restaurantId = order.restaurantId,
                    buyerId = order.buyerId,
                    orderId = order.id,
                    eventType = "EARNED",
                    idempotencyKey = "eats-stamp-earned:${campaign.id}:${order.id}",
                    createdAt = now,
                ),
            )
            val total = earningRepository.findByCampaignIdAndBuyerIdAndReversedAtIsNull(campaign.id, order.buyerId).size
            val milestone = (total / campaign.stampsRequired) * campaign.stampsRequired
            if (milestone > 0 && total % campaign.stampsRequired == 0) {
                val reward = rewardRepository.save(
                    EatsStampReward(
                        id = UUID.randomUUID().toString(),
                        campaignId = campaign.id,
                        restaurantId = campaign.restaurantId,
                        buyerId = order.buyerId,
                        stampThreshold = milestone,
                        rewardDescription = campaign.rewardDescription,
                        discountAmount = campaign.rewardDiscountAmount,
                        issuedAt = now,
                    ),
                )
                eventRepository.save(
                    EatsStampEvent(
                        id = UUID.randomUUID().toString(),
                        earningId = earning.id,
                        campaignId = campaign.id,
                        restaurantId = campaign.restaurantId,
                        buyerId = order.buyerId,
                        orderId = order.id,
                        eventType = "REWARD_ISSUED",
                        idempotencyKey = "eats-stamp-reward-issued:${reward.id}",
                        createdAt = now,
                    ),
                )
            }
            awarded++
            // Daily uniqueness is across campaigns at the restaurant; don't issue multiple
            // stamps for the same buyer/day by walking the remaining active campaigns.
            break
        }
        return awarded
    }

    @Transactional
    fun getBuyerProgress(buyerId: String, campaignId: String): Map<String, Any> {
        val campaign = campaignRepository.findById(campaignId).orElseThrow { IllegalArgumentException("Campaign not found") }
        val earned = earningRepository.findByCampaignIdAndBuyerIdAndReversedAtIsNull(campaignId, buyerId).size
        val rewards = rewardRepository.findByCampaignIdAndBuyerIdOrderByIssuedAtDesc(campaignId, buyerId)
        return mapOf(
            "campaignId" to campaign.id,
            "restaurantId" to campaign.restaurantId,
            "campaignName" to campaign.name,
            "stampsRequired" to campaign.stampsRequired,
            "stampsEarnedTowardNextReward" to (earned % campaign.stampsRequired),
            "totalStampsEarned" to earned,
            "stampsRemaining" to (campaign.stampsRequired - (earned % campaign.stampsRequired)),
            "rewards" to rewards,
        )
    }

    @Transactional
    fun activeCampaigns(restaurantId: String): List<EatsStampCampaign> {
        val now = clock.instant()
        return campaignRepository.findByRestaurantIdAndActiveTrue(restaurantId)
            .filter { !it.startsAt.isAfter(now) && (it.endsAt == null || it.endsAt!!.isAfter(now)) }
    }
}
