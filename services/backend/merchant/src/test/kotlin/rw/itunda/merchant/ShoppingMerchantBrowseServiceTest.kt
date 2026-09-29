package rw.itunda.merchant

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.repository.EatsFavoriteRepository
import rw.itunda.core.repository.EatsOrderRepository
import rw.itunda.core.repository.EatsReviewRepository
import rw.itunda.core.repository.MerchantMaxDiscountProjection
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import java.math.BigDecimal

class ShoppingMerchantBrowseServiceTest : BehaviorSpec({

    fun merchant(id: String, minOrderAmount: BigDecimal? = null, participatesInEatsMembership: Boolean = false) = Merchant(
        id = id, ownerUserId = "owner_$id", accountId = "account_$id", businessName = "Store $id",
        minOrderAmount = minOrderAmount, participatesInEatsMembership = participatesInEatsMembership,
    )

    fun service(
        merchantRepository: MerchantRepository,
        merchantProductRepository: MerchantProductRepository = mockk(relaxed = true),
    ) = ShoppingMerchantBrowseService(
        merchantRepository,
        mockk<EatsReviewRepository>(relaxed = true),
        mockk<EatsFavoriteRepository>(relaxed = true),
        mockk<EatsOrderRepository>(relaxed = true),
        merchantProductRepository,
    )

    fun discountProjection(merchantId: String, maxDiscountPercent: Int): MerchantMaxDiscountProjection =
        object : MerchantMaxDiscountProjection {
            override val merchantId = merchantId
            override val maxDiscountPercent = maxDiscountPercent
        }

    Given("a real Eats-membership-participating restaurant alongside one that isn't") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchants = listOf(merchant("m1", participatesInEatsMembership = true), merchant("m2", participatesInEatsMembership = false))
        val page: Page<Merchant> = PageImpl(merchants, PageRequest.of(0, 20), 2)
        every { merchantRepository.search(MerchantStatus.ACTIVE, null, null, null, any()) } returns page

        val sut = service(merchantRepository)

        When("browsing") {
            val (_, rows) = sut.browse(null, null, null, null, null, null, PageRequest.of(0, 20))

            Then("the real per-restaurant membership badge reflects each merchant's own real opt-in, not a blanket flag") {
                rows.first { it["merchantId"] == "m1" }["participatesInEatsMembership"] shouldBe true
                rows.first { it["merchantId"] == "m2" }["participatesInEatsMembership"] shouldBe false
            }
        }
    }

    Given("real restaurants with genuinely different real active discounts") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val merchants = listOf(merchant("m1"), merchant("m2"), merchant("m3"))
        val page: Page<Merchant> = PageImpl(merchants, PageRequest.of(0, 20), 3)
        every { merchantRepository.search(MerchantStatus.ACTIVE, null, null, null, any()) } returns page
        // m1: 30% real active discount, m2: 10%, m3: no real active discount at all.
        every { merchantProductRepository.getMaxDiscountByMerchantIds(listOf("m1", "m2", "m3")) } returns listOf(
            discountProjection("m1", 30), discountProjection("m2", 10),
        )

        val sut = service(merchantRepository, merchantProductRepository)

        When("sorting by discount") {
            val (_, rows) = sut.browse(null, null, null, null, null, "discount", PageRequest.of(0, 20))

            Then("real discounted merchants rank highest-first, and a merchant with no real discount sorts last, never fabricated to the front") {
                rows.map { it["merchantId"] } shouldBe listOf("m1", "m2", "m3")
            }
        }
    }

    Given("real restaurants with genuinely different real minimum order amounts") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchants = listOf(
            merchant("m1", minOrderAmount = BigDecimal("5000")),
            merchant("m2", minOrderAmount = BigDecimal("1000")),
            merchant("m3", minOrderAmount = null),
        )
        val page: Page<Merchant> = PageImpl(merchants, PageRequest.of(0, 20), 3)
        every { merchantRepository.search(MerchantStatus.ACTIVE, null, null, null, any()) } returns page

        val sut = service(merchantRepository)

        When("sorting by lowest minimum order") {
            val (_, rows) = sut.browse(null, null, null, null, null, "min_order", PageRequest.of(0, 20))

            Then("the real lowest real minimum sorts first, and a merchant with no real minimum set sorts last") {
                rows.map { it["merchantId"] } shouldBe listOf("m2", "m1", "m3")
            }
        }
    }
})
