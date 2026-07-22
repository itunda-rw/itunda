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
import rw.itunda.core.domain.JobPayType
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import rw.itunda.core.web.trustScores
import rw.itunda.jobs.FavoriteJobPostNotFoundException
import rw.itunda.jobs.InvalidJobCoordinatesException
import rw.itunda.jobs.InvalidJobPostException
import rw.itunda.jobs.JobPostFavoriteService
import rw.itunda.jobs.JobPostNotFoundException
import rw.itunda.jobs.JobPostNotOpenException
import rw.itunda.jobs.JobPostService
import rw.itunda.jobs.JobsNeighborhoodNotSetException
import rw.itunda.jobs.OwnJobPostException
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

// Real 당근알바-style local job board -- see JobPostService's own doc comment. Normal
// itunda-user JWT gate (default SecurityConfig .anyRequest().authenticated()).
@RestController
@RequestMapping("/api/v1/jobs")
class JobPostController(
    private val jobPostService: JobPostService,
    private val jobPostFavoriteService: JobPostFavoriteService,
    private val userRepository: UserRepository,
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

    @GetMapping("/my-posts")
    fun myPosts(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = jobPostService.getMyPosts(currentUser.userId, pageable)
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
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "post" to jobPostService.markFilled(currentUser.userId, jobPostId)))

    @DeleteMapping("/posts/{jobPostId}")
    fun removePost(
        @PathVariable jobPostId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "post" to jobPostService.removePost(currentUser.userId, jobPostId)))

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
}
