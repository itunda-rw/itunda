package rw.itunda.identity.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.identity.IdentityService
import rw.itunda.identity.SubmissionAlreadyPendingException

data class SubmitIdentityRequest(val documentType: String, val documentNumber: String, val documentReference: String)

// Real submission endpoint that previously didn't exist anywhere in this backend --
// docs/TOSS_PARITY_MATRIX.md's Compliance row and docs/API_SPECIFICATION.md both had to be
// corrected earlier this session after a repo-wide grep found no identity route at all.
// documentReference is a demo-mode stand-in for an uploaded ID scan (see KycSubmission.kt) --
// there is no file-storage layer in this backend, and real NIDA verification is blocked on
// regulatory/vendor access regardless. This closes the submission half of that gap; see
// web/ComplianceController.kt for the review/decision half.
@RestController
@RequestMapping("/api/v1/identity")
class IdentityController(private val identityService: IdentityService) {

    @PostMapping("/submit")
    fun submit(
        @RequestBody request: SubmitIdentityRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> {
        val submission = identityService.submit(
            currentUser.userId, request.documentType, request.documentNumber, request.documentReference,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "submission" to submission))
    }

    @GetMapping("/status")
    fun status(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "submissions" to identityService.getMySubmissions(currentUser.userId)))

    @ExceptionHandler(SubmissionAlreadyPendingException::class)
    fun handleAlreadyPending(ex: SubmissionAlreadyPendingException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("KYC_SUBMISSION_ALREADY_PENDING", ex.message ?: "Conflict"))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleInvalidSubmission(ex: IllegalArgumentException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_IDENTITY_SUBMISSION", ex.message ?: "Bad request"))

    // Real gap closed 2026-09-07 (Identity product-completeness pass): submit() calls a
    // real rate-limited service method (IdentityService.submit) but this controller had
    // no handler for it -- unlike nearly every other rate-limited controller in this
    // codebase -- so a real user hitting the limit got a raw 500 instead of a real 429.
    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}
