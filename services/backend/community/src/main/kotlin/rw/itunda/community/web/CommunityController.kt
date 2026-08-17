package rw.itunda.community.web

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.community.CommunityMeetupJoinException
import rw.itunda.community.CommunityNeighborhoodNotSetException
import rw.itunda.community.CommunityPostNotFoundException
import rw.itunda.community.CommunityService
import rw.itunda.community.InvalidCommunityCommentException
import rw.itunda.community.InvalidCommunityCoordinatesException
import rw.itunda.community.InvalidCommunityPostException
import rw.itunda.community.InvalidGroupBuyFinalizeException
import rw.itunda.community.InvalidMeetupException
import rw.itunda.community.InvalidMeetupScheduleException
import rw.itunda.community.MeetupAttendanceAlreadyCheckedInException
import rw.itunda.community.MeetupAttendanceNotAMemberException
import rw.itunda.community.MeetupFullException
import rw.itunda.community.MeetupSessionNotFoundException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import rw.itunda.splitbill.SplitBillDescriptionRequiredException
import rw.itunda.splitbill.SplitBillInvalidAmountException
import rw.itunda.splitbill.SplitBillNeedsParticipantsException
import rw.itunda.splitbill.SplitBillParticipantNotGroupMemberException
import java.time.Instant

data class CreateCommunityPostRequest(
    val category: String,
    val title: String,
    val body: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    // Real 당근모임-style structured meetup fields (2026-07-25) -- both ignored unless
    // category == "meetup"; see CommunityService.createPost's own doc comment.
    val eventDate: Instant? = null,
    val capacity: Int? = null,
)
data class AddCommunityCommentRequest(val body: String)
// Real 당근모임 (Karrot Meetups) recurring schedule -- see
// CommunityService.scheduleMeetupSessions's own doc comment.
data class ScheduleMeetupSessionsRequest(val dates: List<Instant>)
// Real 당근마켓 같이사요 (Karrot "Let's Buy Together") -- see
// CommunityService.finalizeGroupBuy's own doc comment.
data class FinalizeGroupBuyRequest(val totalAmount: java.math.BigDecimal, val description: String)
data class SetCommentNotificationsEnabledRequest(val enabled: Boolean)

// Real 동네생활-style community board -- see CommunityService's own doc comment. Normal
// itunda-user JWT gate (default SecurityConfig .anyRequest().authenticated()).
@RestController
@RequestMapping("/api/v1/community")
class CommunityController(private val communityService: CommunityService, private val idempotencyService: IdempotencyService) {

    @GetMapping("/categories")
    fun categories(): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "categories" to CommunityService.CATEGORIES))

    @PostMapping("/posts")
    fun createPost(
        @RequestBody request: CreateCommunityPostRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val post = communityService.createPost(
            currentUser.userId, request.category, request.title, request.body, request.latitude, request.longitude,
            request.eventDate, request.capacity,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "post" to post))
    }

    // Real 당근모임-style "upcoming meetups" browse (2026-07-25) -- see
    // CommunityService.upcomingMeetups' own doc comment.
    @GetMapping("/meetups/upcoming")
    fun upcomingMeetups(@PageableDefault(size = 20) pageable: Pageable): ResponseEntity<Map<String, Any?>> {
        val page = communityService.upcomingMeetups(pageable)
        val joinedCounts = communityService.joinedCounts(page.content)
        return ResponseEntity.ok(mapOf("success" to true, "posts" to page.content, "joinedCounts" to joinedCounts) + pageMeta(page))
    }

    @GetMapping("/posts")
    fun browse(
        @RequestParam(required = false) category: String?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val page = communityService.browse(pageable, category)
        val joinedCounts = communityService.joinedCounts(page.content)
        return ResponseEntity.ok(mapOf("success" to true, "posts" to page.content, "joinedCounts" to joinedCounts) + pageMeta(page))
    }

    @GetMapping("/posts/nearby")
    fun nearby(
        @RequestParam latitude: Double,
        @RequestParam longitude: Double,
        @RequestParam(required = false, defaultValue = "5.0") radiusKm: Double,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val page = communityService.nearby(latitude, longitude, radiusKm, pageable)
        val joinedCounts = communityService.joinedCounts(page.content)
        return ResponseEntity.ok(mapOf("success" to true, "posts" to page.content, "joinedCounts" to joinedCounts) + pageMeta(page))
    }

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see CommunityService.
    // myNeighborhood's own doc comment.
    @GetMapping("/posts/my-neighborhood")
    fun myNeighborhood(
        @RequestParam(required = false) category: String?,
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = communityService.myNeighborhood(currentUser.userId, category, pageable)
        val joinedCounts = communityService.joinedCounts(page.content)
        return ResponseEntity.ok(mapOf("success" to true, "posts" to page.content, "joinedCounts" to joinedCounts) + pageMeta(page))
    }

    // Real relevance-ranked search (2026-08-14) -- see CommunityService.search's own
    // doc comment.
    @GetMapping("/posts/search")
    fun search(
        @RequestParam q: String,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val page = communityService.search(q, pageable)
        val joinedCounts = communityService.joinedCounts(page.content)
        return ResponseEntity.ok(mapOf("success" to true, "posts" to page.content, "joinedCounts" to joinedCounts) + pageMeta(page))
    }

    @GetMapping("/my-posts")
    fun myPosts(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = communityService.getMyPosts(currentUser.userId, pageable)
        val joinedCounts = communityService.joinedCounts(page.content)
        return ResponseEntity.ok(mapOf("success" to true, "posts" to page.content, "joinedCounts" to joinedCounts) + pageMeta(page))
    }

    @GetMapping("/posts/{postId}")
    fun getPost(
        @PathVariable postId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val detail = communityService.getPost(currentUser.userId, postId)
        return ResponseEntity.ok(
            mapOf(
                "success" to true, "post" to detail.post, "authorName" to detail.authorName, "likedByMe" to detail.likedByMe,
            ),
        )
    }

    @DeleteMapping("/posts/{postId}")
    fun removePost(
        @PathVariable postId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "post" to communityService.removePost(currentUser.userId, postId)))

    @GetMapping("/posts/{postId}/comments")
    fun getComments(
        @PathVariable postId: String,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val page = communityService.getComments(postId, pageable)
        val comments = page.content.map { mapOf("comment" to it.comment, "authorName" to it.authorName) }
        return ResponseEntity.ok(mapOf("success" to true, "comments" to comments) + pageMeta(page))
    }

    @PostMapping("/posts/{postId}/comments")
    fun addComment(
        @PathVariable postId: String,
        @RequestBody request: AddCommunityCommentRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val comment = communityService.addComment(currentUser.userId, postId, request.body)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "comment" to comment))
    }

    // Real Karrot 동네생활 "새 댓글 알림 끄기" (2026-08-17) -- see
    // CommunityNotificationPreference's own doc comment.
    @PostMapping("/notification-preference")
    fun setCommentNotificationsEnabled(
        @RequestBody request: SetCommentNotificationsEnabledRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val preference = communityService.setCommentNotificationsEnabled(currentUser.userId, request.enabled)
        return ResponseEntity.ok(mapOf("success" to true, "commentNotificationsEnabled" to preference.commentNotificationsEnabled))
    }

    @GetMapping("/notification-preference")
    fun getCommentNotificationsEnabled(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "commentNotificationsEnabled" to communityService.areCommentNotificationsEnabled(currentUser.userId)))

    @PostMapping("/posts/{postId}/like")
    fun toggleLike(
        @PathVariable postId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val liked = communityService.toggleLike(currentUser.userId, postId)
        return ResponseEntity.ok(mapOf("success" to true, "liked" to liked))
    }

    // Real 같이해요 (join-together) explicit 참여하기 tap (2026-07-24) -- see
    // CommunityService.joinMeetup's own doc comment.
    @PostMapping("/posts/{postId}/join")
    fun joinMeetup(
        @PathVariable postId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val group = communityService.joinMeetup(currentUser.userId, postId)
        return ResponseEntity.ok(mapOf("success" to true, "groupId" to group.id))
    }

    // Real 당근모임 (Karrot Meetups) recurring schedule -- see
    // CommunityService.scheduleMeetupSessions's own doc comment.
    @PostMapping("/posts/{postId}/sessions")
    fun scheduleMeetupSessions(
        @PathVariable postId: String,
        @RequestBody request: ScheduleMeetupSessionsRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val sessions = communityService.scheduleMeetupSessions(currentUser.userId, postId, request.dates)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "sessions" to sessions))
    }

    @GetMapping("/posts/{postId}/sessions")
    fun getMeetupSessions(@PathVariable postId: String): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "sessions" to communityService.getMeetupSessions(postId)))

    // Real 당근모임 (Karrot Meetups) attendance check-in -- see
    // CommunityService.checkIntoSession's own doc comment.
    @PostMapping("/sessions/{sessionId}/check-in")
    fun checkIntoSession(@PathVariable sessionId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val attendance = communityService.checkIntoSession(currentUser.userId, sessionId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "attendance" to attendance))
    }

    @GetMapping("/sessions/{sessionId}/attendance")
    fun getSessionAttendance(
        @PathVariable sessionId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "attendance" to communityService.getSessionAttendance(currentUser.userId, sessionId)))

    // Real 당근마켓 같이사요 (Karrot "Let's Buy Together") -- see
    // CommunityService.finalizeGroupBuy's own doc comment.
    //
    // Real Idempotency-Key gap found and fixed (item 247 follow-up, repo-wide
    // Idempotency-Key coverage sweep): this creates a brand-new real SplitBill with no
    // check for "has this post already been finalized" -- a retried request (timeout,
    // double-tap) created a second real split bill for the same group purchase, with
    // participants who don't know to ignore the duplicate potentially paying into both.
    // Same risk shape this codebase already fixed once for AutoTransferController.create
    // ("a duplicate schedule row means a duplicate charge... down the line", not just an
    // immediate one) -- SplitBillController's own createSplitBill endpoint already
    // requires this same key for the identical underlying action; this is the second,
    // previously-uncovered entry point to it.
    @PostMapping("/posts/{postId}/finalize-group-buy")
    fun finalizeGroupBuy(
        @PathVariable postId: String,
        @RequestBody request: FinalizeGroupBuyRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/community/posts/$postId/finalize-group-buy", idempotencyKey, request) {
            val result = communityService.finalizeGroupBuy(currentUser.userId, postId, request.totalAmount, request.description)
            HttpStatus.CREATED.value() to mapOf("success" to true, "splitBill" to result.splitBill, "participants" to result.participants)
        }
        return ResponseEntity.status(status).body(body)
    }

    @ExceptionHandler(CommunityMeetupJoinException::class)
    fun handleMeetupJoin(ex: CommunityMeetupJoinException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("COMMUNITY_MEETUP_JOIN_INVALID", ex.message ?: "Bad request"))

    @ExceptionHandler(CommunityPostNotFoundException::class)
    fun handleNotFound(ex: CommunityPostNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("COMMUNITY_POST_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidCommunityPostException::class)
    fun handleInvalidPost(ex: InvalidCommunityPostException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_COMMUNITY_POST", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidCommunityCommentException::class)
    fun handleInvalidComment(ex: InvalidCommunityCommentException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_COMMUNITY_COMMENT", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidCommunityCoordinatesException::class)
    fun handleInvalidCoordinates(ex: InvalidCommunityCoordinatesException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_COORDINATES", ex.message ?: "Bad request"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))

    @ExceptionHandler(CommunityNeighborhoodNotSetException::class)
    fun handleNeighborhoodNotSet(ex: CommunityNeighborhoodNotSetException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("NEIGHBORHOOD_NOT_SET", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidMeetupException::class)
    fun handleInvalidMeetup(ex: InvalidMeetupException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_MEETUP", ex.message ?: "Bad request"))

    @ExceptionHandler(MeetupFullException::class)
    fun handleMeetupFull(ex: MeetupFullException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("MEETUP_FULL", ex.message ?: "Conflict"))

    @ExceptionHandler(InvalidMeetupScheduleException::class)
    fun handleInvalidSchedule(ex: InvalidMeetupScheduleException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_MEETUP_SCHEDULE", ex.message ?: "Bad request"))

    @ExceptionHandler(MeetupSessionNotFoundException::class)
    fun handleSessionNotFound(ex: MeetupSessionNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MEETUP_SESSION_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(MeetupAttendanceAlreadyCheckedInException::class)
    fun handleAlreadyCheckedIn(ex: MeetupAttendanceAlreadyCheckedInException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("ALREADY_CHECKED_IN", ex.message ?: "Conflict"))

    // Real gap found 2026-08-08 (third IDOR audit pass): a real 403 here let a
    // stranger with a real sessionId they aren't a member of distinguish "exists,
    // you're not in this group" from a genuinely nonexistent sessionId (404) via
    // status code alone -- same existence-oracle bug class already found and fixed
    // twice this session (GroupAccountController, SavingsController). Notably: this
    // exact method's own doc comment already documents a prior real bug (an
    // unauthenticated caller could read every real attendee's userId) that this same
    // membership check was added to fix -- the membership gate itself was correct,
    // just mapped to the wrong status code.
    @ExceptionHandler(MeetupAttendanceNotAMemberException::class)
    fun handleNotAMember(ex: MeetupAttendanceNotAMemberException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("SESSION_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidGroupBuyFinalizeException::class)
    fun handleInvalidGroupBuyFinalize(ex: InvalidGroupBuyFinalizeException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_GROUP_BUY_FINALIZE", ex.message ?: "Bad request"))

    // Real exceptions SplitBillService.createSplitBill itself can throw, reachable via
    // finalizeGroupBuy -- same clean 4xx handling every other real cross-module reuse
    // in this backend already gives its own delegate's exceptions.
    @ExceptionHandler(SplitBillInvalidAmountException::class)
    fun handleSplitBillInvalidAmount(ex: SplitBillInvalidAmountException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(SplitBillDescriptionRequiredException::class)
    fun handleSplitBillDescriptionRequired(ex: SplitBillDescriptionRequiredException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("DESCRIPTION_REQUIRED", ex.message ?: "Bad request"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))

    @ExceptionHandler(SplitBillNeedsParticipantsException::class)
    fun handleSplitBillNeedsParticipants(ex: SplitBillNeedsParticipantsException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("NEEDS_PARTICIPANTS", ex.message ?: "Bad request"))

    @ExceptionHandler(SplitBillParticipantNotGroupMemberException::class)
    fun handleSplitBillParticipantNotGroupMember(ex: SplitBillParticipantNotGroupMemberException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("PARTICIPANT_NOT_GROUP_MEMBER", ex.message ?: "Bad request"))
}
