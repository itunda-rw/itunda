package rw.itunda.commerce

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.TimeDeal
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.TimeDealRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional

/**
 * First test coverage for real Coupang 타임특가 (Time Deal) -- see TimeDeal.kt's own
 * doc comment for the full sourced account. Mirrors ParkingServiceTest's own
 * established mocking conventions.
 */
class TimeDealServiceTest : BehaviorSpec({

    fun newService(
        merchantRepository: MerchantRepository = mockk(),
        merchantProductRepository: MerchantProductRepository = mockk(),
        timeDealRepository: TimeDealRepository = mockk(),
    ) = TimeDealService(merchantRepository, merchantProductRepository, timeDealRepository)

    Given("a real merchant with a real product") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val timeDealRepository = mockk<TimeDealRepository>()
        val service = newService(merchantRepository, merchantProductRepository, timeDealRepository)

        val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_1", walletId = "wallet_1", businessName = "Kigali Store", status = MerchantStatus.ACTIVE)
        val product = MerchantProduct(id = "product_1", merchantId = "merchant_1", name = "Coffee beans", price = BigDecimal("2000"))
        every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
        every { merchantProductRepository.findById("product_1") } returns Optional.of(product)
        val savedSlot = slot<TimeDeal>()
        every { timeDealRepository.save(capture(savedSlot)) } answers { firstArg() }

        val start = Instant.now()
        val end = start.plus(2, ChronoUnit.HOURS)

        When("the owner creates a real time deal cheaper than the regular price") {
            val result = service.createTimeDeal("owner_1", "product_1", BigDecimal("1500"), 10, start, end)

            Then("a real deal row is saved with the price snapshotted from the product") {
                result.dealPrice shouldBe BigDecimal("1500")
                result.originalPrice shouldBe BigDecimal("2000")
                result.remainingQuantity shouldBe 10
                savedSlot.captured.merchantId shouldBe "merchant_1"
            }
        }

        When("the owner tries to price the deal at or above the regular price") {
            Then("it real-rejects before ever saving") {
                try {
                    service.createTimeDeal("owner_1", "product_1", BigDecimal("2000"), 10, start, end)
                    throw AssertionError("expected InvalidTimeDealException")
                } catch (e: InvalidTimeDealException) {
                    e.message shouldBe "A time deal must cost less than the regular price (2000)"
                }
            }
        }
    }

    Given("a real product belonging to a different merchant") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val service = newService(merchantRepository = merchantRepository, merchantProductRepository = merchantProductRepository)

        val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_1", walletId = "wallet_1", businessName = "Kigali Store", status = MerchantStatus.ACTIVE)
        val othersProduct = MerchantProduct(id = "product_2", merchantId = "merchant_2", name = "Someone else's product", price = BigDecimal("5000"))
        every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
        every { merchantProductRepository.findById("product_2") } returns Optional.of(othersProduct)

        When("that merchant tries to create a deal for it") {
            Then("the real ownership check 404s, not 403s") {
                try {
                    service.createTimeDeal("owner_1", "product_2", BigDecimal("4000"), 5, Instant.now(), Instant.now().plusSeconds(3600))
                    throw AssertionError("expected TimeDealProductNotFoundException")
                } catch (e: TimeDealProductNotFoundException) {
                    e.message shouldBe "Product not found"
                }
            }
        }
    }

    Given("a real active deal being browsed") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val timeDealRepository = mockk<TimeDealRepository>()
        val service = newService(merchantRepository, merchantProductRepository, timeDealRepository)
        val deal = TimeDeal(
            id = "time_deal_1", merchantId = "merchant_1", productId = "product_1", dealPrice = BigDecimal("1500"),
            originalPrice = BigDecimal("2000"), totalQuantity = 10, remainingQuantity = 4,
            startsAt = Instant.now().minusSeconds(60), endsAt = Instant.now().plusSeconds(3600),
        )
        every { timeDealRepository.findActiveDeals(any(), any()) } returns PageImpl(listOf(deal))
        val product = MerchantProduct(id = "product_1", merchantId = "merchant_1", name = "Coffee beans", price = BigDecimal("2000"))
        val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_1", walletId = "wallet_1", businessName = "Kigali Store", status = MerchantStatus.ACTIVE)
        every { merchantProductRepository.findAllById(listOf("product_1")) } returns listOf(product)
        every { merchantRepository.findAllById(listOf("merchant_1")) } returns listOf(merchant)

        When("a consumer browses active deals") {
            val page = service.getActiveDeals(PageRequest.of(0, 20))

            Then("the real active deal is returned, enriched with the real product/store context") {
                page.content.single().deal.id shouldBe "time_deal_1"
                page.content.single().productName shouldBe "Coffee beans"
                page.content.single().businessName shouldBe "Kigali Store"
            }
        }
    }

    Given("a real deal the owner wants to end early") {
        val merchantRepository = mockk<MerchantRepository>()
        val timeDealRepository = mockk<TimeDealRepository>()
        val service = newService(merchantRepository = merchantRepository, timeDealRepository = timeDealRepository)

        val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_1", walletId = "wallet_1", businessName = "Kigali Store", status = MerchantStatus.ACTIVE)
        val deal = TimeDeal(
            id = "time_deal_1", merchantId = "merchant_1", productId = "product_1", dealPrice = BigDecimal("1500"),
            originalPrice = BigDecimal("2000"), totalQuantity = 10, remainingQuantity = 4,
            startsAt = Instant.now().minusSeconds(60), endsAt = Instant.now().plusSeconds(3600),
        )
        every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
        every { timeDealRepository.findById("time_deal_1") } returns Optional.of(deal)
        every { timeDealRepository.save(any()) } answers { firstArg() }

        When("the owner ends it early") {
            val result = service.endDeal("owner_1", "time_deal_1")

            Then("endsAt moves to now rather than the row being deleted") {
                result.endsAt.isBefore(Instant.now().plusSeconds(3600)) shouldBe true
            }
        }
    }
})
