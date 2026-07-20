package rw.itunda.eats

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import rw.itunda.core.domain.EatsOrder
import rw.itunda.core.domain.EatsOrderStatus
import rw.itunda.core.domain.EatsReview
import rw.itunda.core.repository.EatsOrderRepository
import rw.itunda.core.repository.EatsReviewRepository
import rw.itunda.core.repository.RatingSummaryProjection
import java.math.BigDecimal
import java.util.Optional

class EatsReviewServiceTest : BehaviorSpec({

    Given("a real delivered order") {
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        val eatsReviewRepository = mockk<EatsReviewRepository>()
        val service = EatsReviewService(eatsOrderRepository, eatsReviewRepository)

        val deliveredOrder = EatsOrder(
            id = "eats_order_1", buyerId = "buyer_1", restaurantId = "restaurant_1", riderId = "rider_1", deliveryAddress = "addr",
            itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
            totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_1", status = EatsOrderStatus.DELIVERED,
        )

        When("the real buyer submits a real review") {
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(deliveredOrder)
            every { eatsReviewRepository.findByOrderId("eats_order_1") } returns null
            val savedSlot = slot<EatsReview>()
            every { eatsReviewRepository.save(capture(savedSlot)) } answers { firstArg() }

            val review = service.submitReview("buyer_1", "eats_order_1", 5, "  Great food!  ", 4, "  Fast delivery  ")

            Then("it trims comments, resolves the real restaurant/rider from the order, and persists it") {
                review.restaurantId shouldBe "restaurant_1"
                review.riderId shouldBe "rider_1"
                review.restaurantRating shouldBe 5
                review.restaurantComment shouldBe "Great food!"
                review.riderRating shouldBe 4
                review.riderComment shouldBe "Fast delivery"
            }
        }

        When("submitting comments longer than the real 1000-char DB column bound") {
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(deliveredOrder)
            every { eatsReviewRepository.findByOrderId("eats_order_1") } returns null
            val savedSlot = slot<EatsReview>()
            every { eatsReviewRepository.save(capture(savedSlot)) } answers { firstArg() }
            val longComment = "x".repeat(1500)

            val review = service.submitReview("buyer_1", "eats_order_1", 5, longComment, 4, longComment)

            Then("it truncates both comments to 1000 chars rather than risking a raw DB insert failure") {
                review.restaurantComment?.length shouldBe 1000
                review.riderComment?.length shouldBe 1000
            }
        }

        When("submitting a rating outside 1-5") {
            Then("it throws InvalidEatsRatingException before even looking up the order") {
                try {
                    service.submitReview("buyer_1", "eats_order_1", 6, null, 3, null)
                    error("expected InvalidEatsRatingException")
                } catch (e: InvalidEatsRatingException) {
                    // expected
                }
            }
        }

        When("a stranger (not the real buyer) tries to review the order") {
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(deliveredOrder)

            Then("it throws EatsOrderNotFoundException, not a 403 that would confirm the order exists") {
                try {
                    service.submitReview("stranger", "eats_order_1", 5, null, 5, null)
                    error("expected EatsOrderNotFoundException")
                } catch (e: EatsOrderNotFoundException) {
                    // expected
                }
            }
        }

        When("the real buyer tries to review an order that isn't DELIVERED yet") {
            val placedOrder = EatsOrder(
                id = "eats_order_2", buyerId = "buyer_1", restaurantId = "restaurant_1", riderId = null, deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_2", status = EatsOrderStatus.PLACED,
            )
            every { eatsOrderRepository.findById("eats_order_2") } returns Optional.of(placedOrder)

            Then("it throws EatsOrderNotYetDeliveredException") {
                try {
                    service.submitReview("buyer_1", "eats_order_2", 5, null, 5, null)
                    error("expected EatsOrderNotYetDeliveredException")
                } catch (e: EatsOrderNotYetDeliveredException) {
                    // expected
                }
            }
        }

        When("the real buyer tries to review the same order twice") {
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(deliveredOrder)
            every { eatsReviewRepository.findByOrderId("eats_order_1") } returns EatsReview(
                id = "eats_review_1", orderId = "eats_order_1", buyerId = "buyer_1", restaurantId = "restaurant_1",
                riderId = "rider_1", restaurantRating = 5, restaurantComment = null, riderRating = 5, riderComment = null,
            )

            Then("it throws EatsOrderAlreadyReviewedException") {
                try {
                    service.submitReview("buyer_1", "eats_order_1", 4, null, 4, null)
                    error("expected EatsOrderAlreadyReviewedException")
                } catch (e: EatsOrderAlreadyReviewedException) {
                    // expected
                }
            }
        }

        When("fetching a real restaurant's aggregate rating") {
            every { eatsReviewRepository.getRestaurantRatingSummary("restaurant_1") } returns object : RatingSummaryProjection {
                override val average = 4.5
                override val count = 2L
            }

            val summary = service.getRestaurantRating("restaurant_1")

            Then("it returns the real average and count") {
                summary.average shouldBe 4.5
                summary.count shouldBe 2L
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
