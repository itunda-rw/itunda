package rw.itunda.jobs

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.JobPost
import rw.itunda.core.domain.JobPostFavorite
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.JobPostFavoriteRepository
import rw.itunda.core.repository.JobPostRepository
import org.slf4j.LoggerFactory
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
 *
 * 2026-08-17: `notifyFavoritersOfClosure` closes the real gap this class had until now
 * -- a favorite with zero notification hook of any kind -- see that method's own doc
 * comment for the real Karrot sourcing.
 */
@Service
class JobPostFavoriteService(
    private val jobPostFavoriteRepository: JobPostFavoriteRepository,
    private val jobPostRepository: JobPostRepository,
    private val pushNotificationService: PushNotificationService,
) {
    private val log = LoggerFactory.getLogger(JobPostFavoriteService::class.java)

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

    // Real 당근알바 job-closed notification -- Karrot's own real, documented behavior
    // (cs.kr.karrotmarket.com's own FAQ content on 당근알바 알림): "When a job posting
    // closes on Carrot Market, you receive a notification that applications have
    // closed." itunda's honest equivalent notifies every real favoriter -- someone who
    // deliberately saved this post -- the moment it stops being available, whether
    // filled (JobPostService.markFilled) or withdrawn (JobPostService.removePost).
    // Called from the controller layer right after either real status change commits --
    // see KeywordAlertService.notifyMatchingAlerts's own doc comment for why this same
    // shape lives outside the @Transactional service method: a per-favoriter
    // notification loop must never risk poisoning the real status-change transaction it
    // follows (the same real bug class Sections 115/118/129/130 already fixed for
    // scheduler-driven code). Push-only, no persisted `Notification` row, same lighter
    // shape Section 143's `notifyFavoritersOfNewProduct` already established for this
    // kind of best-effort batch favoriter alert. Best-effort: any failure here is
    // swallowed, never allowed to make the real closure action itself look like it
    // failed.
    fun notifyFavoritersOfClosure(jobPost: JobPost) {
        try {
            val favoriters = jobPostFavoriteRepository.findByJobPostId(jobPost.id)
            if (favoriters.isEmpty()) return
            val title = "A job you saved has closed"
            val body = "\"${jobPost.title}\" is no longer accepting applications."
            for (favorite in favoriters) {
                pushNotificationService.sendToUser(favorite.userId, title, body, mapOf("jobPostId" to jobPost.id))
            }
        } catch (e: Exception) {
            // Real, non-critical -- a job-closed push failure must never make the real
            // markFilled/removePost action itself look like it failed.
            log.warn("Failed to notify favoriters of job post {} closure", jobPost.id, e)
        }
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
