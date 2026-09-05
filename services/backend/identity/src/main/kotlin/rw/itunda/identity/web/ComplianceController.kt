package rw.itunda.identity.web

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
import rw.itunda.identity.IdentityService
import rw.itunda.identity.IdentityUserNotFoundException
import rw.itunda.identity.InvalidDecisionReasonException
import rw.itunda.identity.SubmissionNotFoundException
import rw.itunda.identity.SubmissionNotPendingException

data class DecideSubmissionRequest(val approve: Boolean, val reason: String? = null)

// Mapped under api/v1/system/compliance specifically so it inherits SecurityConfig's
// existing hasRole("ADMIN") rule on the system path prefix without needing a new security
// rule -- same real RBAC gate SystemController already uses, no new attack surface.
@RestController
@RequestMapping("/api/v1/system/compliance")
class ComplianceController(private val identityService: IdentityService) {

    @GetMapping("/queue")
    fun queue(@PageableDefault(size = 20) pageable: Pageable): ResponseEntity<Map<String, Any>> {
        val page = identityService.getQueue(pageable)
        return ResponseEntity.ok(mapOf("success" to true, "queue" to page.content) + pageMeta(page))
    }

    @PostMapping("/{submissionId}/decide")
    fun decide(
        @PathVariable submissionId: String,
        @RequestBody request: DecideSubmissionRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> {
        val submission = identityService.decide(submissionId, currentUser.userId, request.approve, request.reason)
        return ResponseEntity.ok(mapOf("success" to true, "submission" to submission))
    }

    @ExceptionHandler(SubmissionNotFoundException::class)
    fun handleNotFound(ex: SubmissionNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("KYC_SUBMISSION_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(SubmissionNotPendingException::class)
    fun handleNotPending(ex: SubmissionNotPendingException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("KYC_SUBMISSION_NOT_PENDING", ex.message ?: "Conflict"))

    @ExceptionHandler(IdentityUserNotFoundException::class)
    fun handleUserNotFound(ex: IdentityUserNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("USER_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidDecisionReasonException::class)
    fun handleInvalidDecisionReason(ex: InvalidDecisionReasonException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_DECISION_REASON", ex.message ?: "Invalid decision reason"))
}
