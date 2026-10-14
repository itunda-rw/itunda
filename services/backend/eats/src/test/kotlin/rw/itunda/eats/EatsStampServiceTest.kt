package rw.itunda.eats

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.core.domain.EatsOrder
import rw.itunda.core.domain.EatsOrderStatus
import rw.itunda.core.domain.EatsStampCampaign
import rw.itunda.core.domain.EatsStampEarning
import rw.itunda.core.domain.EatsStampReward
import rw.itunda.core.domain.Merchant
import rw.itunda.core.repository.EatsOrderRepository
import rw.itunda.core.repository.EatsStampCampaignRepository
import rw.itunda.core.repository.EatsStampEarningRepository
import rw.itunda.core.repository.EatsStampEventRepository
import rw.itunda.core.repository.EatsStampRewardRepository
import rw.itunda.core.repository.MerchantRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional

class EatsStampServiceTest : FunSpec({
    val now = Instant.parse("2026-10-14T10:00:00Z")
    val todayKigali = LocalDate.ofInstant(now, ZoneId.of("Africa/Kigali"))

    fun order(status: EatsOrderStatus = EatsOrderStatus.DELIVERED) = EatsOrder(
        id = "order-1",
        buyerId = "buyer-1",
        restaurantId = "restaurant-1",
        deliveryAddress = "Kigali",
        itemsSubtotal = BigDecimal("5000"),
        deliveryFee = BigDecimal("500"),
        platformFee = BigDecimal("75"),
        totalAmount = BigDecimal("5500"),
        transactionId = "txn-1",
        status = status,
    )

    fun campaign() = EatsStampCampaign(
        id = "campaign-1",
        restaurantId = "restaurant-1",
        name = "Lunch stamps",
        stampsRequired = 5,
        rewardDescription = "Free side",
        startsAt = Instant.parse("2026-01-01T00:00:00Z"),
        endsAt = Instant.parse("2027-01-01T00:00:00Z"),
    )

    fun service(
        order: EatsOrder,
        campaigns: List<EatsStampCampaign>,
        earnedForOrder: Boolean = false,
        earnedToday: Boolean = false,
        priorCampaignStamps: Int = 0,
        rewardSaves: MutableList<EatsStampReward> = mutableListOf(),
    ): EatsStampService {
        val campaignRepo = mockk<EatsStampCampaignRepository>()
        val earningRepo = mockk<EatsStampEarningRepository>()
        val eventRepo = mockk<EatsStampEventRepository>()
        val rewardRepo = mockk<EatsStampRewardRepository>()
        val orderRepo = mockk<EatsOrderRepository>()
        val merchantRepo = mockk<MerchantRepository>()

        every { orderRepo.findById("order-1") } returns Optional.of(order)
        every { merchantRepo.findByIdForUpdate("restaurant-1") } returns Optional.of(
            mockk<Merchant>(relaxed = true),
        )
        every { campaignRepo.findByRestaurantIdAndActiveTrue("restaurant-1") } returns campaigns
        campaigns.forEach { c -> every { campaignRepo.findByIdForUpdate(c.id) } returns Optional.of(c) }
        every { earningRepo.existsByCampaignIdAndOrderId(any(), any()) } returns earnedForOrder
        every { earningRepo.existsByRestaurantIdAndBuyerIdAndEarningDay(any(), any(), todayKigali) } returns earnedToday
        every { earningRepo.save(any()) } answers { firstArg() }
        every { earningRepo.findByCampaignIdAndBuyerIdAndReversedAtIsNull(any(), any()) } returns
            List(priorCampaignStamps) { mockk<EatsStampEarning>(relaxed = true) }
        every { eventRepo.save(any()) } answers { firstArg() }
        every { rewardRepo.save(any()) } answers {
            rewardSaves += firstArg()
            firstArg()
        }

        return EatsStampService(
            campaignRepo, earningRepo, eventRepo, rewardRepo, orderRepo, merchantRepo,
            java.time.Clock.fixed(now, ZoneId.of("UTC")),
        )
    }

    test("does not award a stamp before the order is delivered") {
        val stampService = service(order(EatsOrderStatus.PICKED_UP), listOf(campaign()))

        stampService.awardForCompletedOrder("order-1") shouldBe 0
    }

    test("does not award a second stamp for an already-processed order") {
        val stampService = service(order(), listOf(campaign()), earnedForOrder = true)

        stampService.awardForCompletedOrder("order-1") shouldBe 0
    }

    test("does not award more than one stamp for a buyer at the same restaurant on the same Kigali day") {
        val stampService = service(order(), listOf(campaign()), earnedToday = true)

        stampService.awardForCompletedOrder("order-1") shouldBe 0
    }

    test("awards one stamp for a completed order when the buyer has no stamp that day") {
        val stampService = service(order(), listOf(campaign()))

        stampService.awardForCompletedOrder("order-1") shouldBe 1
    }

    test("does not award a stamp before a campaign starts") {
        val futureCampaign = campaign().copy(startsAt = now.plusSeconds(60))
        val stampService = service(order(), listOf(futureCampaign))

        stampService.awardForCompletedOrder("order-1") shouldBe 0
    }

    test("does not award a stamp at or after the campaign end instant") {
        val endedCampaign = campaign().copy(endsAt = now)
        val stampService = service(order(), listOf(endedCampaign))

        stampService.awardForCompletedOrder("order-1") shouldBe 0
    }

    test("restaurant owner can update campaign terms and deactivate it") {
        val campaignRepo = mockk<EatsStampCampaignRepository>()
        val earningRepo = mockk<EatsStampEarningRepository>()
        val eventRepo = mockk<EatsStampEventRepository>()
        val rewardRepo = mockk<EatsStampRewardRepository>()
        val orderRepo = mockk<EatsOrderRepository>()
        val merchantRepo = mockk<MerchantRepository>()
        val existing = campaign()
        val restaurant = mockk<Merchant>()
        every { campaignRepo.findByIdForUpdate("campaign-1") } returns Optional.of(existing)
        every { merchantRepo.findById("restaurant-1") } returns Optional.of(restaurant)
        every { restaurant.ownerUserId } returns "owner-1"
        every { campaignRepo.save(any()) } answers { firstArg() }
        val stampService = EatsStampService(
            campaignRepo, earningRepo, eventRepo, rewardRepo, orderRepo, merchantRepo,
            java.time.Clock.fixed(now, ZoneId.of("UTC")),
        )

        val updated = stampService.updateCampaign(
            ownerUserId = "owner-1",
            campaignId = "campaign-1",
            name = "Weekend stamps",
            stampsRequired = 4,
            rewardDescription = "Free drink",
            discountAmount = BigDecimal("1200"),
            startsAt = Instant.parse("2026-10-01T00:00:00Z"),
            endsAt = Instant.parse("2026-12-01T00:00:00Z"),
            active = true,
        )
        updated.name shouldBe "Weekend stamps"
        updated.stampsRequired shouldBe 4
        updated.rewardDescription shouldBe "Free drink"
        updated.rewardDiscountAmount shouldBe BigDecimal("1200")
        updated.updatedAt shouldBe now

        stampService.deactivateCampaign("owner-1", "campaign-1").active shouldBe false
    }

    test("a different user cannot update another restaurant's stamp campaign") {
        val campaignRepo = mockk<EatsStampCampaignRepository>()
        val earningRepo = mockk<EatsStampEarningRepository>()
        val eventRepo = mockk<EatsStampEventRepository>()
        val rewardRepo = mockk<EatsStampRewardRepository>()
        val orderRepo = mockk<EatsOrderRepository>()
        val merchantRepo = mockk<MerchantRepository>()
        val restaurant = mockk<Merchant>()
        every { campaignRepo.findByIdForUpdate("campaign-1") } returns Optional.of(campaign())
        every { merchantRepo.findById("restaurant-1") } returns Optional.of(restaurant)
        every { restaurant.ownerUserId } returns "real-owner"
        val stampService = EatsStampService(
            campaignRepo, earningRepo, eventRepo, rewardRepo, orderRepo, merchantRepo,
            java.time.Clock.fixed(now, ZoneId.of("UTC")),
        )

        runCatching {
            stampService.deactivateCampaign("other-user", "campaign-1")
        }.exceptionOrNull()?.message shouldBe "Only this restaurant's owner can manage its stamp campaign"
    }

    test("issues a reward exactly when the completed order reaches the campaign milestone") {
        val rewards = mutableListOf<EatsStampReward>()
        val stampService = service(
            order(),
            listOf(campaign()),
            priorCampaignStamps = 4,
            rewardSaves = rewards,
        )

        stampService.awardForCompletedOrder("order-1") shouldBe 1
        rewards.size shouldBe 1
        rewards.single().stampThreshold shouldBe 5
        rewards.single().status shouldBe "AVAILABLE"
    }
})
