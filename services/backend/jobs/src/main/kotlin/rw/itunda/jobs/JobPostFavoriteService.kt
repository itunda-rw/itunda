package rw.itunda.jobs

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.JobPostFavorite
import rw.itunda.core.repository.JobPostFavoriteRepository
import rw.itunda.core.repository.JobPostRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class FavoriteJobPostNotFoundException(message: String) : RuntimeException(message)

data class FavoriteJobPost(
    val jobPostId: String,
    val title: String,
    val payAmount: BigDecimal,
    val category: String,
    val favoritedAt: Instant,
)

/**
 * Real 당근알바 job-post wishlist -- closes a `docs/DESIGN_REFERENCES.md`-named Hood
 * gap: Marketplace listings already got a real wishlist (2026-07-21,
 * `ListingFavoriteService`) but Jobs never did. Mirrors `ListingFavoriteService`'s
 * exact shape and idempotency discipline field-for-field.
 *
 * `addFavorite` is deliberately idempotent (favoriting an already-favorited post just
 * returns the existing row rather than a 409); `removeFavorite` is a silent no-op for
 * something that was never favorited -- same reasoning `ListingFavoriteService` already
 * established.
 */
@Service
class JobPostFavoriteService(
    private val jobPostFavoriteRepository: JobPostFavoriteRepository,
    private val jobPostRepository: JobPostRepository,
) {
    @Transactional
    fun addFavorite(userId: String, jobPostId: String): JobPostFavorite {
        jobPostRepository.findById(jobPostId).orElseThrow { FavoriteJobPostNotFoundException("Job post not found") }
        jobPostFavoriteRepository.findByUserIdAndJobPostId(userId, jobPostId)?.let { return it }
        return jobPostFavoriteRepository.save(
            JobPostFavorite(id = "job_post_favorite_${UUID.randomUUID()}", userId = userId, jobPostId = jobPostId),
        )
    }

    @Transactional
    fun removeFavorite(userId: String, jobPostId: String) {
        jobPostFavoriteRepository.deleteByUserIdAndJobPostId(userId, jobPostId)
    }

    // Real batch-resolve of job-post info via one findAllById call, the same
    // N+1-avoiding shape ListingFavoriteService.getMyFavorites already established.
    fun getMyFavorites(userId: String, pageable: Pageable): Page<FavoriteJobPost> {
        val page = jobPostFavoriteRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
        val postsById = jobPostRepository.findAllById(page.content.map { it.jobPostId }).associateBy { it.id }
        return page.map { favorite ->
            val post = postsById[favorite.jobPostId]
            FavoriteJobPost(
                jobPostId = favorite.jobPostId,
                title = post?.title ?: "Job post no longer available",
                payAmount = post?.payAmount ?: BigDecimal.ZERO,
                category = post?.category ?: "",
                favoritedAt = favorite.createdAt,
            )
        }
    }
}
