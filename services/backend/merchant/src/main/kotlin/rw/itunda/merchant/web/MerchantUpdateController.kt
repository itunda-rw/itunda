package rw.itunda.merchant.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.domain.MerchantUpdateLabel
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.merchant.InvalidMerchantUpdateException
import rw.itunda.merchant.MerchantNotFoundException
import rw.itunda.merchant.MerchantUpdateNotFoundException
import rw.itunda.merchant.MerchantUpdateService
import java.time.Instant

data class PostMerchantUpdateRequest(
    val label: MerchantUpdateLabel,
    val title: String,
    val body: String,
    val periodStart: Instant? = null,
    val periodEnd: Instant? = null,
)

// Real business news/updates feed -- see MerchantUpdateService's own doc comment.
@RestController
@RequestMapping("/api/v1/merchant")
class MerchantUpdateController(
    private val merchantUpdateService: MerchantUpdateService,
) {
    @PostMapping("/updates")
    fun postUpdate(
        @RequestBody request: PostMerchantUpdateRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val update = merchantUpdateService.postUpdate(
            currentUser.userId, request.label, request.title, request.body, request.periodStart, request.periodEnd,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "update" to update))
    }

    @GetMapping("/{merchantId}/updates")
    fun getUpdates(@PathVariable merchantId: String): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "updates" to merchantUpdateService.getUpdates(merchantId)))

    @PostMapping("/updates/{updateId}/like")
    fun toggleLike(
        @PathVariable updateId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "liked" to merchantUpdateService.toggleLike(currentUser.userId, updateId)))

    @ExceptionHandler(MerchantNotFoundException::class)
    fun handleMerchantNotFound(ex: MerchantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(MerchantUpdateNotFoundException::class)
    fun handleUpdateNotFound(ex: MerchantUpdateNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_UPDATE_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidMerchantUpdateException::class)
    fun handleInvalid(ex: InvalidMerchantUpdateException) =
        ResponseEntity.badRequest().body(ApiError("INVALID_MERCHANT_UPDATE", ex.message ?: "Invalid request"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}
