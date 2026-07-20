package rw.itunda.commerce

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.ProductFavorite
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.ProductFavoriteRepository
import java.math.BigDecimal
import java.util.Optional

class ProductFavoriteServiceTest : BehaviorSpec({

    Given("a real product in a real merchant's catalog") {
        val productFavoriteRepository = mockk<ProductFavoriteRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val service = ProductFavoriteService(productFavoriteRepository, merchantProductRepository, merchantRepository)

        val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_1", walletId = "wallet_1", businessName = "Kigali Store", status = MerchantStatus.ACTIVE)
        val product = MerchantProduct(id = "product_1", merchantId = "merchant_1", name = "Widget", price = BigDecimal("4000"))

        When("favoriting it for the real first time") {
            every { merchantProductRepository.findById("product_1") } returns Optional.of(product)
            every { productFavoriteRepository.findByUserIdAndProductId("buyer_1", "product_1") } returns null
            val savedSlot = slot<ProductFavorite>()
            every { productFavoriteRepository.save(capture(savedSlot)) } answers { firstArg() }

            val favorite = service.addFavorite("buyer_1", "product_1")

            Then("it persists a real new favorite") {
                favorite.userId shouldBe "buyer_1"
                favorite.productId shouldBe "product_1"
                savedSlot.captured.productId shouldBe "product_1"
            }
        }

        When("favoriting an already-favorited product") {
            every { merchantProductRepository.findById("product_1") } returns Optional.of(product)
            val existing = ProductFavorite(id = "product_favorite_1", userId = "buyer_1", productId = "product_1")
            every { productFavoriteRepository.findByUserIdAndProductId("buyer_1", "product_1") } returns existing

            val favorite = service.addFavorite("buyer_1", "product_1")

            Then("it idempotently returns the real existing favorite, never a duplicate") {
                favorite shouldBe existing
                verify(exactly = 0) { productFavoriteRepository.save(any()) }
            }
        }

        When("favoriting a product that doesn't exist") {
            every { merchantProductRepository.findById("ghost") } returns Optional.empty()

            Then("it throws FavoriteProductNotFoundException") {
                try {
                    service.addFavorite("buyer_1", "ghost")
                    error("expected FavoriteProductNotFoundException")
                } catch (e: FavoriteProductNotFoundException) {
                    // expected
                }
            }
        }

        When("un-favoriting a product that was never favorited") {
            every { productFavoriteRepository.deleteByUserIdAndProductId("buyer_1", "never_favorited") } returns 0L

            Then("it silently no-ops rather than throwing") {
                service.removeFavorite("buyer_1", "never_favorited")
                verify { productFavoriteRepository.deleteByUserIdAndProductId("buyer_1", "never_favorited") }
            }
        }

        When("listing a real buyer's favorites") {
            val favorite = ProductFavorite(id = "product_favorite_1", userId = "buyer_1", productId = "product_1")
            every { productFavoriteRepository.findByUserIdOrderByCreatedAtDesc("buyer_1", PageRequest.of(0, 20)) } returns
                PageImpl(listOf(favorite), PageRequest.of(0, 20), 1)
            every { merchantProductRepository.findAllById(listOf("product_1")) } returns listOf(product)
            every { merchantRepository.findAllById(listOf("merchant_1")) } returns listOf(merchant)

            val page = service.getMyFavorites("buyer_1", PageRequest.of(0, 20))

            Then("it resolves the real product's current name/price and the real merchant's business name") {
                page.content.single().productId shouldBe "product_1"
                page.content.single().name shouldBe "Widget"
                page.content.single().price shouldBe BigDecimal("4000")
                page.content.single().businessName shouldBe "Kigali Store"
            }
        }

        When("a favorited product no longer exists") {
            val favorite = ProductFavorite(id = "product_favorite_2", userId = "buyer_1", productId = "deleted_product")
            every { productFavoriteRepository.findByUserIdOrderByCreatedAtDesc("buyer_1", PageRequest.of(0, 20)) } returns
                PageImpl(listOf(favorite), PageRequest.of(0, 20), 1)
            every { merchantProductRepository.findAllById(listOf("deleted_product")) } returns emptyList()
            every { merchantRepository.findAllById(emptyList<String>()) } returns emptyList()

            val page = service.getMyFavorites("buyer_1", PageRequest.of(0, 20))

            Then("it falls back to an honest placeholder rather than crashing") {
                page.content.single().name shouldBe "Product no longer available"
                page.content.single().businessName shouldBe "Merchant no longer available"
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
