package rw.itunda.rideshare

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.RideTrip
import rw.itunda.core.domain.RideTripReview
import rw.itunda.core.domain.RideTripStatus
import rw.itunda.core.repository.RatingSummaryProjection
import rw.itunda.core.repository.RideTripRepository
import rw.itunda.core.repository.RideTripReviewRepository
import java.math.BigDecimal
import java.util.Optional

/**
 * First test coverage for real Kakao T-style post-trip driver ratings (item 213) --
 * mirrors EatsReviewServiceTest's own established mocking conventions for this
 * codebase's post-order-completion review shape.
 */
class RideTripReviewServiceTest : BehaviorSpec({

    Given("a real completed trip") {
        val rideTripRepository = mockk<RideTripRepository>()
        val rideTripReviewRepository = mockk<RideTripReviewRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = RideTripReviewService(rideTripRepository, rideTripReviewRepository, rateLimiter)

        val completedTrip = RideTrip(
            id = "ride_trip_1", passengerId = "passenger_1", driverId = "driver_1", pickupAddress = "A",
            pickupLatitude = -1.95, pickupLongitude = 30.06, dropoffAddress = "B", dropoffLatitude = -1.96, dropoffLongitude = 30.09,
            distanceKm = BigDecimal("3.5"), fare = BigDecimal("1875"), platformFee = BigDecimal("28.13"), transactionId = "txn_1",
            status = RideTripStatus.COMPLETED,
        )

        When("the real passenger submits a real review") {
            every { rideTripRepository.findById("ride_trip_1") } returns Optional.of(completedTrip)
            every { rideTripReviewRepository.findByTripId("ride_trip_1") } returns null
            val savedSlot = slot<RideTripReview>()
            every { rideTripReviewRepository.save(capture(savedSlot)) } answers { firstArg() }

            val review = service.submitReview("passenger_1", "ride_trip_1", 5, "  Great driver!  ")

            Then("it trims the comment, resolves the real driver from the trip, and persists it") {
                review.driverId shouldBe "driver_1"
                review.rating shouldBe 5
                review.comment shouldBe "Great driver!"
            }

            // Real gap found live (2026-09-14, sibling-asymmetry sweep): this class's
            // own doc comment says it mirrors EatsReviewService.submitReview's exact
            // ownership + state discipline, but only copied the IDOR/state checks, not
            // the rate limiter EatsReviewService added after its own real live incident.
            Then("the per-passenger review-submission rate limit is enforced") {
                verify(exactly = 1) { rateLimiter.checkLimit("rideshare:trip-review:submit:passenger_1", limit = any(), window = any()) }
            }
        }

        When("submitting a comment longer than the real 1000-char DB column bound") {
            every { rideTripRepository.findById("ride_trip_1") } returns Optional.of(completedTrip)
            every { rideTripReviewRepository.findByTripId("ride_trip_1") } returns null
            val savedSlot = slot<RideTripReview>()
            every { rideTripReviewRepository.save(capture(savedSlot)) } answers { firstArg() }
            val longComment = "x".repeat(1500)

            val review = service.submitReview("passenger_1", "ride_trip_1", 5, longComment)

            Then("it truncates the comment to 1000 chars rather than risking a raw DB insert failure") {
                review.comment?.length shouldBe 1000
            }
        }

        When("submitting a rating outside 1-5") {
            Then("it throws InvalidRideRatingException before even looking up the trip") {
                try {
                    service.submitReview("passenger_1", "ride_trip_1", 6, null)
                    error("expected InvalidRideRatingException")
                } catch (e: InvalidRideRatingException) {
                    // expected
                }
            }
        }

        When("a stranger (not the real passenger) tries to review the trip") {
            every { rideTripRepository.findById("ride_trip_1") } returns Optional.of(completedTrip)

            Then("it throws RideTripNotFoundException, not a 403 that would confirm the trip exists") {
                try {
                    service.submitReview("stranger", "ride_trip_1", 5, null)
                    error("expected RideTripNotFoundException")
                } catch (e: RideTripNotFoundException) {
                    // expected
                }
            }
        }

        When("the real passenger tries to review a trip that isn't COMPLETED yet") {
            val requestedTrip = RideTrip(
                id = "ride_trip_2", passengerId = "passenger_1", pickupAddress = "A", pickupLatitude = -1.95, pickupLongitude = 30.06,
                dropoffAddress = "B", dropoffLatitude = -1.96, dropoffLongitude = 30.09, distanceKm = BigDecimal("3.5"),
                fare = BigDecimal("1875"), platformFee = BigDecimal("28.13"), transactionId = "txn_2", status = RideTripStatus.REQUESTED,
            )
            every { rideTripRepository.findById("ride_trip_2") } returns Optional.of(requestedTrip)

            Then("it throws RideTripNotYetCompletedException") {
                try {
                    service.submitReview("passenger_1", "ride_trip_2", 5, null)
                    error("expected RideTripNotYetCompletedException")
                } catch (e: RideTripNotYetCompletedException) {
                    // expected
                }
            }
        }

        When("the real passenger tries to review the same trip twice") {
            every { rideTripRepository.findById("ride_trip_1") } returns Optional.of(completedTrip)
            every { rideTripReviewRepository.findByTripId("ride_trip_1") } returns RideTripReview(
                id = "ride_review_1", tripId = "ride_trip_1", passengerId = "passenger_1", driverId = "driver_1", rating = 5, comment = null,
            )

            Then("it throws RideTripAlreadyReviewedException") {
                try {
                    service.submitReview("passenger_1", "ride_trip_1", 4, null)
                    error("expected RideTripAlreadyReviewedException")
                } catch (e: RideTripAlreadyReviewedException) {
                    // expected
                }
            }
        }

        When("fetching a real driver's aggregate rating") {
            every { rideTripReviewRepository.getDriverRatingSummary("driver_1") } returns object : RatingSummaryProjection {
                override val average = 4.5
                override val count = 2L
            }

            val summary = service.getDriverRating("driver_1")

            Then("it returns the real average and count") {
                summary.average shouldBe 4.5
                summary.count shouldBe 2L
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
