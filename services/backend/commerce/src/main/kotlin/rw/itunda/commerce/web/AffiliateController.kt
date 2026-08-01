package rw.itunda.commerce.web

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
import rw.itunda.commerce.AffiliateLinkNotFoundException
import rw.itunda.commerce.AffiliateProductNotFoundException
import rw.itunda.commerce.AffiliateService
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError

data class CreateAffiliateLinkRequest(val productId: String)

// Real 쿠팡파트너스 (Coupang Partners)-style affiliate link program (item 229) -- see
// AffiliateService's own doc comment. Normal itunda-user JWT gate; resolveLink is the
// one public-facing read (a shared link must resolve for anyone who clicks it, not
// just the referrer), everything else is caller-scoped.
@RestController
@RequestMapping("/api/v1/affiliate")
class AffiliateController(private val affiliateService: AffiliateService) {

    @PostMapping("/links")
    fun createLink(
        @RequestBody request: CreateAffiliateLinkRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val link = affiliateService.createLink(currentUser.userId, request.productId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "link" to link))
    }

    @GetMapping("/links/my-links")
    fun getMyLinks(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "links" to affiliateService.getMyLinks(currentUser.userId)))

    @GetMapping("/commissions/my-commissions")
    fun getMyCommissions(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "commissions" to affiliateService.getMyCommissions(currentUser.userId)))

    @PostMapping("/links/{code}/resolve")
    fun resolveLink(@PathVariable code: String): ResponseEntity<Map<String, Any?>> {
        val link = affiliateService.resolveLink(code)
        return ResponseEntity.ok(mapOf("success" to true, "link" to link))
    }

    @ExceptionHandler(AffiliateProductNotFoundException::class)
    fun handleProductNotFound(ex: AffiliateProductNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("PRODUCT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(AffiliateLinkNotFoundException::class)
    fun handleLinkNotFound(ex: AffiliateLinkNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("AFFILIATE_LINK_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}
