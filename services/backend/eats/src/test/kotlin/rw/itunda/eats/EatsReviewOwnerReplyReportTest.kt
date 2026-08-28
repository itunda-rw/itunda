package rw.itunda.eats

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.EatsFulfillmentType
import rw.itunda.core.domain.EatsOrder
import rw.itunda.core.domain.EatsOrderStatus
import rw.itunda.core.domain.EatsReview
import rw.itunda.core.domain.EatsReviewHelpfulVote
import rw.itunda.core.domain.EatsReviewReport
import rw.itunda.core.domain.EatsReviewReportReason
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.EatsOrderRepository
import rw.itunda.core.repository.EatsReviewHelpfulVoteRepository
import rw.itunda.core.repository.EatsReviewReportRepository
import rw.itunda.core.repository.EatsReviewRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import java.math.BigDecimal
import java.util.Optional

// Extracted from EatsReviewServiceTest.kt (2026-08-28, itunda Maps redesign) -- that
// file crossed 500 lines once the real preset-tag tests were added; this half covers
// owner replies, the real PICKUP-order rider-rating bug fix, helpful votes, and
// reporting -- a real, cohesive group, same file-size-lint extraction precedent as
// every other split this session.
class EatsReviewOwnerReplyReportTest : BehaviorSpec({

    Given("a real restaurant owner replying to a real review of their own restaurant") {
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        val eatsReviewRepository = mockk<EatsReviewRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val eatsReviewHelpfulVoteRepository = mockk<EatsReviewHelpfulVoteRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val eatsReviewReportRepository = mockk<EatsReviewReportRepository>(relaxed = true)
        val service = EatsReviewService(eatsOrderRepository, eatsReviewRepository, merchantRepository, notificationRepository, pushNotificationService, eatsReviewHelpfulVoteRepository, rateLimiter, eatsReviewReportRepository)

        val restaurant = Merchant(id = "restaurant_1", ownerUserId = "owner_1", accountId = "account_1", businessName = "Kigali Diner", status = MerchantStatus.ACTIVE)
        val review = EatsReview(
            id = "eats_review_1", orderId = "eats_order_1", buyerId = "buyer_1", restaurantId = "restaurant_1",
            riderId = "rider_1", restaurantRating = 5, restaurantComment = "Great!", riderRating = 5, riderComment = null,
        )

        When("the real owner replies") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns restaurant
            every { eatsReviewRepository.findById("eats_review_1") } returns Optional.of(review)
            every { eatsReviewRepository.save(any()) } answers { firstArg() }
            every { notificationRepository.save(any()) } answers { firstArg() }

            val result = service.replyToRestaurantReview("owner_1", "eats_review_1", "  Thank you for visiting!  ")

            Then("it real-trims and saves the reply with a timestamp") {
                result.ownerReply shouldBe "Thank you for visiting!"
                (result.ownerRepliedAt != null) shouldBe true
            }

            Then("it real-notifies the real reviewing buyer") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "buyer_1" && it.type == "EATS_REVIEW_REPLY" }) }
            }
        }

        When("someone who isn't the real restaurant owner tries to reply") {
            every { merchantRepository.findByOwnerUserId("stranger") } returns null

            Then("it throws RestaurantNotFoundException") {
                try {
                    service.replyToRestaurantReview("stranger", "eats_review_1", "hi")
                    error("expected RestaurantNotFoundException")
                } catch (e: RestaurantNotFoundException) {
                    // expected
                }
            }
        }

        When("a different restaurant's owner tries to reply to this review") {
            val otherRestaurant = Merchant(id = "restaurant_2", ownerUserId = "owner_2", accountId = "account_2", businessName = "Other Place", status = MerchantStatus.ACTIVE)
            every { merchantRepository.findByOwnerUserId("owner_2") } returns otherRestaurant
            every { eatsReviewRepository.findById("eats_review_1") } returns Optional.of(review)

            Then("it throws EatsReviewNotFoundException, not a 403 that would confirm the review exists") {
                try {
                    service.replyToRestaurantReview("owner_2", "eats_review_1", "hi")
                    error("expected EatsReviewNotFoundException")
                } catch (e: EatsReviewNotFoundException) {
                    // expected
                }
            }
        }

        When("replying with an empty string") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns restaurant

            Then("it throws InvalidEatsReviewReplyException before ever touching the review") {
                try {
                    service.replyToRestaurantReview("owner_1", "eats_review_1", "   ")
                    error("expected InvalidEatsReviewReplyException")
                } catch (e: InvalidEatsReviewReplyException) {
                    // expected
                }
            }
        }
    }

    Given("a real Baemin-style PICKUP order delivered with no rider ever assigned") {
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        val eatsReviewRepository = mockk<EatsReviewRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val eatsReviewHelpfulVoteRepository = mockk<EatsReviewHelpfulVoteRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val eatsReviewReportRepository = mockk<EatsReviewReportRepository>(relaxed = true)
        val service = EatsReviewService(eatsOrderRepository, eatsReviewRepository, merchantRepository, notificationRepository, pushNotificationService, eatsReviewHelpfulVoteRepository, rateLimiter, eatsReviewReportRepository)

        val deliveredPickupOrder = EatsOrder(
            id = "eats_order_pickup_1", buyerId = "buyer_1", restaurantId = "restaurant_1", riderId = null, deliveryAddress = "Pickup at Diner",
            itemsSubtotal = BigDecimal("4000"), deliveryFee = BigDecimal.ZERO, platformFee = BigDecimal("60"),
            totalAmount = BigDecimal("4000"), transactionId = "ledgertxn_pickup_1", status = EatsOrderStatus.DELIVERED,
            fulfillmentType = EatsFulfillmentType.PICKUP,
        )

        When("the real buyer reviews it with no rider rating at all -- the real bug this fixes") {
            every { eatsOrderRepository.findById("eats_order_pickup_1") } returns Optional.of(deliveredPickupOrder)
            every { eatsReviewRepository.findByOrderId("eats_order_pickup_1") } returns null
            every { eatsReviewRepository.save(any()) } answers { firstArg() }

            val review = service.submitReview("buyer_1", "eats_order_pickup_1", 5, "Great pickup experience!", null, null)

            Then("it real-succeeds with a null riderId/riderRating -- previously this real-threw for every PICKUP order") {
                review.riderId shouldBe null
                review.riderRating shouldBe null
                review.riderComment shouldBe null
                review.restaurantRating shouldBe 5
            }
        }

        When("a caller passes a rider rating anyway for a PICKUP order with no real rider") {
            every { eatsOrderRepository.findById("eats_order_pickup_1") } returns Optional.of(deliveredPickupOrder)
            every { eatsReviewRepository.findByOrderId("eats_order_pickup_1") } returns null
            every { eatsReviewRepository.save(any()) } answers { firstArg() }

            val review = service.submitReview("buyer_1", "eats_order_pickup_1", 4, null, 3, "some comment")

            Then("it real-discards the rider rating/comment -- there is no real rider to attribute it to") {
                review.riderRating shouldBe null
                review.riderComment shouldBe null
            }
        }
    }

    Given("a real review with a real helpfulCount of 3") {
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        val eatsReviewRepository = mockk<EatsReviewRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val eatsReviewHelpfulVoteRepository = mockk<EatsReviewHelpfulVoteRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val eatsReviewReportRepository = mockk<EatsReviewReportRepository>(relaxed = true)
        val service = EatsReviewService(eatsOrderRepository, eatsReviewRepository, merchantRepository, notificationRepository, pushNotificationService, eatsReviewHelpfulVoteRepository, rateLimiter, eatsReviewReportRepository)

        val review = EatsReview(
            id = "eats_review_1", orderId = "eats_order_1", buyerId = "buyer_1", restaurantId = "restaurant_1",
            riderId = "rider_1", restaurantRating = 5, restaurantComment = "Great!", riderRating = 5, riderComment = null,
            helpfulCount = 3,
        )

        When("a real viewer marks it helpful for the first time") {
            every { eatsReviewRepository.findById("eats_review_1") } returns Optional.of(review)
            every { eatsReviewHelpfulVoteRepository.findByReviewIdAndUserId("eats_review_1", "viewer_1") } returns null
            every { eatsReviewHelpfulVoteRepository.save(any()) } answers { firstArg() }
            every { eatsReviewRepository.save(any()) } answers { firstArg() }

            val helpful = service.toggleHelpful("viewer_1", "eats_review_1")

            Then("it returns true, saves a real vote row, and increments the real counter to 4") {
                helpful shouldBe true
                verify(exactly = 1) { eatsReviewHelpfulVoteRepository.save(match { it.reviewId == "eats_review_1" && it.userId == "viewer_1" }) }
                review.helpfulCount shouldBe 4
            }
        }

        When("that same real viewer taps it again") {
            val existingVote = EatsReviewHelpfulVote(id = "eats_review_helpful_1", reviewId = "eats_review_1", userId = "viewer_1")
            every { eatsReviewRepository.findById("eats_review_1") } returns Optional.of(review)
            every { eatsReviewHelpfulVoteRepository.findByReviewIdAndUserId("eats_review_1", "viewer_1") } returns existingVote
            every { eatsReviewHelpfulVoteRepository.delete(existingVote) } returns Unit
            every { eatsReviewRepository.save(any()) } answers { firstArg() }

            val helpful = service.toggleHelpful("viewer_1", "eats_review_1")

            Then("it returns false, deletes the real vote row, and decrements the real counter back to 2") {
                helpful shouldBe false
                verify(exactly = 1) { eatsReviewHelpfulVoteRepository.delete(existingVote) }
                review.helpfulCount shouldBe 2
            }
        }

        When("toggling helpful on an unknown review id") {
            every { eatsReviewRepository.findById("does_not_exist") } returns Optional.empty()

            Then("it throws EatsReviewNotFoundException before ever touching a vote") {
                try {
                    service.toggleHelpful("viewer_1", "does_not_exist")
                    error("expected EatsReviewNotFoundException")
                } catch (e: EatsReviewNotFoundException) {
                    verify(exactly = 0) { eatsReviewHelpfulVoteRepository.save(any()) }
                }
            }
        }
    }

    Given("a real review someone wants to report") {
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        val eatsReviewRepository = mockk<EatsReviewRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val eatsReviewHelpfulVoteRepository = mockk<EatsReviewHelpfulVoteRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val eatsReviewReportRepository = mockk<EatsReviewReportRepository>()
        val service = EatsReviewService(eatsOrderRepository, eatsReviewRepository, merchantRepository, notificationRepository, pushNotificationService, eatsReviewHelpfulVoteRepository, rateLimiter, eatsReviewReportRepository)

        val review = EatsReview(
            id = "eats_review_1", orderId = "eats_order_1", buyerId = "buyer_1", restaurantId = "restaurant_1",
            riderId = "rider_1", restaurantRating = 1, restaurantComment = "abusive text", riderRating = null, riderComment = null,
        )

        When("the real review's own author tries to report it") {
            every { eatsReviewRepository.findById("eats_review_1") } returns Optional.of(review)

            Then("it throws OwnEatsReviewReportException before ever touching the report table") {
                try {
                    service.reportReview("buyer_1", "eats_review_1", EatsReviewReportReason.OTHER, null)
                    error("expected OwnEatsReviewReportException")
                } catch (e: OwnEatsReviewReportException) {
                    verify(exactly = 0) { eatsReviewReportRepository.save(any()) }
                }
            }
        }

        When("a real reporter who's already reported this review tries again") {
            every { eatsReviewRepository.findById("eats_review_1") } returns Optional.of(review)
            every { eatsReviewReportRepository.findByReviewIdAndReporterId("eats_review_1", "reporter_1") } returns
                EatsReviewReport(id = "eats_review_report_0", reviewId = "eats_review_1", reporterId = "reporter_1", reason = EatsReviewReportReason.OTHER)

            Then("it throws EatsReviewAlreadyReportedException") {
                try {
                    service.reportReview("reporter_1", "eats_review_1", EatsReviewReportReason.DEFAMATION, null)
                    error("expected EatsReviewAlreadyReportedException")
                } catch (e: EatsReviewAlreadyReportedException) {
                    verify(exactly = 0) { eatsReviewReportRepository.save(any()) }
                }
            }
        }

        When("a below-threshold real report comes in") {
            every { eatsReviewRepository.findById("eats_review_1") } returns Optional.of(review)
            every { eatsReviewReportRepository.findByReviewIdAndReporterId("eats_review_1", "reporter_1") } returns null
            every { eatsReviewReportRepository.save(any()) } answers { firstArg() }
            every { eatsReviewReportRepository.countByReviewId("eats_review_1") } returns 1

            val report = service.reportReview("reporter_1", "eats_review_1", EatsReviewReportReason.DEFAMATION, "  contains a real name  ")

            Then("it saves a real report but leaves the review visible") {
                report.reason shouldBe EatsReviewReportReason.DEFAMATION
                report.details shouldBe "contains a real name"
                review.hidden shouldBe false
                verify(exactly = 0) { eatsReviewRepository.save(any()) }
            }
        }

        When("the 3rd distinct reporter's report crosses the real threshold") {
            every { eatsReviewRepository.findById("eats_review_1") } returns Optional.of(review)
            every { eatsReviewReportRepository.findByReviewIdAndReporterId("eats_review_1", "reporter_3") } returns null
            every { eatsReviewReportRepository.save(any()) } answers { firstArg() }
            every { eatsReviewReportRepository.countByReviewId("eats_review_1") } returns 3
            every { eatsReviewRepository.save(any()) } answers { firstArg() }

            service.reportReview("reporter_3", "eats_review_1", EatsReviewReportReason.OBSCENE_OR_VIOLENT, null)

            Then("the review is silently hidden -- no notification, matching the sourced real Baemin behavior") {
                review.hidden shouldBe true
                verify(exactly = 0) { notificationRepository.save(any()) }
                verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any()) }
            }
        }

        When("reporting an unknown review id") {
            every { eatsReviewRepository.findById("does_not_exist") } returns Optional.empty()

            Then("it throws EatsReviewNotFoundException before ever touching the report table") {
                try {
                    service.reportReview("reporter_1", "does_not_exist", EatsReviewReportReason.OTHER, null)
                    error("expected EatsReviewNotFoundException")
                } catch (e: EatsReviewNotFoundException) {
                    verify(exactly = 0) { eatsReviewReportRepository.save(any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
