package rw.itunda.jobs

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Conversation
import rw.itunda.core.domain.JobPayType
import rw.itunda.core.domain.JobPost
import rw.itunda.core.domain.JobPostStatus
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.repository.JobPostRepository
import rw.itunda.messaging.MessagingService
import rw.itunda.messaging.SelfConversationException
import java.math.BigDecimal
import java.time.Duration

class JobPostNotFoundException(message: String) : RuntimeException(message)
class InvalidJobPostException(message: String) : RuntimeException(message)
class JobPostNotOpenException(message: String) : RuntimeException(message)
class OwnJobPostException(message: String) : RuntimeException(message)
class InvalidJobCoordinatesException(message: String) : RuntimeException(message)

data class JobCategory(val id: String, val label: String)

/**
 * A real 당근알바 (Danggeun/Karrot "Alba")-style local job board -- see `JobPost`'s own
 * doc comment for the full account of why this is a distinct surface from Marketplace,
 * explicitly named by the user alongside 당근생활 (now real as Community) and
 * 당근부동산 (real estate, still open) as three distinct neighborhood-services
 * products.
 *
 * v1, honestly scoped, mirroring `MarketplaceService`'s own v1 shape: real posts, real
 * category browse, a real opt-in Haversine "near me" browse, real "message poster"
 * (the real apply mechanism, reusing `MessagingService` unmodified), real
 * poster-only mark-filled/remove. No structured "application" object, no
 * accept/reject-a-worker flow -- a real conversation with the poster covers v1, matching
 * how Marketplace's own price-offer negotiation was a later addition, not required to
 * ship a real, usable job board.
 */
@Service
class JobPostService(
    private val jobPostRepository: JobPostRepository,
    private val rateLimiter: RateLimiter,
    private val messagingService: MessagingService,
) {
    companion object {
        val CATEGORIES = listOf(
            JobCategory("delivery", "Delivery"),
            JobCategory("cleaning", "Cleaning"),
            JobCategory("tutoring", "Tutoring"),
            JobCategory("event_staff", "Event staff"),
            JobCategory("moving_help", "Moving help"),
            JobCategory("pet_care", "Pet care"),
            JobCategory("other", "Other"),
        )
        private val CATEGORY_IDS = CATEGORIES.map { it.id }.toSet()
    }

    private fun requirePoster(posterId: String, jobPostId: String): JobPost {
        val post = jobPostRepository.findById(jobPostId).orElseThrow { JobPostNotFoundException("Job post not found") }
        if (post.posterId != posterId) {
            // Same "don't reveal a resource exists to someone who shouldn't act on it"
            // discipline MarketplaceService.requireOwner already established.
            throw JobPostNotFoundException("Job post not found")
        }
        return post
    }

    @Transactional
    fun createPost(
        posterId: String,
        category: String,
        title: String,
        description: String,
        payType: JobPayType,
        payAmount: BigDecimal,
        latitude: Double? = null,
        longitude: Double? = null,
    ): JobPost {
        val trimmedTitle = title.trim()
        val trimmedDescription = description.trim()
        if (trimmedTitle.isEmpty() || trimmedDescription.isEmpty()) {
            throw InvalidJobPostException("Title and description are both required")
        }
        if (category !in CATEGORY_IDS) {
            throw InvalidJobPostException("Unknown category")
        }
        if (payAmount <= BigDecimal.ZERO) {
            throw InvalidJobPostException("Pay amount must be greater than zero")
        }
        if ((latitude == null) != (longitude == null)) {
            throw InvalidJobCoordinatesException("Both latitude and longitude are required together")
        }
        if (latitude != null && longitude != null && !GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidJobCoordinatesException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        // Real anti-spam limit, same 10/hour convention MarketplaceService.createListing/
        // CommunityService.createPost already established.
        rateLimiter.checkLimit("jobs:post:$posterId", limit = 10, window = Duration.ofHours(1))

        return jobPostRepository.save(
            JobPost(
                id = "job_post_${java.util.UUID.randomUUID()}", posterId = posterId, category = category,
                title = trimmedTitle, description = trimmedDescription, payType = payType, payAmount = payAmount,
                latitude = latitude, longitude = longitude,
            ),
        )
    }

    fun browse(pageable: Pageable, category: String?): Page<JobPost> =
        if (category.isNullOrBlank()) {
            jobPostRepository.findByStatusOrderByCreatedAtDesc(JobPostStatus.OPEN, pageable)
        } else {
            jobPostRepository.findByStatusAndCategoryOrderByCreatedAtDesc(JobPostStatus.OPEN, category, pageable)
        }

    fun getMyPosts(posterId: String, pageable: Pageable): Page<JobPost> =
        jobPostRepository.findByPosterIdOrderByCreatedAtDesc(posterId, pageable)

    // Real opt-in "near me" browse -- same shape MarketplaceService.nearby's own v1 (and
    // CommunityService.nearby) already established.
    fun nearby(latitude: Double, longitude: Double, radiusKm: Double, pageable: Pageable): Page<JobPost> {
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidJobCoordinatesException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        if (radiusKm <= 0.0) {
            throw InvalidJobCoordinatesException("radiusKm must be greater than zero")
        }
        val sorted = jobPostRepository.findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(JobPostStatus.OPEN)
            .map { it to GeoUtils.haversineKm(latitude, longitude, it.latitude!!, it.longitude!!) }
            .filter { (_, distanceKm) -> distanceKm <= radiusKm }
            .sortedBy { (_, distanceKm) -> distanceKm }
            .map { (post, _) -> post }

        val start = (pageable.pageNumber * pageable.pageSize).coerceAtMost(sorted.size)
        val end = (start + pageable.pageSize).coerceAtMost(sorted.size)
        return PageImpl(sorted.subList(start, end), pageable, sorted.size.toLong())
    }

    // Any status, not just OPEN -- a worker who already messaged about a now-FILLED post
    // should still be able to open it, matching Marketplace's own getListing precedent.
    fun getPost(jobPostId: String): JobPost =
        jobPostRepository.findById(jobPostId).orElseThrow { JobPostNotFoundException("Job post not found") }

    @Transactional
    fun markFilled(posterId: String, jobPostId: String): JobPost {
        val post = requirePoster(posterId, jobPostId)
        if (post.status != JobPostStatus.OPEN) {
            throw JobPostNotOpenException("Only an open job post can be marked filled")
        }
        post.status = JobPostStatus.FILLED
        return jobPostRepository.save(post)
    }

    @Transactional
    fun removePost(posterId: String, jobPostId: String): JobPost {
        val post = requirePoster(posterId, jobPostId)
        post.status = JobPostStatus.REMOVED
        return jobPostRepository.save(post)
    }

    /** Real "message poster" -- the real apply mechanism for this job board. Reuses
     * `MessagingService.startOrGetConversation` completely unmodified, the exact same
     * "compose a real, already-proven service rather than duplicating its logic"
     * discipline `MarketplaceService.contactSeller` already established. */
    fun contactPoster(applicantId: String, jobPostId: String): Conversation {
        val post = getPost(jobPostId)
        try {
            return messagingService.startOrGetConversation(applicantId, post.posterId)
        } catch (e: SelfConversationException) {
            throw OwnJobPostException("This is your own job post")
        }
    }
}
