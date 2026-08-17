package rw.itunda.jobs.web

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.domain.HoodTransactionType
import rw.itunda.core.domain.JobPayType
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.review.HoodReviewAlreadySubmittedException
import rw.itunda.core.review.HoodReviewNoCounterpartyException
import rw.itunda.core.review.HoodReviewNotPartyException
import rw.itunda.core.review.HoodReviewService
import rw.itunda.core.review.HoodReviewTransactionNotCompletedException
import rw.itunda.core.review.HoodReviewTransactionNotFoundException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import rw.itunda.core.web.toResponseDto
import rw.itunda.core.web.trustScores
import rw.itunda.jobs.FavoriteJobPostNotFoundException
import rw.itunda.jobs.InvalidJobApplicationException
import rw.itunda.jobs.InvalidJobCoordinatesException
import rw.itunda.jobs.InvalidJobPostException
import rw.itunda.jobs.JobApplicationAlreadyPendingException
import rw.itunda.jobs.JobApplicationNotFoundException
import rw.itunda.jobs.JobApplicationNotPendingException
import rw.itunda.jobs.JobApplicationService
import rw.itunda.jobs.JobPostFavoriteService
import rw.itunda.jobs.JobPostNotFoundException
import rw.itunda.jobs.JobPostNotOpenException
import rw.itunda.jobs.JobPostService
import rw.itunda.jobs.JobsNeighborhoodNotSetException
import rw.itunda.jobs.OwnJobPostException
import rw.itunda.jobs.WorkerNotFoundException
import java.math.BigDecimal

data class CreateJobPostRequest(
    val category: String,
    val title: String,
    val description: String,
    val payType: JobPayType,
    val payAmount: BigDecimal,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
data class MarkFilledRequest(val workerPhoneNumber: String? = null)
data class SubmitHoodReviewRequest(val goodPoints: List<String> = emptyList(), val uncomfortablePoints: List<String> = emptyList())
data class ApplyToJobRequest(val message: String)
data class RespondToApplicationRequest(val accept: Boolean)

// Real 당근알바-style local job board -- see JobPostService's own doc comment. Normal
// itunda-user JWT gate (default SecurityConfig .anyRequest().authenticated()).
@RestController
@RequestMapping("/api/v1/jobs")
class JobPostController(
    private val jobPostService: JobPostService,
    private val jobPostFavoriteService: JobPostFavoriteService,
    private val userRepository: UserRepository,
    private val hoodReviewService: HoodReviewService,
    private val jobApplicationService: JobApplicationService,
) {

    @GetMapping("/categories")
    fun categories(): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "categories" to JobPostService.CATEGORIES))

    @PostMapping("/posts")
    fun createPost(
        @RequestBody request: CreateJobPostRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val post = jobPostService.createPost(
            currentUser.userId, request.category, request.title, request.description,
            request.payType, request.payAmount, request.latitude, request.longitude,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "post" to post))
    }

    @GetMapping("/posts")
    fun browse(
        @RequestParam(required = false) category: String?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val page = jobPostService.browse(pageable, category)
        // Real Karrot-Score-style trust badge (2026-07-21) -- see trustScores' own doc
        // comment. One batch findAllById, not one query per post's poster.
        val scores = trustScores(userRepository, page.content.map { it.posterId })
        return ResponseEntity.ok(mapOf("success" to true, "posts" to page.content, "trustScores" to scores) + pageMeta(page))
    }

    @GetMapping("/posts/nearby")
    fun nearby(
        @RequestParam latitude: Double,
        @RequestParam longitude: Double,
        @RequestParam(required = false, defaultValue = "5.0") radiusKm: Double,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val page = jobPostService.nearby(latitude, longitude, radiusKm, pageable)
        val scores = trustScores(userRepository, page.content.map { it.posterId })
        return ResponseEntity.ok(mapOf("success" to true, "posts" to page.content, "trustScores" to scores) + pageMeta(page))
    }

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see JobPostService.
    // myNeighborhood's own doc comment.
    @GetMapping("/posts/my-neighborhood")
    fun myNeighborhood(
        @RequestParam(required = false) category: String?,
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = jobPostService.myNeighborhood(currentUser.userId, category, pageable)
        val scores = trustScores(userRepository, page.content.map { it.posterId })
        return ResponseEntity.ok(mapOf("success" to true, "posts" to page.content, "trustScores" to scores) + pageMeta(page))
    }

    // Real relevance-ranked search (2026-08-14) -- see JobPostService.search's own
    // doc comment.
    @GetMapping("/posts/search")
    fun search(
        @RequestParam q: String,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val page = jobPostService.search(q, pageable)
        val scores = trustScores(userRepository, page.content.map { it.posterId })
        return ResponseEntity.ok(mapOf("success" to true, "posts" to page.content, "trustScores" to scores) + pageMeta(page))
    }

    @GetMapping("/my-posts")
    fun myPosts(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = jobPostService.getMyPosts(currentUser.userId, pageable)
        val scores = trustScores(userRepository, page.content.map { it.posterId })
        return ResponseEntity.ok(mapOf("success" to true, "posts" to page.content, "trustScores" to scores) + pageMeta(page))
    }

    // Real "Jobs I did" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
    // recommendation #6. See JobPostRepository.findByWorkerIdOrderByCreatedAtDesc's
    // own doc comment for the full account.
    @GetMapping("/my-worked-posts")
    fun myWorkedPosts(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = jobPostService.getMyWorkedPosts(currentUser.userId, pageable)
        val scores = trustScores(userRepository, page.content.map { it.posterId })
        return ResponseEntity.ok(mapOf("success" to true, "posts" to page.content, "trustScores" to scores) + pageMeta(page))
    }

    @GetMapping("/posts/{jobPostId}")
    fun getPost(@PathVariable jobPostId: String): ResponseEntity<Map<String, Any?>> {
        val post = jobPostService.getPost(jobPostId)
        val posterTrustScore = trustScores(userRepository, listOf(post.posterId))[post.posterId]
        return ResponseEntity.ok(mapOf("success" to true, "post" to post, "posterTrustScore" to posterTrustScore))
    }

    @PostMapping("/posts/{jobPostId}/mark-filled")
    fun markFilled(
        @PathVariable jobPostId: String,
        @RequestBody(required = false) request: MarkFilledRequest?,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val post = jobPostService.markFilled(currentUser.userId, jobPostId, request?.workerPhoneNumber)
        // Real 당근알바 job-closed notification -- see JobPostFavoriteService
        // .notifyFavoritersOfClosure's own doc comment for why this lives here, at the
        // controller layer, right after the real status change commits.
        jobPostFavoriteService.notifyFavoritersOfClosure(post)
        return ResponseEntity.ok(mapOf("success" to true, "post" to post))
    }

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see HoodReviewService's own doc comment for the full account.
    @PostMapping("/posts/{jobPostId}/review")
    fun submitReview(
        @PathVariable jobPostId: String,
        @RequestBody request: SubmitHoodReviewRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val review = hoodReviewService.submitReview(
            currentUser.userId, HoodTransactionType.JOB_POST, jobPostId, request.goodPoints, request.uncomfortablePoints,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "review" to review.toResponseDto()))
    }

    @GetMapping("/posts/{jobPostId}/review")
    fun getReviews(
        @PathVariable jobPostId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val reviews = hoodReviewService.getTransactionReviews(currentUser.userId, HoodTransactionType.JOB_POST, jobPostId).map { it.toResponseDto() }
        return ResponseEntity.ok(mapOf("success" to true, "reviews" to reviews))
    }

    @DeleteMapping("/posts/{jobPostId}")
    fun removePost(
        @PathVariable jobPostId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val post = jobPostService.removePost(currentUser.userId, jobPostId)
        // Real 당근알바 job-closed notification -- see JobPostFavoriteService
        // .notifyFavoritersOfClosure's own doc comment.
        jobPostFavoriteService.notifyFavoritersOfClosure(post)
        return ResponseEntity.ok(mapOf("success" to true, "post" to post))
    }

    // Real 당근알바 job-post wishlist (2026-07-22) -- see JobPostFavoriteService's own
    // doc comment. Mirrors MarketplaceController's own favorite-listing endpoints
    // field-for-field.
    @PostMapping("/posts/{jobPostId}/favorite")
    fun addFavorite(
        @PathVariable jobPostId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val favorite = jobPostFavoriteService.addFavorite(currentUser.userId, jobPostId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "favorite" to favorite))
    }

    @DeleteMapping("/posts/{jobPostId}/favorite")
    fun removeFavorite(
        @PathVariable jobPostId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Boolean>> {
        jobPostFavoriteService.removeFavorite(currentUser.userId, jobPostId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @GetMapping("/posts/favorites")
    fun getMyFavoritePosts(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = jobPostFavoriteService.getMyFavorites(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "favorites" to page.content) + pageMeta(page))
    }

    @PostMapping("/posts/{jobPostId}/contact-poster")
    fun contactPoster(
        @PathVariable jobPostId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val conversation = jobPostService.contactPoster(currentUser.userId, jobPostId)
        return ResponseEntity.ok(mapOf("success" to true, "conversation" to conversation))
    }

    // Real 당근알바-style structured application (2026-07-25) -- see
    // JobApplicationService's own doc comment for the full account.
    @PostMapping("/posts/{jobPostId}/apply")
    fun apply(
        @PathVariable jobPostId: String,
        @RequestBody request: ApplyToJobRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val application = jobApplicationService.apply(currentUser.userId, jobPostId, request.message)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "application" to application))
    }

    @GetMapping("/posts/{jobPostId}/applications")
    fun getApplicationsForPost(
        @PathVariable jobPostId: String,
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = jobApplicationService.getApplicationsForPost(currentUser.userId, jobPostId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "applications" to page.content) + pageMeta(page))
    }

    @GetMapping("/my-applications")
    fun getMyApplications(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = jobApplicationService.getMyApplications(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "applications" to page.content) + pageMeta(page))
    }

    // Accepting opens a real conversation (returned inline); declining does not.
    @PostMapping("/applications/{applicationId}/respond")
    fun respondToApplication(
        @PathVariable applicationId: String,
        @RequestBody request: RespondToApplicationRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (application, conversation) = jobApplicationService.respond(currentUser.userId, applicationId, request.accept)
        return ResponseEntity.ok(mapOf("success" to true, "application" to application, "conversation" to conversation))
    }

    @ExceptionHandler(JobPostNotFoundException::class)
    fun handleNotFound(ex: JobPostNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("JOB_POST_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidJobPostException::class)
    fun handleInvalid(ex: InvalidJobPostException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_JOB_POST", ex.message ?: "Bad request"))

    @ExceptionHandler(JobPostNotOpenException::class)
    fun handleNotOpen(ex: JobPostNotOpenException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("JOB_POST_NOT_OPEN", ex.message ?: "Conflict"))

    @ExceptionHandler(OwnJobPostException::class)
    fun handleOwnPost(ex: OwnJobPostException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("OWN_JOB_POST", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidJobCoordinatesException::class)
    fun handleInvalidCoordinates(ex: InvalidJobCoordinatesException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_COORDINATES", ex.message ?: "Bad request"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))

    @ExceptionHandler(JobsNeighborhoodNotSetException::class)
    fun handleNeighborhoodNotSet(ex: JobsNeighborhoodNotSetException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("NEIGHBORHOOD_NOT_SET", ex.message ?: "Bad request"))

    @ExceptionHandler(FavoriteJobPostNotFoundException::class)
    fun handleFavoriteNotFound(ex: FavoriteJobPostNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("JOB_POST_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(WorkerNotFoundException::class)
    fun handleWorkerNotFound(ex: WorkerNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WORKER_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(HoodReviewTransactionNotFoundException::class)
    fun handleReviewTransactionNotFound(ex: HoodReviewTransactionNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("JOB_POST_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(HoodReviewTransactionNotCompletedException::class)
    fun handleReviewTransactionNotCompleted(ex: HoodReviewTransactionNotCompletedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("REVIEW_TRANSACTION_NOT_COMPLETED", ex.message ?: "Conflict"))

    @ExceptionHandler(HoodReviewNoCounterpartyException::class)
    fun handleReviewNoCounterparty(ex: HoodReviewNoCounterpartyException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("REVIEW_NO_COUNTERPARTY", ex.message ?: "Bad request"))

    @ExceptionHandler(HoodReviewNotPartyException::class)
    fun handleReviewNotParty(ex: HoodReviewNotPartyException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("REVIEW_NOT_PARTY", ex.message ?: "Forbidden"))

    @ExceptionHandler(HoodReviewAlreadySubmittedException::class)
    fun handleReviewAlreadySubmitted(ex: HoodReviewAlreadySubmittedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("REVIEW_ALREADY_SUBMITTED", ex.message ?: "Conflict"))

    @ExceptionHandler(InvalidJobApplicationException::class)
    fun handleInvalidApplication(ex: InvalidJobApplicationException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_JOB_APPLICATION", ex.message ?: "Bad request"))

    @ExceptionHandler(JobApplicationAlreadyPendingException::class)
    fun handleApplicationAlreadyPending(ex: JobApplicationAlreadyPendingException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("JOB_APPLICATION_ALREADY_PENDING", ex.message ?: "Conflict"))

    @ExceptionHandler(JobApplicationNotFoundException::class)
    fun handleApplicationNotFound(ex: JobApplicationNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("JOB_APPLICATION_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(JobApplicationNotPendingException::class)
    fun handleApplicationNotPending(ex: JobApplicationNotPendingException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("JOB_APPLICATION_NOT_PENDING", ex.message ?: "Conflict"))
}
