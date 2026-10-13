package rw.itunda.eats

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.EatsOrder
import rw.itunda.core.domain.EatsOrderStatus
import rw.itunda.core.domain.EatsStampCampaign
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
        every { earningRepo.findByCampaignIdAndBuyerIdAndReversedAtIsNull(any(), any()) } returns emptyList()
        every { eventRepo.save(any()) } answers { firstArg() }
        every { rewardRepo.save(any()) } answers { firstArg() }

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
})
