package rw.itunda.community

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.CommunityComment
import rw.itunda.core.domain.CommunityLike
import rw.itunda.core.domain.CommunityNotificationPreference
import rw.itunda.core.domain.CommunityPost
import rw.itunda.core.domain.CommunityPostReport
import rw.itunda.core.domain.CommunityPostStatus
import rw.itunda.core.domain.CommunityReportReason
import rw.itunda.core.domain.GroupConversation
import rw.itunda.core.domain.GroupConversationMember
import rw.itunda.core.domain.MeetupAttendance
import rw.itunda.core.domain.MeetupSession
import rw.itunda.core.domain.Notification
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.geo.NominatimGeocodingClient
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.CommunityCommentRepository
import rw.itunda.core.repository.CommunityLikeRepository
import rw.itunda.core.repository.CommunityNotificationPreferenceRepository
import rw.itunda.core.repository.CommunityPostRepository
import rw.itunda.core.repository.CommunityPostReportRepository
import rw.itunda.core.repository.GroupConversationMemberRepository
import rw.itunda.core.repository.GroupConversationRepository
import rw.itunda.core.repository.MeetupAttendanceRepository
import rw.itunda.core.repository.MeetupSessionRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.splitbill.SplitBillService
import rw.itunda.splitbill.SplitBillWithParticipants
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

class CommunityPostNotFoundException(message: String) : RuntimeException(message)
class CommunityPostNotOwnedException(message: String) : RuntimeException(message)
class InvalidCommunityPostException(message: String) : RuntimeException(message)
class InvalidCommunityCommentException(message: String) : RuntimeException(message)
class InvalidCommunityCoordinatesException(message: String) : RuntimeException(message)
class CommunityNeighborhoodNotSetException(message: String) : RuntimeException(message)
class CommunityMeetupJoinException(message: String) : RuntimeException(message)
class InvalidMeetupException(message: String) : RuntimeException(message)
class MeetupFullException(message: String) : RuntimeException(message)
class InvalidMeetupScheduleException(message: String) : RuntimeException(message)
class MeetupSessionNotFoundException(message: String) : RuntimeException(message)
class MeetupAttendanceAlreadyCheckedInException(message: String) : RuntimeException(message)
class MeetupAttendanceNotAMemberException(message: String) : RuntimeException(message)
class InvalidGroupBuyFinalizeException(message: String) : RuntimeException(message)
class OwnCommunityPostReportException(message: String) : RuntimeException(message)
class CommunityPostAlreadyReportedException(message: String) : RuntimeException(message)

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
    private val groupConversationRepository: GroupConversationRepository,
    private val groupConversationMemberRepository: GroupConversationMemberRepository,
    private val meetupSessionRepository: MeetupSessionRepository,
    private val meetupAttendanceRepository: MeetupAttendanceRepository,
    private val rateLimiter: RateLimiter,
    private val nominatimGeocodingClient: NominatimGeocodingClient,
    private val pushNotificationService: PushNotificationService,
    private val splitBillService: SplitBillService,
    private val communityNotificationPreferenceRepository: CommunityNotificationPreferenceRepository,
    private val communityPostReportRepository: CommunityPostReportRepository,
) {
    companion object {
        // Same real threshold + reasoning MarketplaceService.REPORT_THRESHOLD (Section
        // 140) and EatsReviewService.REPORT_THRESHOLD (Section 141) already established.
        private const val REPORT_THRESHOLD = 3

        val CATEGORIES = listOf(
            CommunityCategory("question", "Question"),
            CommunityCategory("news", "Neighborhood news"),
            CommunityCategory("recommendation", "Recommendation"),
            CommunityCategory("lost_found", "Lost & found"),
            CommunityCategory("meetup", "Meetup"),
            CommunityCategory("group_buy", "Group buy"),
            CommunityCategory("free", "Free talk"),
        )
        private val CATEGORY_IDS = CATEGORIES.map { it.id }.toSet()

        // Real 당근모임 (Karrot Meetups) own sourced cap -- a real recurring series is
        // limited to this many fixed sessions in one action.
        const val MAX_MEETUP_SESSIONS = 6

        // Real 당근마켓 같이사요 (Karrot "Let's Buy Together") own sourced cap -- a real
        // group-buy is limited to this many total participants (organizer included).
        const val MAX_GROUP_BUY_CAPACITY = 4
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
        eventDate: Instant? = null,
        capacity: Int? = null,
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
        // Real 당근모임-style mandatory date-setting (2026-07-25) -- Karrot's own real
        // product made this mandatory specifically when it spun 모임 out of the
        // freeform 같이해요 post type, see CommunityPost.eventDate's own doc comment.
        // Only enforced for the meetup category -- every other category stays exactly
        // as unaffected as before this field existed.
        if (category == "meetup") {
            if (eventDate == null) {
                throw InvalidMeetupException("A meetup needs a real date and time")
            }
            if (eventDate.isBefore(Instant.now())) {
                throw InvalidMeetupException("A meetup's date must be in the future")
            }
            if (capacity != null && capacity < 2) {
                throw InvalidMeetupException("Capacity must allow at least 2 people (including the organizer)")
            }
        }
        // Real 당근마켓 같이사요 (Karrot "Let's Buy Together") mandatory real headcount
        // cap -- Karrot's own real product limits a group-buy to
        // MAX_GROUP_BUY_CAPACITY total participants; itunda enforces it at post
        // creation, not left to the organizer's own honor system.
        if (category == "group_buy" && (capacity == null || capacity !in 2..MAX_GROUP_BUY_CAPACITY)) {
            throw InvalidMeetupException("A group buy needs a real capacity between 2 and $MAX_GROUP_BUY_CAPACITY people (including the organizer)")
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
                neighborhood = neighborhood, eventDate = if (category == "meetup") eventDate else null,
                capacity = if (category == "meetup" || category == "group_buy") capacity else null,
            ),
        )
    }

    // Real 당근모임-style "upcoming meetups" browse (2026-07-25) -- see
    // CommunityPostRepository.findUpcomingMeetups' own doc comment.
    fun upcomingMeetups(pageable: Pageable): Page<CommunityPost> =
        postRepository.findUpcomingMeetups(CommunityPostStatus.ACTIVE, Instant.now(), pageable)

    // Real relevance-ranked search (2026-08-14) -- see MarketplaceService.search's own
    // doc comment for the full "why" (not neighborhood-scoped, same as browse above).
    fun search(query: String, pageable: Pageable): Page<CommunityPost> {
        val booleanQuery = rw.itunda.core.search.FullTextSearchUtil.toBooleanModeQuery(query)
        return if (booleanQuery != null) {
            postRepository.searchFullText(CommunityPostStatus.ACTIVE, booleanQuery, pageable)
        } else {
            postRepository.searchShort(CommunityPostStatus.ACTIVE, query.trim(), pageable)
        }
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
        // Real dual-neighborhood support (2026-08-04) -- see User.secondNeighborhood's own doc comment.
        val neighborhoods = listOfNotNull(neighborhood, caller.secondNeighborhood)
        return if (category.isNullOrBlank()) {
            postRepository.findByStatusAndNeighborhoodInOrderByCreatedAtDesc(CommunityPostStatus.ACTIVE, neighborhoods, pageable)
        } else {
            postRepository.findByStatusAndNeighborhoodInAndCategoryOrderByCreatedAtDesc(CommunityPostStatus.ACTIVE, neighborhoods, category, pageable)
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

    // Real 동네생활 신고하기 (report a post) -- see CommunityPostReport.kt's own doc
    // comment for the real sourcing. One real report per (post, reporter), same
    // DB-unique concurrency guard toggleLike's own CommunityLike already establishes.
    // Once REPORT_THRESHOLD distinct reporters accumulate, the post is silently removed
    // (status -> REMOVED, the same real effect the author's own removePost already
    // has -- every browse/search/myNeighborhood/nearby/upcomingMeetups query already
    // filters on status = ACTIVE, so no read-path changes needed) -- no notification to
    // anyone, matching the sourced real silence rather than inventing a friendlier flow.
    @Transactional
    fun reportPost(reporterId: String, postId: String, reason: CommunityReportReason, details: String?): CommunityPostReport {
        val post = postRepository.findById(postId).orElseThrow { CommunityPostNotFoundException("Post not found") }
        if (post.authorId == reporterId) {
            throw OwnCommunityPostReportException("You can't report your own post")
        }
        if (communityPostReportRepository.findByPostIdAndReporterId(postId, reporterId) != null) {
            throw CommunityPostAlreadyReportedException("You've already reported this post")
        }
        rateLimiter.checkLimit("community:report:$reporterId", limit = 20, window = Duration.ofMinutes(1))

        val saved = communityPostReportRepository.save(
            CommunityPostReport(
                id = "community_post_report_${UUID.randomUUID()}", postId = postId, reporterId = reporterId,
                reason = reason, details = details?.trim()?.take(500)?.ifBlank { null },
            ),
        )

        if (post.status == CommunityPostStatus.ACTIVE && communityPostReportRepository.countByPostId(postId) >= REPORT_THRESHOLD) {
            post.status = CommunityPostStatus.REMOVED
            postRepository.save(post)
        }

        return saved
    }

    // Real 같이해요 (join-together) group chat (2026-07-24) -- closes
    // docs/DESIGN_REFERENCES.md Section 4 recommendation #4's "joining their group chat
    // requires an explicit 참여하기 tap." Deliberately bypasses
    // GroupMessagingService.addMember (which requires the requester to already BE a
    // group member -- correct for "invite someone to my existing group," wrong for
    // "publicly join a meetup group you're not in yet"), and manages the group/member
    // rows directly instead -- same cross-module repository reuse discipline
    // TrustScoreSupport.trustScores already established (a service in one module using
    // another module's repository directly, not routed through that module's own
    // service API).
    @Transactional
    fun joinMeetup(userId: String, postId: String): GroupConversation {
        val post = postRepository.findById(postId).orElseThrow { CommunityPostNotFoundException("Post not found") }
        // Real 당근마켓 같이사요 (group-buy) reuses this exact real join/capacity
        // mechanic outright -- see CommunityService's own doc comment on
        // finalizeGroupBuy for the full account of why "join a capped group, then
        // split the real bill" is the same shape a meetup already establishes.
        if (post.category != "meetup" && post.category != "group_buy") {
            throw CommunityMeetupJoinException("Only meetup or group-buy posts can be joined")
        }
        if (post.status != CommunityPostStatus.ACTIVE) {
            throw CommunityMeetupJoinException("This post is no longer active")
        }
        val groupId = post.groupConversationId ?: run {
            val newGroupId = "group_${UUID.randomUUID()}"
            groupConversationRepository.save(GroupConversation(id = newGroupId, name = post.title, createdBy = post.authorId))
            groupConversationMemberRepository.save(
                GroupConversationMember(id = "group_member_${UUID.randomUUID()}", groupConversationId = newGroupId, userId = post.authorId),
            )
            post.groupConversationId = newGroupId
            postRepository.save(post)
            newGroupId
        }
        if (groupConversationMemberRepository.findByGroupConversationIdAndUserId(groupId, userId) == null) {
            // Real capacity cap (2026-07-25) -- see CommunityPost.capacity's own doc
            // comment. Checked at join time, not reserved in advance -- an honest
            // first-come-first-served cap, same as every other real "N spots" mechanic
            // in this codebase (e.g. AgentWithdrawalAuthorizationService's own real
            // per-day limit check happens at the moment of the action, not earlier).
            //
            // Real bug found live (2026-08-02): this count-then-insert shape is a
            // classic TOCTOU -- two different users concurrently joining the last open
            // spot could both read a count one below capacity before either commit and
            // both get admitted, overrunning capacity. Locking the post row itself
            // (findByIdForUpdate) serializes concurrent joins to it, so the count this
            // re-check sees always reflects every already-committed join.
            postRepository.findByIdForUpdate(postId)
            val capacity = post.capacity
            if (capacity != null && groupConversationMemberRepository.findByGroupConversationId(groupId).size >= capacity) {
                throw MeetupFullException("This meetup is full")
            }
            groupConversationMemberRepository.save(
                GroupConversationMember(id = "group_member_${UUID.randomUUID()}", groupConversationId = groupId, userId = userId),
            )
        }
        return groupConversationRepository.findById(groupId).orElseThrow()
    }

    // Real "N joined" count (2026-07-24) -- batch member-count query
    // (countMembersByGroupConversationIds) already proven by
    // GroupMessagingService.listMyGroups, reused here rather than fetching every member
    // row just to call .size, same N+1-avoidance discipline this class's own doc
    // comment already established for likeCount/commentCount.
    fun joinedCounts(posts: Collection<CommunityPost>): Map<String, Int> {
        val groupIds = posts.mapNotNull { it.groupConversationId }
        if (groupIds.isEmpty()) return emptyMap()
        val counts = groupConversationMemberRepository.countMembersByGroupConversationIds(groupIds)
            .associate { it.groupConversationId to it.memberCount.toInt() }
        return posts.mapNotNull { post -> post.groupConversationId?.let { gid -> post.id to (counts[gid] ?: 0) } }.toMap()
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
        //
        // Real push wired in (2026-07-28) -- a real reply to a real post is exactly the
        // kind of social-app moment (Karrot/Kakao/every real neighborhood app) a poster
        // expects to hear about immediately, not on their next in-app poll.
        //
        // Real Karrot 동네생활 "새 댓글 알림 끄기" (2026-08-17) -- see
        // CommunityNotificationPreference's own doc comment. A post author who's
        // turned this off never gets a comment notification, on any of their posts;
        // the comment itself is still saved and counted either way.
        if (post.authorId != authorId && areCommentNotificationsEnabled(post.authorId)) {
            val commenterName = resolveNames(listOf(authorId))[authorId] ?: "Someone"
            val body = trimmed.take(120)
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = post.authorId, type = "COMMUNITY_COMMENT",
                    title = commenterName, body = body,
                    isRead = false, createdAt = Instant.now(), dataJson = "{\"postId\":\"$postId\"}",
                ),
            )
            pushNotificationService.sendToUser(post.authorId, commenterName, body, mapOf("postId" to postId))
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

    /**
     * Real 당근모임 (Karrot Meetups) recurring schedule -- see `MeetupSession`'s own doc
     * comment for the full sourced account. Only the real meetup's own author can
     * schedule sessions (same `requireAuthor` gate `removePost` already uses), and only
     * for a real `category == "meetup"` post. Replaces any previously-scheduled series
     * outright rather than appending -- itunda's own honest choice, since Karrot's own
     * real UI doesn't publicly document whether re-scheduling merges or replaces.
     */
    @Transactional
    fun scheduleMeetupSessions(authorId: String, postId: String, dates: List<Instant>): List<MeetupSession> {
        val post = requireAuthor(authorId, postId)
        if (post.category != "meetup") {
            throw InvalidMeetupScheduleException("Only meetup posts can have a real recurring schedule")
        }
        if (dates.isEmpty() || dates.size > MAX_MEETUP_SESSIONS) {
            throw InvalidMeetupScheduleException("A meetup schedule must have between 1 and $MAX_MEETUP_SESSIONS sessions")
        }
        val now = Instant.now()
        if (dates.any { !it.isAfter(now) }) {
            throw InvalidMeetupScheduleException("Every scheduled session must be in the future")
        }
        meetupSessionRepository.deleteAll(meetupSessionRepository.findByPostIdOrderBySequenceAsc(postId))
        return dates.sorted().mapIndexed { index, date ->
            meetupSessionRepository.save(MeetupSession(id = "meetup_session_${UUID.randomUUID()}", postId = postId, sequence = index, scheduledFor = date))
        }
    }

    fun getMeetupSessions(postId: String): List<MeetupSession> = meetupSessionRepository.findByPostIdOrderBySequenceAsc(postId)

    /**
     * Real 당근모임 (Karrot Meetups) attendance check-in -- see `MeetupAttendance`'s own
     * doc comment. Only a real joined member of the meetup's own group chat can check
     * in (same real membership itunda already tracks via `joinMeetup`) -- checking in
     * without ever having joined would record attendance for someone who was never
     * actually part of the group.
     */
    @Transactional
    fun checkIntoSession(userId: String, sessionId: String): MeetupAttendance {
        val session = meetupSessionRepository.findById(sessionId).orElseThrow { MeetupSessionNotFoundException("Session not found") }
        val post = postRepository.findById(session.postId).orElseThrow { CommunityPostNotFoundException("Post not found") }
        val groupId = post.groupConversationId
        if (groupId == null || groupConversationMemberRepository.findByGroupConversationIdAndUserId(groupId, userId) == null) {
            throw MeetupAttendanceNotAMemberException("Join this meetup before checking in to a session")
        }
        if (meetupAttendanceRepository.findBySessionIdAndUserId(sessionId, userId) != null) {
            throw MeetupAttendanceAlreadyCheckedInException("Already checked in to this session")
        }
        return meetupAttendanceRepository.save(MeetupAttendance(id = "meetup_attendance_${UUID.randomUUID()}", sessionId = sessionId, userId = userId))
    }

    /**
     * Real bug found live 2026-08-02 in a security sweep: this had zero auth or
     * membership check at all -- any unauthenticated caller who knew/guessed a real
     * sessionId got back every real attendee's raw userId. Same real membership gate
     * `checkIntoSession` already establishes: only a real joined member of the
     * meetup's own group chat can see who else checked in.
     */
    fun getSessionAttendance(userId: String, sessionId: String): List<MeetupAttendance> {
        val session = meetupSessionRepository.findById(sessionId).orElseThrow { MeetupSessionNotFoundException("Session not found") }
        val post = postRepository.findById(session.postId).orElseThrow { CommunityPostNotFoundException("Post not found") }
        val groupId = post.groupConversationId
        if (groupId == null || groupConversationMemberRepository.findByGroupConversationIdAndUserId(groupId, userId) == null) {
            throw MeetupAttendanceNotAMemberException("Join this meetup before viewing session attendance")
        }
        return meetupAttendanceRepository.findBySessionId(sessionId)
    }

    /**
     * Real 당근마켓 같이사요 (Karrot "Let's Buy Together") -- see this class's own doc
     * comment on why joining a real capped group-buy reuses `joinMeetup` outright.
     * Once the real organizer has fronted the total cost, they finalize here: reuses
     * the already-proven `SplitBillService.createSplitBill` wholesale for the actual
     * real cost-splitting (organizer request, participants pay their real even share)
     * against the same real group chat every real joined participant is already in --
     * no new payment mechanic invented for this feature, just two already-real
     * capabilities wired together.
     */
    @Transactional
    fun finalizeGroupBuy(organizerId: String, postId: String, totalAmount: BigDecimal, description: String): SplitBillWithParticipants {
        val post = requireAuthor(organizerId, postId)
        if (post.category != "group_buy") {
            throw InvalidGroupBuyFinalizeException("Only group-buy posts can be finalized")
        }
        val groupId = post.groupConversationId
            ?: throw InvalidGroupBuyFinalizeException("This group buy has no real participants to split the cost with yet")
        val participantIds = groupConversationMemberRepository.findByGroupConversationId(groupId)
            .map { it.userId }
            .filter { it != organizerId }
        return splitBillService.createSplitBill(organizerId, groupId, totalAmount, description, participantIds)
    }

    // Real Karrot 동네생활 "새 댓글 알림 끄기" toggle -- see
    // CommunityNotificationPreference's own doc comment for the sourced feature and
    // why this is a global per-user category toggle, not a per-post mute.
    @Transactional
    fun setCommentNotificationsEnabled(userId: String, enabled: Boolean): CommunityNotificationPreference {
        val preference = communityNotificationPreferenceRepository.findByUserId(userId)
            ?: CommunityNotificationPreference(id = "community_notification_pref_${UUID.randomUUID()}", userId = userId)
        preference.commentNotificationsEnabled = enabled
        preference.updatedAt = Instant.now()
        return communityNotificationPreferenceRepository.save(preference)
    }

    fun areCommentNotificationsEnabled(userId: String): Boolean =
        communityNotificationPreferenceRepository.findByUserId(userId)?.commentNotificationsEnabled ?: true
}
