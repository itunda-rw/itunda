package rw.itunda.maps

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.MerchantUpdate
import rw.itunda.core.domain.MerchantUpdateLabel
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.eats.EatsReviewService
import rw.itunda.eats.RatingSummary
import rw.itunda.merchant.MerchantNotFoundException
import rw.itunda.merchant.MerchantUpdateService
import java.math.BigDecimal
import java.util.Optional

class MapsPlaceDetailServiceTest : BehaviorSpec({

    Given("a real merchant with real reviews, tags, menu, photos, and updates") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val eatsReviewService = mockk<EatsReviewService>()
        val merchantUpdateService = mockk<MerchantUpdateService>()
        val service = MapsPlaceDetailService(merchantRepository, merchantProductRepository, eatsReviewService, merchantUpdateService, mockk(relaxed = true))

        val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_1", accountId = "account_1", businessName = "Kigali Diner", status = MerchantStatus.ACTIVE, category = "Rwandan")
        merchant.photoUrls = "https://a.jpg,https://b.jpg"
        merchant.aiSummary = "A real generated summary."

        val product = MerchantProduct(id = "p1", merchantId = "merchant_1", name = "Brochette", price = BigDecimal("3000"), active = true)
        val update = MerchantUpdate(id = "u1", merchantId = "merchant_1", label = MerchantUpdateLabel.NOTICE, title = "T", body = "B")

        every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
        every { eatsReviewService.getRestaurantRating("merchant_1") } returns RatingSummary(average = 4.5, count = 10)
        every { eatsReviewService.restaurantGoodPointCounts("merchant_1") } returns mapOf("GREAT_FOOD" to 5)
        every { merchantProductRepository.findByMerchantIdAndActiveTrue("merchant_1") } returns listOf(product)
        every { merchantUpdateService.getUpdates("merchant_1") } returns listOf(update)

        When("fetching the real consolidated place detail") {
            val detail = service.getPlaceDetail("merchant_1")

            Then("it assembles every real piece from its real source, nothing fabricated") {
                detail.businessName shouldBe "Kigali Diner"
                detail.photoUrls shouldBe listOf("https://a.jpg", "https://b.jpg")
                detail.aiSummary shouldBe "A real generated summary."
                detail.rating.average shouldBe 4.5
                detail.goodPointCounts shouldBe mapOf("GREAT_FOOD" to 5)
                detail.menu shouldBe listOf(product)
                detail.updates shouldBe listOf(update)
            }
        }
    }

    Given("an unknown merchant id") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val eatsReviewService = mockk<EatsReviewService>()
        val merchantUpdateService = mockk<MerchantUpdateService>()
        val service = MapsPlaceDetailService(merchantRepository, merchantProductRepository, eatsReviewService, merchantUpdateService, mockk(relaxed = true))
        every { merchantRepository.findById("does_not_exist") } returns Optional.empty()

        When("fetching its place detail") {
            Then("it throws MerchantNotFoundException") {
                try {
                    service.getPlaceDetail("does_not_exist")
                    error("expected MerchantNotFoundException")
                } catch (e: MerchantNotFoundException) {
                    // expected
                }
            }
        }
    }
})
