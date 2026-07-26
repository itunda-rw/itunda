package rw.itunda.merchant

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import rw.itunda.auth.RateLimitExceededException
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta

data class BroadcastToFollowersRequest(val title: String, val body: String)

// Real Naver Smart Store-style "알림받기" (follow a store) -- see
// MerchantFollowService's own doc comment.
@RestController
@RequestMapping("/api/v1/merchant")
class MerchantFollowController(private val merchantFollowService: MerchantFollowService) {

    @PostMapping("/{merchantId}/follow")
    fun follow(
        @PathVariable merchantId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val follow = merchantFollowService.follow(currentUser.userId, merchantId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "follow" to follow))
    }

    @DeleteMapping("/{merchantId}/follow")
    fun unfollow(
        @PathVariable merchantId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Boolean>> {
        merchantFollowService.unfollow(currentUser.userId, merchantId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @GetMapping("/follows")
    fun getMyFollowedMerchants(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = merchantFollowService.getMyFollowedMerchants(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "follows" to page.content) + pageMeta(page))
    }

    @GetMapping("/followers/count")
    fun getFollowerCount(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "count" to merchantFollowService.getFollowerCount(currentUser.userId)))

    @PostMapping("/followers/broadcast")
    fun broadcastToFollowers(
        @RequestBody request: BroadcastToFollowersRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val result = merchantFollowService.broadcastToFollowers(currentUser.userId, request.title, request.body)
        return ResponseEntity.ok(mapOf("success" to true, "recipientCount" to result.recipientCount))
    }

    @ExceptionHandler(MerchantNotFoundException::class)
    fun handleMerchantNotFound(ex: MerchantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidBroadcastException::class)
    fun handleInvalidBroadcast(ex: InvalidBroadcastException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_BROADCAST", ex.message ?: "Bad request"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}
