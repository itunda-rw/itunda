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
        val orderItemRepository = mockk<rw.itunda.core.repository.OrderItemRepository>(relaxed = true)
        val eatsFavoriteRepository = mockk<rw.itunda.core.repository.EatsFavoriteRepository>(relaxed = true)
        val pushNotificationService = mockk<rw.itunda.core.push.PushNotificationService>(relaxed = true)
        val service = MerchantProductService(merchantRepository, merchantProductRepository, priceTierRepository, rateLimiter, orderItemRepository, eatsFavoriteRepository, pushNotificationService)

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

            Then("no new-menu-item push fires when nobody has favorited this merchant") {
                verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any()) }
            }
        }

        When("the merchant has 2 real restaurant favoriters and adds a new product") {
            every { eatsFavoriteRepository.findByRestaurantId("merchant_1") } returns listOf(
                rw.itunda.core.domain.EatsFavorite(id = "eats_favorite_1", userId = "fan_1", restaurantId = "merchant_1"),
                rw.itunda.core.domain.EatsFavorite(id = "eats_favorite_2", userId = "fan_2", restaurantId = "merchant_1"),
            )
            service.addProduct("owner_1", "Cappuccino", BigDecimal("3000"))

            Then("both favoriters get a real push, the merchant's own account does not") {
                verify(exactly = 1) { pushNotificationService.sendToUser("fan_1", "New menu item at Kigali Coffee", "Cappuccino - 3000 RWF", any()) }
                verify(exactly = 1) { pushNotificationService.sendToUser("fan_2", "New menu item at Kigali Coffee", "Cappuccino - 3000 RWF", any()) }
                verify(exactly = 0) { pushNotificationService.sendToUser("owner_1", any(), any(), any()) }
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
        val orderItemRepository = mockk<rw.itunda.core.repository.OrderItemRepository>(relaxed = true)
        val eatsFavoriteRepository = mockk<rw.itunda.core.repository.EatsFavoriteRepository>(relaxed = true)
        val pushNotificationService = mockk<rw.itunda.core.push.PushNotificationService>(relaxed = true)
        val service = MerchantProductService(merchantRepository, merchantProductRepository, priceTierRepository, rateLimiter, orderItemRepository, eatsFavoriteRepository, pushNotificationService)

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
        val orderItemRepository = mockk<rw.itunda.core.repository.OrderItemRepository>(relaxed = true)
        val eatsFavoriteRepository = mockk<rw.itunda.core.repository.EatsFavoriteRepository>(relaxed = true)
        val pushNotificationService = mockk<rw.itunda.core.push.PushNotificationService>(relaxed = true)
        val service = MerchantProductService(merchantRepository, merchantProductRepository, priceTierRepository, rateLimiter, orderItemRepository, eatsFavoriteRepository, pushNotificationService)

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
        val orderItemRepository = mockk<rw.itunda.core.repository.OrderItemRepository>(relaxed = true)
        val eatsFavoriteRepository = mockk<rw.itunda.core.repository.EatsFavoriteRepository>(relaxed = true)
        val pushNotificationService = mockk<rw.itunda.core.push.PushNotificationService>(relaxed = true)
        val service = MerchantProductService(merchantRepository, merchantProductRepository, priceTierRepository, rateLimiter, orderItemRepository, eatsFavoriteRepository, pushNotificationService)

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
        val orderItemRepository = mockk<rw.itunda.core.repository.OrderItemRepository>(relaxed = true)
        val eatsFavoriteRepository = mockk<rw.itunda.core.repository.EatsFavoriteRepository>(relaxed = true)
        val pushNotificationService = mockk<rw.itunda.core.push.PushNotificationService>(relaxed = true)
        val service = MerchantProductService(merchantRepository, merchantProductRepository, priceTierRepository, rateLimiter, orderItemRepository, eatsFavoriteRepository, pushNotificationService)

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

    Given("a real product being viewed and analyzed") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val priceTierRepository = mockk<ProductPriceTierRepository>(relaxed = true)
        val orderItemRepository = mockk<rw.itunda.core.repository.OrderItemRepository>()
        val eatsFavoriteRepository = mockk<rw.itunda.core.repository.EatsFavoriteRepository>(relaxed = true)
        val pushNotificationService = mockk<rw.itunda.core.push.PushNotificationService>(relaxed = true)
        val service = MerchantProductService(merchantRepository, merchantProductRepository, priceTierRepository, rateLimiter, orderItemRepository, eatsFavoriteRepository, pushNotificationService)

        val product = MerchantProduct(id = "p2", merchantId = "merchant_2", name = "Espresso", price = BigDecimal("1500"), viewCount = 4)
        every { merchantProductRepository.findById("p2") } returns Optional.of(product)
        every { merchantProductRepository.incrementViewCount("p2") } returns 1

        When("a real customer views it") {
            val viewed = service.getProduct("p2")

            Then("the real view-count increment is triggered and the returned entity reflects it") {
                viewed.viewCount shouldBe 5
                verify(exactly = 1) { merchantProductRepository.incrementViewCount("p2") }
            }
        }

        val ownerMerchant = Merchant(id = "merchant_2", ownerUserId = "owner_2", walletId = "wallet_2", businessName = "Kigali Espresso Bar", status = MerchantStatus.ACTIVE)
        every { merchantRepository.findByOwnerUserId("owner_2") } returns ownerMerchant
        every { orderItemRepository.countByProductId("p2") } returns 7L

        When("the owning merchant requests its real analytics") {
            val (analyzedProduct, orderCount) = service.getProductAnalytics("owner_2", "p2")

            Then("it returns the real view count alongside the real order count") {
                analyzedProduct.id shouldBe "p2"
                orderCount shouldBe 7L
            }
        }

        val otherMerchant = Merchant(id = "merchant_3", ownerUserId = "owner_3", walletId = "wallet_3", businessName = "Rival Cafe", status = MerchantStatus.ACTIVE)
        every { merchantRepository.findByOwnerUserId("owner_3") } returns otherMerchant

        When("a different merchant requests analytics for a product they don't own") {
            Then("it real-404s rather than leaking another merchant's real numbers") {
                try {
                    service.getProductAnalytics("owner_3", "p2")
                    error("expected MerchantProductNotFoundException")
                } catch (e: MerchantProductNotFoundException) {
                    verify(exactly = 0) { orderItemRepository.countByProductId(any()) }
                }
            }
        }
    }

    Given("a real merchant exceeds the real product-creation rate limit") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val rateLimiter = mockk<RateLimiter>()
        val priceTierRepository = mockk<ProductPriceTierRepository>(relaxed = true)
        val orderItemRepository = mockk<rw.itunda.core.repository.OrderItemRepository>(relaxed = true)
        val eatsFavoriteRepository = mockk<rw.itunda.core.repository.EatsFavoriteRepository>(relaxed = true)
        val pushNotificationService = mockk<rw.itunda.core.push.PushNotificationService>(relaxed = true)
        val service = MerchantProductService(merchantRepository, merchantProductRepository, priceTierRepository, rateLimiter, orderItemRepository, eatsFavoriteRepository, pushNotificationService)
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
