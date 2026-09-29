package rw.itunda.merchant

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantBooking
import rw.itunda.core.domain.MerchantBookingReview
import rw.itunda.core.domain.MerchantBookingStatus
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.MerchantBookingRepository
import rw.itunda.core.repository.MerchantBookingReviewRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.RatingSummaryProjection
import java.time.LocalDate
import java.time.LocalTime
import java.util.Optional

class MerchantBookingReviewServiceTest : BehaviorSpec({

    Given("a real customer reviewing a real completed booking") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantBookingRepository = mockk<MerchantBookingRepository>()
        val merchantBookingReviewRepository = mockk<MerchantBookingReviewRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = MerchantBookingReviewService(
            merchantRepository, merchantBookingRepository, merchantBookingReviewRepository,
            notificationRepository, pushNotificationService, rateLimiter,
        )

        val booking = MerchantBooking(
            id = "booking_1", merchantId = "merchant_1", customerId = "customer_1", serviceId = "service_1",
            serviceName = "Haircut", bookingDate = LocalDate.now(), startTime = LocalTime.NOON, endTime = LocalTime.NOON,
            status = MerchantBookingStatus.COMPLETED,
        )

        When("submitting a valid review for the first time") {
            every { merchantBookingRepository.findById("booking_1") } returns Optional.of(booking)
            every { merchantBookingReviewRepository.findByBookingId("booking_1") } returns null
            every { merchantBookingReviewRepository.save(any()) } answers { firstArg() }

            val review = service.submitReview("customer_1", "booking_1", 5, "Great service")

            Then("it real-creates the review tied to the real booking/merchant") {
                review.bookingId shouldBe "booking_1"
                review.merchantId shouldBe "merchant_1"
                review.customerId shouldBe "customer_1"
                review.rating shouldBe 5
            }

            // Real gap found live (2026-09-14, sibling-asymmetry sweep): this class had
            // no RateLimiter at all -- EatsReviewService.submitReview's own doc comment
            // documents a real live incident fixing the identical gap for the exact
            // same review-submission shape this class never got.
            Then("the per-customer review-submission rate limit is enforced") {
                verify(exactly = 1) { rateLimiter.checkLimit("merchant:booking-review:submit:customer_1", limit = any(), window = any()) }
            }
        }

        When("submitting a rating outside 1..5") {
            Then("it throws InvalidBookingRatingException before ever touching the booking") {
                try {
                    service.submitReview("customer_1", "booking_1", 6, null)
                    error("expected InvalidBookingRatingException")
                } catch (e: InvalidBookingRatingException) {
                    verify(exactly = 0) { merchantBookingRepository.findById(any()) }
                }
            }
        }

        When("someone who isn't the real customer of the booking tries to review it") {
            every { merchantBookingRepository.findById("booking_1") } returns Optional.of(booking)

            Then("it throws MerchantBookingReviewNotFoundException, a real 404 not 403") {
                try {
                    service.submitReview("stranger", "booking_1", 5, null)
                    error("expected MerchantBookingReviewNotFoundException")
                } catch (e: MerchantBookingReviewNotFoundException) {
                    // expected
                }
            }
        }

        When("the booking is not yet COMPLETED") {
            val pending = MerchantBooking(
                id = "booking_2", merchantId = "merchant_1", customerId = "customer_1", serviceId = "service_1",
                serviceName = "Haircut", bookingDate = LocalDate.now(), startTime = LocalTime.NOON, endTime = LocalTime.NOON,
                status = MerchantBookingStatus.REQUESTED,
            )
            every { merchantBookingRepository.findById("booking_2") } returns Optional.of(pending)

            Then("it throws BookingNotCompletedException") {
                try {
                    service.submitReview("customer_1", "booking_2", 5, null)
                    error("expected BookingNotCompletedException")
                } catch (e: BookingNotCompletedException) {
                    // expected
                }
            }
        }

        When("the booking has already been reviewed") {
            val existingReview = MerchantBookingReview(
                id = "review_1", bookingId = "booking_1", merchantId = "merchant_1", customerId = "customer_1",
                serviceName = "Haircut", rating = 4, comment = null,
            )
            every { merchantBookingRepository.findById("booking_1") } returns Optional.of(booking)
            every { merchantBookingReviewRepository.findByBookingId("booking_1") } returns existingReview

            Then("it throws BookingAlreadyReviewedException") {
                try {
                    service.submitReview("customer_1", "booking_1", 5, null)
                    error("expected BookingAlreadyReviewedException")
                } catch (e: BookingAlreadyReviewedException) {
                    // expected
                }
            }
        }
    }

    Given("a real merchant owner replying to a real review of their own business") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantBookingRepository = mockk<MerchantBookingRepository>()
        val merchantBookingReviewRepository = mockk<MerchantBookingReviewRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = MerchantBookingReviewService(
            merchantRepository, merchantBookingRepository, merchantBookingReviewRepository,
            notificationRepository, pushNotificationService, rateLimiter,
        )

        val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_1", accountId = "account_1", businessName = "Kigali Coffee", status = MerchantStatus.ACTIVE)
        val review = MerchantBookingReview(
            id = "review_1", bookingId = "booking_1", merchantId = "merchant_1", customerId = "customer_1",
            serviceName = "Haircut", rating = 5, comment = "Great service",
        )

        When("posting a valid reply") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
            every { merchantBookingReviewRepository.findById("review_1") } returns Optional.of(review)
            every { merchantBookingReviewRepository.save(any()) } answers { firstArg() }
            every { notificationRepository.save(any()) } answers { firstArg() }

            val result = service.replyToReview("owner_1", "review_1", "Thank you for visiting!")

            Then("it real-saves the reply, real-notifies in-app, and real-pushes the customer") {
                result.ownerReply shouldBe "Thank you for visiting!"
                result.ownerRepliedAt shouldNotBe null
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "customer_1" && it.type == "MERCHANT_BOOKING_REVIEW_REPLY" }) }
                verify(exactly = 1) { pushNotificationService.sendToUser("customer_1", any(), "Thank you for visiting!", any()) }
            }
        }

        When("replying with an empty reply") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant

            Then("it throws InvalidReviewReplyException before ever touching the review") {
                try {
                    service.replyToReview("owner_1", "review_1", "   ")
                    error("expected InvalidReviewReplyException")
                } catch (e: InvalidReviewReplyException) {
                    verify(exactly = 0) { merchantBookingReviewRepository.findById(any()) }
                }
            }
        }

        When("someone who isn't a registered merchant tries to reply") {
            every { merchantRepository.findByOwnerUserId("stranger") } returns null

            Then("it throws MerchantNotFoundException") {
                try {
                    service.replyToReview("stranger", "review_1", "Thanks!")
                    error("expected MerchantNotFoundException")
                } catch (e: MerchantNotFoundException) {
                    // expected
                }
            }
        }

        When("a real different merchant owner tries to reply to a review that isn't theirs") {
            val otherMerchant = Merchant(id = "merchant_2", ownerUserId = "owner_2", accountId = "account_2", businessName = "Other Shop", status = MerchantStatus.ACTIVE)
            every { merchantRepository.findByOwnerUserId("owner_2") } returns otherMerchant
            every { merchantBookingReviewRepository.findById("review_1") } returns Optional.of(review)

            Then("it throws MerchantBookingReviewNotFoundException, a real 404 not 403") {
                try {
                    service.replyToReview("owner_2", "review_1", "Thanks!")
                    error("expected MerchantBookingReviewNotFoundException")
                } catch (e: MerchantBookingReviewNotFoundException) {
                    verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any()) }
                }
            }
        }
    }

    Given("a real customer/merchant reading review data") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantBookingRepository = mockk<MerchantBookingRepository>()
        val merchantBookingReviewRepository = mockk<MerchantBookingReviewRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = MerchantBookingReviewService(
            merchantRepository, merchantBookingRepository, merchantBookingReviewRepository,
            notificationRepository, pushNotificationService, rateLimiter,
        )

        When("checking a merchant's real rating summary") {
            val projection = object : RatingSummaryProjection {
                override val average: Double = 4.5
                override val count: Long = 2L
            }
            every { merchantBookingReviewRepository.getMerchantRatingSummary("merchant_1") } returns projection

            val summary = service.getMerchantRating("merchant_1")

            Then("it returns the real average/count") {
                summary.average shouldBe 4.5
                summary.count shouldBe 2L
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
