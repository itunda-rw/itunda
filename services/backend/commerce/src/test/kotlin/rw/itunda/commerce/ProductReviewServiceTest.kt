package rw.itunda.commerce

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import rw.itunda.core.domain.Order
import rw.itunda.core.domain.OrderItem
import rw.itunda.core.domain.OrderStatus
import rw.itunda.core.domain.ProductReview
import rw.itunda.core.repository.OrderItemRepository
import rw.itunda.core.repository.OrderRepository
import rw.itunda.core.repository.ProductReviewRepository
import rw.itunda.core.repository.RatingSummaryProjection
import java.math.BigDecimal
import java.util.Optional

class ProductReviewServiceTest : BehaviorSpec({

    Given("a real delivered order with one line item") {
        val orderRepository = mockk<OrderRepository>()
        val orderItemRepository = mockk<OrderItemRepository>()
        val productReviewRepository = mockk<ProductReviewRepository>()
        val service = ProductReviewService(orderRepository, orderItemRepository, productReviewRepository)

        val deliveredOrder = Order(
            id = "order_1", buyerId = "buyer_1", merchantId = "merchant_1", deliveryAddress = "addr",
            totalAmount = BigDecimal("6000"), fee = BigDecimal("90"), transactionId = "ledgertxn_1",
            status = OrderStatus.DELIVERED,
        )
        val orderItem = OrderItem(
            id = "order_item_1", orderId = "order_1", productId = "product_1", productName = "Widget",
            unitPrice = BigDecimal("6000"), quantity = 1,
        )

        When("the real buyer submits a real review") {
            every { orderItemRepository.findById("order_item_1") } returns Optional.of(orderItem)
            every { orderRepository.findById("order_1") } returns Optional.of(deliveredOrder)
            every { productReviewRepository.findByOrderItemId("order_item_1") } returns null
            val savedSlot = slot<ProductReview>()
            every { productReviewRepository.save(capture(savedSlot)) } answers { firstArg() }

            val review = service.submitReview("buyer_1", "order_item_1", 5, "  Great product!  ")

            Then("it trims the comment, resolves the real product/merchant from the order item, and persists it") {
                review.productId shouldBe "product_1"
                review.merchantId shouldBe "merchant_1"
                review.orderId shouldBe "order_1"
                review.rating shouldBe 5
                review.comment shouldBe "Great product!"
            }
        }

        When("submitting a rating outside 1-5") {
            Then("it throws InvalidProductRatingException before even looking up the order item") {
                try {
                    service.submitReview("buyer_1", "order_item_1", 6, null)
                    error("expected InvalidProductRatingException")
                } catch (e: InvalidProductRatingException) {
                    // expected
                }
            }
        }

        When("a stranger (not the real buyer) tries to review the order item") {
            every { orderItemRepository.findById("order_item_1") } returns Optional.of(orderItem)
            every { orderRepository.findById("order_1") } returns Optional.of(deliveredOrder)

            Then("it throws OrderItemNotFoundException, not a 403 that would confirm the order exists") {
                try {
                    service.submitReview("stranger", "order_item_1", 5, null)
                    error("expected OrderItemNotFoundException")
                } catch (e: OrderItemNotFoundException) {
                    // expected
                }
            }
        }

        When("the real buyer tries to review an item on an order that isn't DELIVERED yet") {
            val placedOrder = Order(
                id = "order_2", buyerId = "buyer_1", merchantId = "merchant_1", deliveryAddress = "addr",
                totalAmount = BigDecimal("6000"), fee = BigDecimal("90"), transactionId = "ledgertxn_2",
                status = OrderStatus.PLACED,
            )
            val placedOrderItem = OrderItem(
                id = "order_item_2", orderId = "order_2", productId = "product_1", productName = "Widget",
                unitPrice = BigDecimal("6000"), quantity = 1,
            )
            every { orderItemRepository.findById("order_item_2") } returns Optional.of(placedOrderItem)
            every { orderRepository.findById("order_2") } returns Optional.of(placedOrder)

            Then("it throws ProductNotYetDeliveredException") {
                try {
                    service.submitReview("buyer_1", "order_item_2", 5, null)
                    error("expected ProductNotYetDeliveredException")
                } catch (e: ProductNotYetDeliveredException) {
                    // expected
                }
            }
        }

        When("the real buyer tries to review the same order item twice") {
            every { orderItemRepository.findById("order_item_1") } returns Optional.of(orderItem)
            every { orderRepository.findById("order_1") } returns Optional.of(deliveredOrder)
            every { productReviewRepository.findByOrderItemId("order_item_1") } returns ProductReview(
                id = "product_review_1", orderItemId = "order_item_1", orderId = "order_1", buyerId = "buyer_1",
                productId = "product_1", merchantId = "merchant_1", rating = 5, comment = null,
            )

            Then("it throws ProductAlreadyReviewedException") {
                try {
                    service.submitReview("buyer_1", "order_item_1", 4, null)
                    error("expected ProductAlreadyReviewedException")
                } catch (e: ProductAlreadyReviewedException) {
                    // expected
                }
            }
        }

        When("fetching a real product's aggregate rating") {
            every { productReviewRepository.getProductRatingSummary("product_1") } returns object : RatingSummaryProjection {
                override val average = 4.5
                override val count = 2L
            }

            val summary = service.getProductRating("product_1")

            Then("it returns the real average and count") {
                summary.average shouldBe 4.5
                summary.count shouldBe 2L
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
