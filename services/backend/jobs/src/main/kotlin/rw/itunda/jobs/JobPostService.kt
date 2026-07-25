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
import rw.itunda.core.geo.NominatimGeocodingClient
import rw.itunda.core.repository.JobPostRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.trust.TrustScoreService
import rw.itunda.messaging.MessagingService
import rw.itunda.messaging.SelfConversationException
import java.math.BigDecimal
import java.time.Duration

class JobPostNotFoundException(message: String) : RuntimeException(message)
class InvalidJobPostException(message: String) : RuntimeException(message)
class JobPostNotOpenException(message: String) : RuntimeException(message)
class OwnJobPostException(message: String) : RuntimeException(message)
class InvalidJobCoordinatesException(message: String) : RuntimeException(message)
class JobsNeighborhoodNotSetException(message: String) : RuntimeException(message)
class WorkerNotFoundException(message: String) : RuntimeException(message)

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
    private val nominatimGeocodingClient: NominatimGeocodingClient,
    private val userRepository: UserRepository,
    private val trustScoreService: TrustScoreService,
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
        // Real bound, matching `deliveryAddress`'s own fix on the Eats/Commerce rows
        // the same day -- `title`/`description` are VARCHAR(200)/VARCHAR(2000), and
        // this DB's real STRICT_TRANS_TABLES mode throws a raw, unhandled 500 on an
        // over-length insert rather than truncating.
        if (trimmedTitle.length > 200 || trimmedDescription.length > 2000) {
            throw InvalidJobPostException("Title must be 200 characters or fewer, description 2000 or fewer")
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

        // Real hyperlocal neighborhood (2026-07-20) -- see MarketplaceService.
        // createListing's own doc comment for the full account; identical here.
        val neighborhood = if (latitude != null && longitude != null) {
            nominatimGeocodingClient.reverseGeocode(latitude, longitude)
        } else {
            userRepository.findById(posterId).orElse(null)?.neighborhood
        }

        return jobPostRepository.save(
            JobPost(
                id = "job_post_${java.util.UUID.randomUUID()}", posterId = posterId, category = category,
                title = trimmedTitle, description = trimmedDescription, payType = payType, payAmount = payAmount,
                latitude = latitude, longitude = longitude, neighborhood = neighborhood,
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

    // Real "Jobs I did" (2026-07-25) -- see JobPostRepository.
    // findByWorkerIdOrderByCreatedAtDesc's own doc comment for the full account.
    fun getMyWorkedPosts(workerId: String, pageable: Pageable): Page<JobPost> =
        jobPostRepository.findByWorkerIdOrderByCreatedAtDesc(workerId, pageable)

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see MarketplaceService.
    // myNeighborhood's own doc comment for the full account; identical shape here.
    fun myNeighborhood(callerUserId: String, category: String?, pageable: Pageable): Page<JobPost> {
        val caller = userRepository.findById(callerUserId).orElseThrow { JobPostNotFoundException("User not found") }
        val neighborhood = caller.neighborhood
            ?: throw JobsNeighborhoodNotSetException("Set your neighborhood first via POST /api/v1/auth/profile/neighborhood")
        return if (category.isNullOrBlank()) {
            jobPostRepository.findByStatusAndNeighborhoodOrderByCreatedAtDesc(JobPostStatus.OPEN, neighborhood, pageable)
        } else {
            jobPostRepository.findByStatusAndNeighborhoodAndCategoryOrderByCreatedAtDesc(JobPostStatus.OPEN, neighborhood, category, pageable)
        }
    }

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
    // Real optional worker identification (2026-07-24) -- see MarketplaceService.
    // markSold's own doc comment for the full account; identical shape here.
    fun markFilled(posterId: String, jobPostId: String, workerPhoneNumber: String? = null): JobPost {
        val post = requirePoster(posterId, jobPostId)
        if (post.status != JobPostStatus.OPEN) {
            throw JobPostNotOpenException("Only an open job post can be marked filled")
        }
        val trimmedPhone = workerPhoneNumber?.trim()
        if (!trimmedPhone.isNullOrEmpty()) {
            val worker = userRepository.findByPhoneNumber(trimmedPhone)
                ?: throw WorkerNotFoundException("No itunda account found for this phone number")
            if (worker.id == posterId) throw OwnJobPostException("You can't record yourself as the worker")
            post.workerId = worker.id
        }
        post.status = JobPostStatus.FILLED
        val saved = jobPostRepository.save(post)
        // Real Karrot-Score-style trust badge (2026-07-21) -- see TrustScoreService's own
        // doc comment; identical shape to MarketplaceService.markSold.
        trustScoreService.computeScore(posterId)
        // Real review-prompt system message (2026-07-25) -- see
        // MarketplaceService.markSold's own doc comment for the full sourced account;
        // identical shape here.
        post.workerId?.let { workerId ->
            val conversation = messagingService.startOrGetConversation(posterId, workerId)
            messagingService.sendMessage(
                posterId, conversation.id,
                "✅ Marked \"${post.title}\" as filled. If everything went well, leave a review so other neighbors know what to expect!",
            )
        }
        return saved
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
