package rw.itunda.community.web

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
import rw.itunda.community.CommunityNeighborhoodNotSetException
import rw.itunda.community.CommunityPostNotFoundException
import rw.itunda.community.CommunityService
import rw.itunda.community.InvalidCommunityCommentException
import rw.itunda.community.InvalidCommunityCoordinatesException
import rw.itunda.community.InvalidCommunityPostException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta

data class CreateCommunityPostRequest(
    val category: String,
    val title: String,
    val body: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
data class AddCommunityCommentRequest(val body: String)

// Real 동네생활-style community board -- see CommunityService's own doc comment. Normal
// itunda-user JWT gate (default SecurityConfig .anyRequest().authenticated()).
@RestController
@RequestMapping("/api/v1/community")
class CommunityController(private val communityService: CommunityService) {

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
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "post" to post))
    }

    @GetMapping("/posts")
    fun browse(
        @RequestParam(required = false) category: String?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val page = communityService.browse(pageable, category)
        return ResponseEntity.ok(mapOf("success" to true, "posts" to page.content) + pageMeta(page))
    }

    @GetMapping("/posts/nearby")
    fun nearby(
        @RequestParam latitude: Double,
        @RequestParam longitude: Double,
        @RequestParam(required = false, defaultValue = "5.0") radiusKm: Double,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val page = communityService.nearby(latitude, longitude, radiusKm, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "posts" to page.content) + pageMeta(page))
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
        return ResponseEntity.ok(mapOf("success" to true, "posts" to page.content) + pageMeta(page))
    }

    @GetMapping("/my-posts")
    fun myPosts(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = communityService.getMyPosts(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "posts" to page.content) + pageMeta(page))
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

    @PostMapping("/posts/{postId}/like")
    fun toggleLike(
        @PathVariable postId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val liked = communityService.toggleLike(currentUser.userId, postId)
        return ResponseEntity.ok(mapOf("success" to true, "liked" to liked))
    }

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
}
