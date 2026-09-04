package rw.itunda.commerce

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.ProductFavorite

/**
 * First test coverage for ProductPriceDropScheduler -- same real "one bad row can't
 * poison the sweep" resilience contract as every other scheduler test in this sweep.
 */
class ProductPriceDropSchedulerTest : BehaviorSpec({

    fun favorite(id: String) = ProductFavorite(id = id, userId = "user_$id", productId = "product_$id")

    Given("3 favorites with a real price drop, where notifying the middle one fails") {
        val productFavoriteService = mockk<ProductFavoriteService>()
        every { productFavoriteService.getFavoritesWithPriceDrops() } returns listOf(favorite("f1"), favorite("f2"), favorite("f3"))
        every { productFavoriteService.notifyPriceDrop("f1") } returns Unit
        every { productFavoriteService.notifyPriceDrop("f2") } throws RuntimeException("push notification failed")
        every { productFavoriteService.notifyPriceDrop("f3") } returns Unit
        val scheduler = ProductPriceDropScheduler(productFavoriteService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 are still notified -- the middle failure doesn't stop the sweep") {
                verify(exactly = 1) { productFavoriteService.notifyPriceDrop("f1") }
                verify(exactly = 1) { productFavoriteService.notifyPriceDrop("f2") }
                verify(exactly = 1) { productFavoriteService.notifyPriceDrop("f3") }
            }
        }
    }

    Given("no favorites with a price drop") {
        val productFavoriteService = mockk<ProductFavoriteService>()
        every { productFavoriteService.getFavoritesWithPriceDrops() } returns emptyList()
        val scheduler = ProductPriceDropScheduler(productFavoriteService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is notified, no exception is thrown") {
                verify(exactly = 0) { productFavoriteService.notifyPriceDrop(any()) }
            }
        }
    }
})
