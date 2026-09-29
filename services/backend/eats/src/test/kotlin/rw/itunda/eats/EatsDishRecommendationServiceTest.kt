package rw.itunda.eats

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import rw.itunda.core.domain.MerchantBusinessType
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.repository.EatsOrderRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.OrderItemRepository
import rw.itunda.core.repository.ProductOrderCountProjection
import java.math.BigDecimal

class EatsDishRecommendationServiceTest : BehaviorSpec({

    fun dish(id: String) = MerchantProduct(id = id, merchantId = "merchant_$id", name = "Dish $id", price = BigDecimal("2000"))
    fun projection(productId: String, count: Long): ProductOrderCountProjection =
        object : ProductOrderCountProjection {
            override val productId = productId
            override val count = count
        }

    Given("a real buyer who has ordered from one dish's restaurant before, but not the others") {
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val merchantRepository = mockk<MerchantRepository>(relaxed = true)
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        val orderItemRepository = mockk<OrderItemRepository>(relaxed = true)

        val dishes = listOf(dish("d1"), dish("d2"), dish("d3"))
        val page: Page<MerchantProduct> = PageImpl(dishes, PageRequest.of(0, 30), 3)
        every { merchantProductRepository.findDishes(MerchantStatus.ACTIVE, null, MerchantBusinessType.RESTAURANT, null, any()) } returns page
        every { eatsOrderRepository.findDistinctRestaurantIdsByBuyerId("buyer_1") } returns listOf("merchant_d2")

        val sut = EatsDishRecommendationService(merchantProductRepository, merchantRepository, eatsOrderRepository, orderItemRepository)

        When("fetching dishes with the default (recommended) sort") {
            val (_, result) = sut.getDishes(null, null, null, PageRequest.of(0, 30), "buyer_1")

            Then("only the real familiar restaurant's dish is flagged recommended") {
                result.first { it["id"] == "d1" }["recommended"] shouldBe false
                result.first { it["id"] == "d2" }["recommended"] shouldBe true
                result.first { it["id"] == "d3" }["recommended"] shouldBe false
            }
        }
    }

    Given("real dishes with genuinely different real order counts") {
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val merchantRepository = mockk<MerchantRepository>(relaxed = true)
        val eatsOrderRepository = mockk<EatsOrderRepository>(relaxed = true)
        val orderItemRepository = mockk<OrderItemRepository>()

        val dishes = listOf(dish("d1"), dish("d2"), dish("d3"))
        val page: Page<MerchantProduct> = PageImpl(dishes, PageRequest.of(0, 30), 3)
        every { merchantProductRepository.findDishes(MerchantStatus.ACTIVE, null, MerchantBusinessType.RESTAURANT, null, any()) } returns page
        // d1: 2 real orders, d2: 9, d3: never ordered.
        every { orderItemRepository.getProductOrderCounts(listOf("d1", "d2", "d3")) } returns listOf(projection("d1", 2), projection("d2", 9))

        val sut = EatsDishRecommendationService(merchantProductRepository, merchantRepository, eatsOrderRepository, orderItemRepository)

        When("fetching dishes sorted by popular") {
            val (_, result) = sut.getDishes(null, null, "popular", PageRequest.of(0, 30), "buyer_1")

            Then("the real highest-order-count dish ranks first, and a dish with zero real orders ranks last") {
                result.map { it["id"] } shouldBe listOf("d2", "d1", "d3")
            }
        }
    }
})
