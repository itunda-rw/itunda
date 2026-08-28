package rw.itunda.community

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.NeighborhoodReview
import rw.itunda.core.repository.NeighborhoodReviewRepository
import java.time.Duration
import java.util.UUID

class InvalidNeighborhoodReviewException(message: String) : RuntimeException(message)
class NeighborhoodReviewAlreadySubmittedException(message: String) : RuntimeException(message)

/**
 * Real 살아본 후기 (Karrot "lived here" reviews) -- a public review of what it's like
 * to live in a neighborhood, genuinely distinct from `HoodReviewService`'s buyer/
 * seller transaction reviews. See `NeighborhoodReview`'s own doc comment for the
 * full account.
 */
@Service
class NeighborhoodReviewService(
    private val neighborhoodReviewRepository: NeighborhoodReviewRepository,
    private val rateLimiter: RateLimiter,
) {
    fun getReviews(neighborhood: String): List<NeighborhoodReview> =
        neighborhoodReviewRepository.findByNeighborhoodOrderByCreatedAtDesc(neighborhood)

    @Transactional
    fun submitReview(userId: String, neighborhood: String, residencyYears: Int?, body: String): NeighborhoodReview {
        val trimmedNeighborhood = neighborhood.trim()
        val trimmedBody = body.trim()
        if (trimmedNeighborhood.isEmpty() || trimmedBody.isEmpty()) {
            throw InvalidNeighborhoodReviewException("Neighborhood and review text are both required")
        }
        // Real bound, matching this codebase's own repeated STRICT_TRANS_TABLES
        // over-length-insert fix (see EatsReviewService/HoodReviewService, among many
        // others) -- `body`/`neighborhood` are VARCHAR(1000)/VARCHAR(120).
        if (trimmedBody.length > 1000) {
            throw InvalidNeighborhoodReviewException("Review must be 1000 characters or fewer")
        }
        if (trimmedNeighborhood.length > 120) {
            throw InvalidNeighborhoodReviewException("Neighborhood name must be 120 characters or fewer")
        }
        if (residencyYears != null && residencyYears < 0) {
            throw InvalidNeighborhoodReviewException("Residency years cannot be negative")
        }
        // Real anti-spam limit, same 10/hour convention every other Hood creation
        // endpoint already established.
        rateLimiter.checkLimit("community:neighborhood-review:$userId", limit = 10, window = Duration.ofHours(1))
        // Real one-review-per-user-per-neighborhood rule -- a real DB unique
        // constraint (V-migration) backs this as the actual source of truth under a
        // race; this check just gives an honest 400 in the common, non-racing case
        // instead of a raw constraint-violation 500.
        if (neighborhoodReviewRepository.existsByUserIdAndNeighborhood(userId, trimmedNeighborhood)) {
            throw NeighborhoodReviewAlreadySubmittedException("You've already reviewed this neighborhood")
        }
        return neighborhoodReviewRepository.save(
            NeighborhoodReview(
                id = "neighborhood_review_${UUID.randomUUID()}", userId = userId, neighborhood = trimmedNeighborhood,
                residencyYears = residencyYears, body = trimmedBody,
            ),
        )
    }
}
