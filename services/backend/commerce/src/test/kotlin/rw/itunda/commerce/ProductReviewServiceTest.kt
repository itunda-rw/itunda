package rw.itunda.commerce

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.Order
import rw.itunda.core.domain.OrderItem
import rw.itunda.core.domain.OrderStatus
import rw.itunda.core.domain.ProductReview
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
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
        val merchantRepository = mockk<MerchantRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val service = ProductReviewService(orderRepository, orderItemRepository, productReviewRepository, merchantRepository, notificationRepository)

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

        When("submitting a comment longer than the real 1000-char DB column bound") {
            every { orderItemRepository.findById("order_item_1") } returns Optional.of(orderItem)
            every { orderRepository.findById("order_1") } returns Optional.of(deliveredOrder)
            every { productReviewRepository.findByOrderItemId("order_item_1") } returns null
            val savedSlot = slot<ProductReview>()
            every { productReviewRepository.save(capture(savedSlot)) } answers { firstArg() }
            val longComment = "x".repeat(1500)

            val review = service.submitReview("buyer_1", "order_item_1", 5, longComment)

            Then("it truncates the comment to 1000 chars rather than risking a raw DB insert failure") {
                review.comment?.length shouldBe 1000
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

    Given("a real merchant owner replying to a real review of their own product") {
        val orderRepository = mockk<OrderRepository>()
        val orderItemRepository = mockk<OrderItemRepository>()
        val productReviewRepository = mockk<ProductReviewRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val service = ProductReviewService(orderRepository, orderItemRepository, productReviewRepository, merchantRepository, notificationRepository)

        val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_1", walletId = "wallet_1", businessName = "Kigali Store", status = MerchantStatus.ACTIVE)
        val review = ProductReview(
            id = "product_review_1", orderItemId = "order_item_1", orderId = "order_1", buyerId = "buyer_1",
            productId = "product_1", merchantId = "merchant_1", rating = 5, comment = "Great!",
        )

        When("the real owner replies") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
            every { productReviewRepository.findById("product_review_1") } returns Optional.of(review)
            every { productReviewRepository.save(any()) } answers { firstArg() }
            every { notificationRepository.save(any()) } answers { firstArg() }

            val result = service.replyToProductReview("owner_1", "product_review_1", "  Thanks for shopping with us!  ")

            Then("it real-trims and saves the reply with a timestamp") {
                result.ownerReply shouldBe "Thanks for shopping with us!"
                (result.ownerRepliedAt != null) shouldBe true
            }

            Then("it real-notifies the real reviewing buyer") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "buyer_1" && it.type == "PRODUCT_REVIEW_REPLY" }) }
            }
        }

        When("someone who isn't the real merchant owner tries to reply") {
            every { merchantRepository.findByOwnerUserId("stranger") } returns null

            Then("it throws MerchantNotFoundException") {
                try {
                    service.replyToProductReview("stranger", "product_review_1", "hi")
                    error("expected MerchantNotFoundException")
                } catch (e: MerchantNotFoundException) {
                    // expected
                }
            }
        }

        When("a different merchant's owner tries to reply to this review") {
            val otherMerchant = Merchant(id = "merchant_2", ownerUserId = "owner_2", walletId = "wallet_2", businessName = "Other Store", status = MerchantStatus.ACTIVE)
            every { merchantRepository.findByOwnerUserId("owner_2") } returns otherMerchant
            every { productReviewRepository.findById("product_review_1") } returns Optional.of(review)

            Then("it throws ProductReviewNotFoundException, not a 403 that would confirm the review exists") {
                try {
                    service.replyToProductReview("owner_2", "product_review_1", "hi")
                    error("expected ProductReviewNotFoundException")
                } catch (e: ProductReviewNotFoundException) {
                    // expected
                }
            }
        }

        When("replying with an empty string") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant

            Then("it throws InvalidProductReviewReplyException before ever touching the review") {
                try {
                    service.replyToProductReview("owner_1", "product_review_1", "   ")
                    error("expected InvalidProductReviewReplyException")
                } catch (e: InvalidProductReviewReplyException) {
                    // expected
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
