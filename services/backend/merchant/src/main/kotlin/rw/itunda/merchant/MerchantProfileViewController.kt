package rw.itunda.merchant

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError

// Real 비즈프로필 (Karrot Business Profile) visitor-count dashboard -- see
// MerchantProfileViewService's own doc comment.
@RestController
@RequestMapping("/api/v1/merchant/profile-views")
class MerchantProfileViewController(private val merchantProfileViewService: MerchantProfileViewService) {

    @GetMapping("/trend")
    fun getTrend(
        @RequestParam(required = false, defaultValue = "7") days: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "trend" to merchantProfileViewService.getTrend(currentUser.userId, days)))

    @ExceptionHandler(MerchantNotFoundException::class)
    fun handleNotFound(ex: MerchantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_NOT_FOUND", ex.message ?: "Not found"))
}
