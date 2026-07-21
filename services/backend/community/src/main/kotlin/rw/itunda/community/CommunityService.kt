package rw.itunda.community

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.CommunityComment
import rw.itunda.core.domain.CommunityLike
import rw.itunda.core.domain.CommunityPost
import rw.itunda.core.domain.CommunityPostStatus
import rw.itunda.core.domain.Notification
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.geo.NominatimGeocodingClient
import rw.itunda.core.repository.CommunityCommentRepository
import rw.itunda.core.repository.CommunityLikeRepository
import rw.itunda.core.repository.CommunityPostRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID

class CommunityPostNotFoundException(message: String) : RuntimeException(message)
class CommunityPostNotOwnedException(message: String) : RuntimeException(message)
class InvalidCommunityPostException(message: String) : RuntimeException(message)
class InvalidCommunityCommentException(message: String) : RuntimeException(message)
class InvalidCommunityCoordinatesException(message: String) : RuntimeException(message)
class CommunityNeighborhoodNotSetException(message: String) : RuntimeException(message)

data class CommunityCategory(val id: String, val label: String)

data class CommunityPostDetail(val post: CommunityPost, val authorName: String, val likedByMe: Boolean)
data class CommunityCommentWithAuthor(val comment: CommunityComment, val authorName: String)

/**
 * A real 동네생활 (Danggeun/Karrot "Neighborhood Life")-style community board -- see
 * `CommunityPost`'s own doc comment for the full account of why this is a distinct
 * surface from Marketplace, explicitly named by the user alongside 당근알바
 * (job board) and 당근부동산 (real estate), the two other named-but-not-yet-built
 * 당근-style neighborhood-services products.
 *
 * v1, honestly scoped: real posts/comments/likes, real category browse, a real opt-in
 * "near me" proximity filter (same `GeoUtils.haversineKm` foundation Marketplace/Eats
 * already use, Haversine-only -- no OSRM road-ranking yet, an honest, named follow-up
 * matching Marketplace's own v1-before-OSRM-upgrade precedent). No post/comment editing
 * yet (only a real author-only remove). No hyperlocal auto-filtering by a user's actual
 * neighborhood, since `User` has no address/district field anywhere in this backend --
 * matches Marketplace's own already-honest scope on this exact point.
 */
@Service
class CommunityService(
    private val postRepository: CommunityPostRepository,
    private val commentRepository: CommunityCommentRepository,
    private val likeRepository: CommunityLikeRepository,
    private val userRepository: UserRepository,
    private val notificationRepository: NotificationRepository,
    private val rateLimiter: RateLimiter,
    private val nominatimGeocodingClient: NominatimGeocodingClient,
) {
    companion object {
        val CATEGORIES = listOf(
            CommunityCategory("question", "Question"),
            CommunityCategory("news", "Neighborhood news"),
            CommunityCategory("recommendation", "Recommendation"),
            CommunityCategory("lost_found", "Lost & found"),
            CommunityCategory("meetup", "Meetup"),
            CommunityCategory("free", "Free talk"),
        )
        private val CATEGORY_IDS = CATEGORIES.map { it.id }.toSet()
    }

    private fun requireAuthor(authorId: String, postId: String): CommunityPost {
        val post = postRepository.findById(postId).orElseThrow { CommunityPostNotFoundException("Post not found") }
        if (post.authorId != authorId) {
            // Same "don't reveal a resource exists to someone who shouldn't act on it"
            // discipline MarketplaceService.requireOwner already established.
            throw CommunityPostNotFoundException("Post not found")
        }
        return post
    }

    private fun resolveNames(userIds: Collection<String>): Map<String, String> =
        userRepository.findAllById(userIds.distinct()).associate { it.id to "${it.firstName} ${it.lastName}" }

    @Transactional
    fun createPost(
        authorId: String,
        category: String,
        title: String,
        body: String,
        latitude: Double? = null,
        longitude: Double? = null,
    ): CommunityPost {
        val trimmedTitle = title.trim()
        val trimmedBody = body.trim()
        if (trimmedTitle.isEmpty() || trimmedBody.isEmpty()) {
            throw InvalidCommunityPostException("Title and body are both required")
        }
        // Real bound, matching `deliveryAddress`'s own fix on the Eats/Commerce rows
        // the same day (and this class's own `addComment` body check just below) --
        // `title`/`body` are VARCHAR(200)/VARCHAR(4000), and this DB's real
        // STRICT_TRANS_TABLES mode throws a raw, unhandled 500 on an over-length
        // insert rather than truncating.
        if (trimmedTitle.length > 200 || trimmedBody.length > 4000) {
            throw InvalidCommunityPostException("Title must be 200 characters or fewer, body 4000 or fewer")
        }
        if (category !in CATEGORY_IDS) {
            throw InvalidCommunityPostException("Unknown category")
        }
        if ((latitude == null) != (longitude == null)) {
            throw InvalidCommunityCoordinatesException("Both latitude and longitude are required together")
        }
        if (latitude != null && longitude != null && !GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidCommunityCoordinatesException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        // Real anti-spam limit, same 10/hour convention MarketplaceService.createListing
        // already established for user-generated post creation.
        rateLimiter.checkLimit("community:post:$authorId", limit = 10, window = Duration.ofHours(1))

        // Real hyperlocal neighborhood (2026-07-20) -- cached once here from a real
        // reverse-geocode, same discipline MarketplaceService.createListing already
        // established. Best-effort: null when unconfigured/unreachable/no match, never
        // blocks the post itself from being created.
        // iOS may intentionally omit an exact pin for a neighborhood conversation.
        // In that case use the user's already-confirmed Hood neighborhood rather than
        // creating a post that can never appear in their own neighborhood feed.
        val neighborhood = if (latitude != null && longitude != null) {
            nominatimGeocodingClient.reverseGeocode(latitude, longitude)
        } else {
            userRepository.findById(authorId).orElse(null)?.neighborhood
        }

        return postRepository.save(
            CommunityPost(
                id = "community_post_${UUID.randomUUID()}", authorId = authorId, category = category,
                title = trimmedTitle, body = trimmedBody, latitude = latitude, longitude = longitude,
                neighborhood = neighborhood,
            ),
        )
    }

    fun browse(pageable: Pageable, category: String?): Page<CommunityPost> =
        if (category.isNullOrBlank()) {
            postRepository.findByStatusOrderByCreatedAtDesc(CommunityPostStatus.ACTIVE, pageable)
        } else {
            postRepository.findByStatusAndCategoryOrderByCreatedAtDesc(CommunityPostStatus.ACTIVE, category, pageable)
        }

    fun getMyPosts(authorId: String, pageable: Pageable): Page<CommunityPost> =
        postRepository.findByAuthorIdOrderByCreatedAtDesc(authorId, pageable)

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see MarketplaceService.
    // myNeighborhood's own doc comment for the full account; identical shape here.
    fun myNeighborhood(callerUserId: String, category: String?, pageable: Pageable): Page<CommunityPost> {
        val caller = userRepository.findById(callerUserId).orElseThrow { CommunityPostNotFoundException("User not found") }
        val neighborhood = caller.neighborhood
            ?: throw CommunityNeighborhoodNotSetException("Set your neighborhood first via POST /api/v1/auth/profile/neighborhood")
        return if (category.isNullOrBlank()) {
            postRepository.findByStatusAndNeighborhoodOrderByCreatedAtDesc(CommunityPostStatus.ACTIVE, neighborhood, pageable)
        } else {
            postRepository.findByStatusAndNeighborhoodAndCategoryOrderByCreatedAtDesc(CommunityPostStatus.ACTIVE, neighborhood, category, pageable)
        }
    }

    // Real opt-in "near me" browse -- same bounded-candidate-then-Haversine shape
    // MarketplaceService.nearby's own v1 (before its later OSRM upgrade) already used.
    fun nearby(latitude: Double, longitude: Double, radiusKm: Double, pageable: Pageable): Page<CommunityPost> {
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidCommunityCoordinatesException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        if (radiusKm <= 0.0) {
            throw InvalidCommunityCoordinatesException("radiusKm must be greater than zero")
        }
        val sorted = postRepository.findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(CommunityPostStatus.ACTIVE)
            .map { it to GeoUtils.haversineKm(latitude, longitude, it.latitude!!, it.longitude!!) }
            .filter { (_, distanceKm) -> distanceKm <= radiusKm }
            .sortedBy { (_, distanceKm) -> distanceKm }
            .map { (post, _) -> post }

        val start = (pageable.pageNumber * pageable.pageSize).coerceAtMost(sorted.size)
        val end = (start + pageable.pageSize).coerceAtMost(sorted.size)
        return PageImpl(sorted.subList(start, end), pageable, sorted.size.toLong())
    }

    fun getPost(viewerId: String, postId: String): CommunityPostDetail {
        val post = postRepository.findById(postId).orElseThrow { CommunityPostNotFoundException("Post not found") }
        val authorName = resolveNames(listOf(post.authorId))[post.authorId] ?: "Unknown user"
        val likedByMe = likeRepository.existsByPostIdAndUserId(postId, viewerId)
        return CommunityPostDetail(post, authorName, likedByMe)
    }

    @Transactional
    fun removePost(authorId: String, postId: String): CommunityPost {
        val post = requireAuthor(authorId, postId)
        post.status = CommunityPostStatus.REMOVED
        return postRepository.save(post)
    }

    fun getComments(postId: String, pageable: Pageable): Page<CommunityCommentWithAuthor> {
        if (!postRepository.existsById(postId)) throw CommunityPostNotFoundException("Post not found")
        val page = commentRepository.findByPostIdOrderByCreatedAtAsc(postId, pageable)
        val names = resolveNames(page.content.map { it.authorId })
        return page.map { CommunityCommentWithAuthor(it, names[it.authorId] ?: "Unknown user") }
    }

    @Transactional
    fun addComment(authorId: String, postId: String, body: String): CommunityComment {
        val post = postRepository.findById(postId).orElseThrow { CommunityPostNotFoundException("Post not found") }
        if (post.status != CommunityPostStatus.ACTIVE) {
            throw CommunityPostNotFoundException("Post not found")
        }
        val trimmed = body.trim()
        if (trimmed.isEmpty()) {
            throw InvalidCommunityCommentException("Comment cannot be empty")
        }
        if (trimmed.length > 1000) {
            throw InvalidCommunityCommentException("Comment is too long")
        }
        // Real anti-spam limit, matches this codebase's standard per-user-action shape.
        rateLimiter.checkLimit("community:comment:$authorId", limit = 30, window = Duration.ofHours(1))

        val comment = commentRepository.save(
            CommunityComment(id = "community_comment_${UUID.randomUUID()}", postId = postId, authorId = authorId, body = trimmed),
        )
        post.commentCount += 1
        postRepository.save(post)

        // Real notification via the existing in-app Notification system, matching
        // MessagingService's own "new message" notification -- never sent to yourself
        // commenting on your own post.
        if (post.authorId != authorId) {
            val commenterName = resolveNames(listOf(authorId))[authorId] ?: "Someone"
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = post.authorId, type = "COMMUNITY_COMMENT",
                    title = commenterName, body = trimmed.take(120),
                    isRead = false, createdAt = Instant.now(), dataJson = "{\"postId\":\"$postId\"}",
                ),
            )
        }
        return comment
    }

    // Real idempotent like/unlike toggle -- same shape EatsFavoriteService's own
    // add/remove already established, and (learning from this session's own security
    // sweep, which found `MessagingService.toggleReaction` had shipped with zero rate
    // limiting) real-rate-limited from day one, not retrofitted after the fact.
    @Transactional
    fun toggleLike(userId: String, postId: String): Boolean {
        val post = postRepository.findById(postId).orElseThrow { CommunityPostNotFoundException("Post not found") }
        rateLimiter.checkLimit("community:like:$userId", limit = 60, window = Duration.ofMinutes(1))

        val existing = likeRepository.findByPostIdAndUserId(postId, userId)
        return if (existing != null) {
            likeRepository.delete(existing)
            post.likeCount = (post.likeCount - 1).coerceAtLeast(0)
            postRepository.save(post)
            false
        } else {
            likeRepository.save(CommunityLike(id = "community_like_${UUID.randomUUID()}", postId = postId, userId = userId))
            post.likeCount += 1
            postRepository.save(post)
            true
        }
    }
}
