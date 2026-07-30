package rw.itunda.rideshare

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.RideTripReview
import rw.itunda.core.domain.RideTripStatus
import rw.itunda.core.repository.RideTripRepository
import rw.itunda.core.repository.RideTripReviewRepository
import java.util.UUID

class RideTripNotYetCompletedException(message: String) : RuntimeException(message)
class RideTripAlreadyReviewedException(message: String) : RuntimeException(message)
class InvalidRideRatingException(message: String) : RuntimeException(message)

data class RideDriverRatingSummary(val average: Double?, val count: Long)

/**
 * Real Kakao T-style post-trip driver rating (item 213) -- see `RideTripReview.kt`'s own
 * doc comment for the full account. Mirrors `EatsReviewService.submitReview`'s exact
 * ownership + state discipline: only the real passenger of a trip that's actually
 * `COMPLETED` can review it (real 404, not 403, on a mismatched passenger -- same
 * "don't reveal a resource exists to someone who shouldn't see it" discipline every
 * other IDOR check in this backend uses), and only once per trip, backed by a real DB
 * unique constraint on `trip_id`, not just the application-level check alone.
 */
@Service
class RideTripReviewService(
    private val rideTripRepository: RideTripRepository,
    private val rideTripReviewRepository: RideTripReviewRepository,
) {
    @Transactional
    fun submitReview(passengerId: String, tripId: String, rating: Int, comment: String?): RideTripReview {
        if (rating !in 1..5) {
            throw InvalidRideRatingException("Rating must be between 1 and 5")
        }
        val trip = rideTripRepository.findById(tripId).orElseThrow { RideTripNotFoundException("Trip not found") }
        if (trip.passengerId != passengerId) {
            throw RideTripNotFoundException("Trip not found")
        }
        if (trip.status != RideTripStatus.COMPLETED) {
            throw RideTripNotYetCompletedException("Only a completed trip can be reviewed")
        }
        val driverId = trip.driverId ?: throw RideTripNotYetCompletedException("Only a completed trip can be reviewed")
        if (rideTripReviewRepository.findByTripId(tripId) != null) {
            throw RideTripAlreadyReviewedException("This trip has already been reviewed")
        }
        return rideTripReviewRepository.save(
            RideTripReview(
                id = "ride_review_${UUID.randomUUID()}", tripId = tripId, passengerId = passengerId, driverId = driverId,
                // Real bound -- comment is VARCHAR(1000) under this DB's real
                // STRICT_TRANS_TABLES mode, matching EatsReviewService.submitReview's own
                // identical over-length-insert fix.
                rating = rating, comment = comment?.trim()?.take(1000)?.ifBlank { null },
            ),
        )
    }

    fun getDriverReviews(driverId: String, pageable: Pageable): Page<RideTripReview> =
        rideTripReviewRepository.findByDriverIdOrderByCreatedAtDesc(driverId, pageable)

    fun getDriverRating(driverId: String): RideDriverRatingSummary {
        val summary = rideTripReviewRepository.getDriverRatingSummary(driverId)
        return RideDriverRatingSummary(summary.average, summary.count)
    }
}
