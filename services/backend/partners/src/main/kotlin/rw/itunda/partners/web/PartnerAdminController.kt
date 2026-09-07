package rw.itunda.partners.web

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
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
import rw.itunda.core.web.pageMeta
import rw.itunda.partners.PartnerMiniAppNotFoundException
import rw.itunda.partners.PartnerMiniAppNotPendingException
import rw.itunda.partners.PartnerNotFoundException
import rw.itunda.partners.PartnerService

data class DecidePartnerMiniAppRequest(val approve: Boolean, val reason: String? = null)

// Mapped under /api/v1/system/partners specifically so it inherits SecurityConfig's
// existing hasRole("ADMIN") rule on the system path prefix, same convention
// ComplianceController already established for the KYC/KYB review queue.
@RestController
@RequestMapping("/api/v1/system/partners")
class PartnerAdminController(private val partnerService: PartnerService) {

    @GetMapping("/queue")
    fun queue(@PageableDefault(size = 20) pageable: Pageable): ResponseEntity<Map<String, Any?>> {
        val page = partnerService.getQueue(pageable)
        return ResponseEntity.ok(mapOf("success" to true, "queue" to page.content) + pageMeta(page))
    }

    @PostMapping("/{miniAppId}/decide")
    fun decide(
        @PathVariable miniAppId: String,
        @RequestBody request: DecidePartnerMiniAppRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val miniApp = partnerService.decide(miniAppId, currentUser.userId, request.approve, request.reason)
        return ResponseEntity.ok(mapOf("success" to true, "miniApp" to miniApp))
    }

    // Real admin moderation lever (2026-09-07, Partners product-completeness pass) --
    // PartnerService.resolvePartner already real-enforces PartnerStatus.SUSPENDED, but
    // nothing anywhere could ever set a Partner to SUSPENDED until this pass -- same
    // real gap class this sweep already found and fixed for Merchant/vehicle-inspection
    // mechanics.
    @GetMapping
    fun listPartners(): ResponseEntity<Map<String, Any?>> {
        val partners = partnerService.getAllPartners().map {
            mapOf("partnerId" to it.id, "companyName" to it.companyName, "contactEmail" to it.contactEmail, "status" to it.status.name, "createdAt" to it.createdAt.toString())
        }
        return ResponseEntity.ok(mapOf("success" to true, "partners" to partners))
    }

    @PostMapping("/{partnerId}/suspend")
    fun suspend(@PathVariable partnerId: String): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "partner" to partnerService.suspendPartner(partnerId)))

    @PostMapping("/{partnerId}/reactivate")
    fun reactivate(@PathVariable partnerId: String): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "partner" to partnerService.reactivatePartner(partnerId)))

    @ExceptionHandler(PartnerMiniAppNotFoundException::class)
    fun handleNotFound(ex: PartnerMiniAppNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("PARTNER_MINI_APP_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(PartnerMiniAppNotPendingException::class)
    fun handleNotPending(ex: PartnerMiniAppNotPendingException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("PARTNER_MINI_APP_NOT_PENDING", ex.message ?: "Conflict"))

    @ExceptionHandler(PartnerNotFoundException::class)
    fun handlePartnerNotFound(ex: PartnerNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("PARTNER_NOT_FOUND", ex.message ?: "Not found"))
}
