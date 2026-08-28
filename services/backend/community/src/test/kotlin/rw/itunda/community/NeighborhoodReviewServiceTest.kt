package rw.itunda.community

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.NeighborhoodReview
import rw.itunda.core.repository.NeighborhoodReviewRepository
import java.time.Duration

class NeighborhoodReviewServiceTest : BehaviorSpec({

    Given("a real user who hasn't reviewed this neighborhood yet") {
        val neighborhoodReviewRepository = mockk<NeighborhoodReviewRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = NeighborhoodReviewService(neighborhoodReviewRepository, rateLimiter)

        every { neighborhoodReviewRepository.existsByUserIdAndNeighborhood("user_1", "Kimironko") } returns false
        val savedSlot = slot<NeighborhoodReview>()
        every { neighborhoodReviewRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("submitting a real review") {
            val review = service.submitReview("user_1", "Kimironko", 3, "  Quiet and safe, close to a good market.  ")

            Then("it trims and real-persists the review") {
                review.body shouldBe "Quiet and safe, close to a good market."
                review.residencyYears shouldBe 3
                review.neighborhood shouldBe "Kimironko"
            }
        }

        When("submitting with negative residency years") {
            Then("it throws InvalidNeighborhoodReviewException") {
                try {
                    service.submitReview("user_1", "Kimironko", -1, "Body")
                    error("expected InvalidNeighborhoodReviewException")
                } catch (e: InvalidNeighborhoodReviewException) {
                    // expected
                }
            }
        }

        When("submitting a blank review") {
            Then("it throws InvalidNeighborhoodReviewException") {
                try {
                    service.submitReview("user_1", "Kimironko", null, "   ")
                    error("expected InvalidNeighborhoodReviewException")
                } catch (e: InvalidNeighborhoodReviewException) {
                    // expected
                }
            }
        }

        When("submitting a review over the real 1000-char DB column bound") {
            Then("it throws InvalidNeighborhoodReviewException rather than risking a raw DB insert failure") {
                try {
                    service.submitReview("user_1", "Kimironko", null, "x".repeat(1001))
                    error("expected InvalidNeighborhoodReviewException")
                } catch (e: InvalidNeighborhoodReviewException) {
                    // expected
                }
            }
        }

        When("a real user exceeds the real review-creation rate limit") {
            every { rateLimiter.checkLimit("community:neighborhood-review:user_1", limit = 10, window = Duration.ofHours(1)) } throws
                RateLimitExceededException("Too many requests")

            Then("it real-propagates RateLimitExceededException") {
                try {
                    service.submitReview("user_1", "Kimironko", null, "Body")
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    // expected
                }
            }
        }
    }

    Given("a real user who already reviewed this neighborhood") {
        val neighborhoodReviewRepository = mockk<NeighborhoodReviewRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = NeighborhoodReviewService(neighborhoodReviewRepository, rateLimiter)
        every { neighborhoodReviewRepository.existsByUserIdAndNeighborhood("user_1", "Kimironko") } returns true

        When("submitting a second review for the same neighborhood") {
            Then("it throws NeighborhoodReviewAlreadySubmittedException, never a second real row") {
                try {
                    service.submitReview("user_1", "Kimironko", null, "Body")
                    error("expected NeighborhoodReviewAlreadySubmittedException")
                } catch (e: NeighborhoodReviewAlreadySubmittedException) {
                    // expected
                }
                verify(exactly = 0) { neighborhoodReviewRepository.save(any()) }
            }
        }
    }

    Given("real reviews for a neighborhood") {
        val neighborhoodReviewRepository = mockk<NeighborhoodReviewRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = NeighborhoodReviewService(neighborhoodReviewRepository, rateLimiter)
        val reviews = listOf(
            NeighborhoodReview(id = "r1", userId = "u1", neighborhood = "Kimironko", body = "Great area"),
        )
        every { neighborhoodReviewRepository.findByNeighborhoodOrderByCreatedAtDesc("Kimironko") } returns reviews

        When("fetching reviews for that neighborhood") {
            val result = service.getReviews("Kimironko")

            Then("it returns the real reviews") {
                result shouldBe reviews
            }
        }
    }
})
