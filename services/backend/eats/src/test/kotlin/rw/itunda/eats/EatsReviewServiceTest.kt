package rw.itunda.eats

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.EatsOrder
import rw.itunda.core.domain.EatsOrderStatus
import rw.itunda.core.domain.EatsReview
import rw.itunda.core.repository.EatsOrderRepository
import rw.itunda.core.repository.EatsReviewHelpfulVoteRepository
import rw.itunda.core.repository.EatsReviewReportRepository
import rw.itunda.core.repository.EatsReviewRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.RatingSummaryProjection
import java.math.BigDecimal
import java.util.Optional

// See EatsReviewOwnerReplyReportTest.kt for owner-reply/PICKUP-rider-bug/helpful-vote/
// report coverage -- extracted 2026-08-28 (itunda Maps redesign) to stay under the
// 500-line file-size-lint cap once the real preset-tag tests below were added.
class EatsReviewServiceTest : BehaviorSpec({

    Given("a real delivered order") {
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        val eatsReviewRepository = mockk<EatsReviewRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val eatsReviewHelpfulVoteRepository = mockk<EatsReviewHelpfulVoteRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val eatsReviewReportRepository = mockk<EatsReviewReportRepository>(relaxed = true)
        val service = EatsReviewService(eatsOrderRepository, eatsReviewRepository, merchantRepository, notificationRepository, pushNotificationService, eatsReviewHelpfulVoteRepository, rateLimiter, eatsReviewReportRepository)

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

        When("the real buyer submits a review with a real photo URL") {
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(deliveredOrder)
            every { eatsReviewRepository.findByOrderId("eats_order_1") } returns null
            val savedSlot = slot<EatsReview>()
            every { eatsReviewRepository.save(capture(savedSlot)) } answers { firstArg() }

            val review = service.submitReview("buyer_1", "eats_order_1", 5, "Great food!", 4, "Fast delivery", "  https://example.com/food.jpg  ")

            Then("it trims and persists the photo URL") {
                review.photoUrl shouldBe "https://example.com/food.jpg"
            }
        }

        When("submitting a photo URL longer than the real 500-char DB column bound") {
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(deliveredOrder)
            every { eatsReviewRepository.findByOrderId("eats_order_1") } returns null
            val savedSlot = slot<EatsReview>()
            every { eatsReviewRepository.save(capture(savedSlot)) } answers { firstArg() }
            val longUrl = "https://example.com/" + "x".repeat(600)

            val review = service.submitReview("buyer_1", "eats_order_1", 5, null, 4, null, longUrl)

            Then("it truncates the photo URL to 500 chars rather than risking a raw DB insert failure") {
                review.photoUrl?.length shouldBe 500
            }
        }

        When("submitting no photo URL at all") {
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(deliveredOrder)
            every { eatsReviewRepository.findByOrderId("eats_order_1") } returns null
            val savedSlot = slot<EatsReview>()
            every { eatsReviewRepository.save(capture(savedSlot)) } answers { firstArg() }

            val review = service.submitReview("buyer_1", "eats_order_1", 5, null, 4, null)

            Then("it stays null -- a review with no photo is still a complete, honest review") {
                review.photoUrl shouldBe null
            }
        }

        When("submitting real preset tags, including one unknown/invalid tag id") {
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(deliveredOrder)
            every { eatsReviewRepository.findByOrderId("eats_order_1") } returns null
            val savedSlot = slot<EatsReview>()
            every { eatsReviewRepository.save(capture(savedSlot)) } answers { firstArg() }

            val review = service.submitReview(
                "buyer_1", "eats_order_1", 5, "Great food!", 4, "Fast delivery", null,
                listOf("GREAT_FOOD", "NICE_INTERIOR", "GREAT_FOOD", "MADE_UP_TAG"),
            )

            Then("it keeps only the real known tags, dedupes, and drops the unknown one") {
                review.goodPointList() shouldBe listOf("GREAT_FOOD", "NICE_INTERIOR")
            }
        }

        When("submitting no tags at all") {
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(deliveredOrder)
            every { eatsReviewRepository.findByOrderId("eats_order_1") } returns null
            val savedSlot = slot<EatsReview>()
            every { eatsReviewRepository.save(capture(savedSlot)) } answers { firstArg() }

            val review = service.submitReview("buyer_1", "eats_order_1", 5, null, 4, null)

            Then("it stays null -- a review with no tags selected is still a complete, honest review") {
                review.goodPoints shouldBe null
                review.goodPointList() shouldBe emptyList()
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

        When("fetching a real restaurant's real tag aggregate") {
            fun reviewWithTags(id: String, tags: String?) = EatsReview(
                id = id, orderId = "${id}_order", buyerId = "buyer_x", restaurantId = "restaurant_1",
                riderId = null, restaurantRating = 5, restaurantComment = null, riderRating = null, riderComment = null,
                goodPoints = tags,
            )
            every { eatsReviewRepository.findByRestaurantIdAndHiddenFalse("restaurant_1") } returns listOf(
                reviewWithTags("r1", "GREAT_FOOD|NICE_INTERIOR"),
                reviewWithTags("r2", "GREAT_FOOD"),
                reviewWithTags("r3", null),
            )

            val counts = service.restaurantGoodPointCounts("restaurant_1")

            Then("it returns real counts derived only from real submitted tags") {
                counts shouldBe mapOf("GREAT_FOOD" to 2, "NICE_INTERIOR" to 1)
            }
        }
    }

    // Real bug found live (2026-09-06, Eats product-completeness pass): submitReview
    // had shipped with zero rate limiting despite this same class's own helpful-vote/
    // report endpoints already having one. See submitReview's own doc comment.
    Given("a real buyer who has already hit the real review-submission rate limit") {
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        val eatsReviewRepository = mockk<EatsReviewRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val eatsReviewHelpfulVoteRepository = mockk<EatsReviewHelpfulVoteRepository>()
        val rateLimiter = mockk<RateLimiter>()
        every { rateLimiter.checkLimit("eats:review:submit:buyer_1", limit = 20, window = java.time.Duration.ofHours(1)) } throws
            RateLimitExceededException("Too many requests")
        val eatsReviewReportRepository = mockk<EatsReviewReportRepository>(relaxed = true)
        val service = EatsReviewService(eatsOrderRepository, eatsReviewRepository, merchantRepository, notificationRepository, pushNotificationService, eatsReviewHelpfulVoteRepository, rateLimiter, eatsReviewReportRepository)

        When("they try to submit another review") {
            Then("it real-429s before ever looking up the order") {
                try {
                    service.submitReview("buyer_1", "eats_order_1", 5, "Great food!", 4, "Fast delivery")
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    // expected
                }
                verify(exactly = 0) { eatsOrderRepository.findById(any()) }
                verify(exactly = 0) { eatsReviewRepository.save(any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
