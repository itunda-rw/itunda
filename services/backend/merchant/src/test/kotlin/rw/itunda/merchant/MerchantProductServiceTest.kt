package rw.itunda.merchant

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.ProductPriceTierRepository
import java.math.BigDecimal
import java.time.Duration
import java.util.Optional

class MerchantProductServiceTest : BehaviorSpec({

    val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_1", walletId = "wallet_merchant", businessName = "Kigali Coffee", status = MerchantStatus.ACTIVE)

    Given("a registered merchant managing their product catalog") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val priceTierRepository = mockk<ProductPriceTierRepository>(relaxed = true)
        val service = MerchantProductService(merchantRepository, merchantProductRepository, priceTierRepository, rateLimiter)

        every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
        every { merchantProductRepository.save(any()) } answers { firstArg() }

        When("adding a real product") {
            val product = service.addProduct("owner_1", "Latte", BigDecimal("2500"))

            Then("it's created against the real merchant, active by default") {
                product.merchantId shouldBe "merchant_1"
                product.name shouldBe "Latte"
                product.price shouldBe BigDecimal("2500")
                product.active shouldBe true
            }
        }

        When("adding a product with a zero or negative price") {
            Then("it real-fails before ever creating a row") {
                try {
                    service.addProduct("owner_1", "Free Thing", BigDecimal.ZERO)
                    error("expected InvalidProductPriceException")
                } catch (e: InvalidProductPriceException) {
                    // expected
                }
            }
        }
    }

    Given("a merchant's real active catalog") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val priceTierRepository = mockk<ProductPriceTierRepository>(relaxed = true)
        val service = MerchantProductService(merchantRepository, merchantProductRepository, priceTierRepository, rateLimiter)

        every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
        val products = listOf(
            MerchantProduct(id = "p1", merchantId = "merchant_1", name = "Latte", price = BigDecimal("2500")),
            MerchantProduct(id = "p2", merchantId = "merchant_1", name = "Croissant", price = BigDecimal("1500")),
        )
        every { merchantProductRepository.findByMerchantIdAndActiveTrue("merchant_1") } returns products

        When("fetched") {
            val catalog = service.getCatalog("owner_1")

            Then("it reflects the real active products for this merchant") {
                catalog shouldBe products
            }
        }
    }

    Given("a merchant updating one of their own products") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val priceTierRepository = mockk<ProductPriceTierRepository>(relaxed = true)
        val service = MerchantProductService(merchantRepository, merchantProductRepository, priceTierRepository, rateLimiter)

        every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
        val product = MerchantProduct(id = "p1", merchantId = "merchant_1", name = "Latte", price = BigDecimal("2500"))
        every { merchantProductRepository.findById("p1") } returns Optional.of(product)
        every { merchantProductRepository.save(any()) } answers { firstArg() }

        When("changing the name and price") {
            val updated = service.updateProduct("owner_1", "p1", "Large Latte", BigDecimal("3000"))

            Then("the real row reflects the change") {
                updated.name shouldBe "Large Latte"
                updated.price shouldBe BigDecimal("3000")
            }
        }

        When("marking it temporarily sold out") {
            val updated = service.setSoldOut("owner_1", "p1", true)

            Then("the real row is flagged sold out, distinct from the active/soft-delete flag") {
                updated.soldOut shouldBe true
                updated.active shouldBe true
            }
        }

        When("un-marking a previously sold-out product") {
            product.soldOut = true
            val updated = service.setSoldOut("owner_1", "p1", false)

            Then("the real row is real-available again") {
                updated.soldOut shouldBe false
            }
        }
    }

    Given("a merchant trying to modify a product belonging to a different merchant") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val priceTierRepository = mockk<ProductPriceTierRepository>(relaxed = true)
        val service = MerchantProductService(merchantRepository, merchantProductRepository, priceTierRepository, rateLimiter)

        every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
        val othersProduct = MerchantProduct(id = "p9", merchantId = "merchant_other", name = "Someone Else's Item", price = BigDecimal("1000"))
        every { merchantProductRepository.findById("p9") } returns Optional.of(othersProduct)

        When("attempting to update it") {
            Then("it real-404s rather than leaking or mutating another merchant's catalog") {
                try {
                    service.updateProduct("owner_1", "p9", "Hacked Name", BigDecimal("1"))
                    error("expected MerchantProductNotFoundException")
                } catch (e: MerchantProductNotFoundException) {
                    // expected
                }
            }
        }

        When("attempting to remove it") {
            Then("it also real-404s") {
                try {
                    service.removeProduct("owner_1", "p9")
                    error("expected MerchantProductNotFoundException")
                } catch (e: MerchantProductNotFoundException) {
                    // expected
                }
            }
        }

        When("attempting to mark it sold out") {
            Then("it also real-404s rather than letting one merchant toggle another's stock") {
                try {
                    service.setSoldOut("owner_1", "p9", true)
                    error("expected MerchantProductNotFoundException")
                } catch (e: MerchantProductNotFoundException) {
                    // expected
                }
            }
        }
    }

    Given("a merchant removing their own product") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val priceTierRepository = mockk<ProductPriceTierRepository>(relaxed = true)
        val service = MerchantProductService(merchantRepository, merchantProductRepository, priceTierRepository, rateLimiter)

        every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
        val product = MerchantProduct(id = "p1", merchantId = "merchant_1", name = "Latte", price = BigDecimal("2500"))
        every { merchantProductRepository.findById("p1") } returns Optional.of(product)
        every { merchantProductRepository.save(any()) } answers { firstArg() }

        When("removed") {
            val removed = service.removeProduct("owner_1", "p1")

            Then("it's deactivated, not deleted -- preserves past reports/receipts referencing it") {
                removed.active shouldBe false
            }
        }
    }

    Given("a real merchant exceeds the real product-creation rate limit") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val rateLimiter = mockk<RateLimiter>()
        val priceTierRepository = mockk<ProductPriceTierRepository>(relaxed = true)
        val service = MerchantProductService(merchantRepository, merchantProductRepository, priceTierRepository, rateLimiter)
        every { rateLimiter.checkLimit("merchant:product:owner_9", limit = 30, window = Duration.ofHours(1)) } throws RateLimitExceededException("Too many requests")

        When("they try to add another real product") {
            Then("it real-propagates RateLimitExceededException, found missing in a 2026-07-19 security sweep") {
                try {
                    service.addProduct("owner_9", "Latte", BigDecimal("2500"))
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { merchantProductRepository.save(any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
