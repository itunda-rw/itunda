package rw.itunda.partners.web

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
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.partners.PartnerMiniAppNotFoundException
import rw.itunda.partners.PartnerMiniAppNotPendingException
import rw.itunda.partners.PartnerService

data class DecidePartnerMiniAppRequest(val approve: Boolean, val reason: String? = null)

// Mapped under /api/v1/system/partners specifically so it inherits SecurityConfig's
// existing hasRole("ADMIN") rule on the system path prefix, same convention
// ComplianceController already established for the KYC/KYB review queue.
@RestController
@RequestMapping("/api/v1/system/partners")
class PartnerAdminController(private val partnerService: PartnerService) {

    @GetMapping("/queue")
    fun queue(): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "queue" to partnerService.getQueue()))

    @PostMapping("/{miniAppId}/decide")
    fun decide(
        @PathVariable miniAppId: String,
        @RequestBody request: DecidePartnerMiniAppRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val miniApp = partnerService.decide(miniAppId, currentUser.userId, request.approve, request.reason)
        return ResponseEntity.ok(mapOf("success" to true, "miniApp" to miniApp))
    }

    @ExceptionHandler(PartnerMiniAppNotFoundException::class)
    fun handleNotFound(ex: PartnerMiniAppNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("PARTNER_MINI_APP_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(PartnerMiniAppNotPendingException::class)
    fun handleNotPending(ex: PartnerMiniAppNotPendingException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("PARTNER_MINI_APP_NOT_PENDING", ex.message ?: "Conflict"))
}
