package rw.itunda.realestate.web

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
import rw.itunda.realestate.InvalidOwnershipDecisionReasonException
import rw.itunda.realestate.PropertyOwnershipService
import rw.itunda.realestate.PropertyOwnershipSubmissionNotFoundException
import rw.itunda.realestate.PropertyOwnershipSubmissionNotPendingException

data class DecideOwnershipSubmissionRequest(val approve: Boolean, val reason: String? = null)

// Mapped under api/v1/system/property-verification specifically so it inherits
// SecurityConfig's existing hasRole("ADMIN") rule on the system path prefix, same real
// RBAC gate ComplianceController uses for KYC/KYB -- no new attack surface.
@RestController
@RequestMapping("/api/v1/system/property-verification")
class PropertyOwnershipAdminController(private val propertyOwnershipService: PropertyOwnershipService) {

    @GetMapping("/queue")
    fun queue(@PageableDefault(size = 20) pageable: Pageable): ResponseEntity<Map<String, Any>> {
        val page = propertyOwnershipService.getQueue(pageable)
        return ResponseEntity.ok(mapOf("success" to true, "queue" to page.content) + pageMeta(page))
    }

    @PostMapping("/{submissionId}/decide")
    fun decide(
        @PathVariable submissionId: String,
        @RequestBody request: DecideOwnershipSubmissionRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> {
        val submission = propertyOwnershipService.decide(submissionId, currentUser.userId, request.approve, request.reason)
        return ResponseEntity.ok(mapOf("success" to true, "submission" to submission))
    }

    @ExceptionHandler(PropertyOwnershipSubmissionNotFoundException::class)
    fun handleNotFound(ex: PropertyOwnershipSubmissionNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("PROPERTY_OWNERSHIP_SUBMISSION_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(PropertyOwnershipSubmissionNotPendingException::class)
    fun handleNotPending(ex: PropertyOwnershipSubmissionNotPendingException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("PROPERTY_OWNERSHIP_SUBMISSION_NOT_PENDING", ex.message ?: "Conflict"))

    @ExceptionHandler(InvalidOwnershipDecisionReasonException::class)
    fun handleInvalidDecisionReason(ex: InvalidOwnershipDecisionReasonException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_DECISION_REASON", ex.message ?: "Invalid decision reason"))
}
