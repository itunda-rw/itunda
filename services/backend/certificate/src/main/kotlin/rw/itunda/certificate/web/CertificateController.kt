package rw.itunda.certificate.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.certificate.CertificateNotFoundException
import rw.itunda.certificate.CertificateRenewalReminderScheduler
import rw.itunda.certificate.CertificateService
import rw.itunda.certificate.CertificateUserNotFoundException
import rw.itunda.certificate.CertificateUserNotVerifiedException
import rw.itunda.certificate.NoCertificateFoundException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError

data class VerifyCertificateSignatureRequest(val serialNumber: String, val payload: String, val signature: String)

// /issue, /me, /revoke manage a real itunda user's own certificate -- default
// SecurityConfig .anyRequest().authenticated() correctly gates those. /verify and
// /status are deliberately different: real public-key signature verification never
// requires a secret credential (that's the entire point of asymmetric crypto) -- a
// real third-party checking a document someone signed with their itunda certificate
// has no itunda account of their own. Both are explicitly permitAll'd in
// SecurityConfig, matching how real CRL/OCSP-style certificate-status checks are
// public. A real bug caught by this pass's own live verification: both were
// initially left behind the default JWT gate, real-401ing any non-itunda-user caller
// -- the opposite of what a public verification endpoint should do.
@RestController
@RequestMapping("/api/v1/certificate")
class CertificateController(
    private val certificateService: CertificateService,
    private val certificateRenewalReminderScheduler: CertificateRenewalReminderScheduler,
    private val idempotencyService: IdempotencyService,
) {

    // Idempotency-Key added 2026-09-07 (Certificate product-completeness pass) --
    // `issue` is the one endpoint in this whole app where a lost response causes
    // irreversible harm: the private key is returned exactly once and never persisted
    // (see CertificateService's own doc comment). Before this fix, a naive client
    // retry after a timeout would create a brand-new certificate (silently revoking
    // the one just issued), permanently orphaning a private key the user may never
    // have actually received. Same no-request-body shape CardController.reissue
    // already establishes -- currentUser.userId stands in for canonical-JSON purposes.
    @PostMapping("/issue")
    fun issue(
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/certificate/issue", idempotencyKey, currentUser.userId) {
            val (certificate, privateKey) = certificateService.issue(currentUser.userId)
            HttpStatus.CREATED.value() to mapOf(
                "success" to true,
                "certificate" to certificate,
                // Shown exactly once -- see CertificateService's own doc comment. This
                // backend never persists it and can never show it again.
                "privateKey" to privateKey,
            )
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/me")
    fun getMyCertificate(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "certificate" to certificateService.getMyCertificate(currentUser.userId)))

    @PostMapping("/revoke")
    fun revoke(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "certificate" to certificateService.revoke(currentUser.userId)))

    @GetMapping("/status/{serialNumber}")
    fun getStatus(@PathVariable serialNumber: String): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "certificate" to certificateService.getStatus(serialNumber)))

    @PostMapping("/verify")
    fun verify(@RequestBody request: VerifyCertificateSignatureRequest): ResponseEntity<Map<String, Any?>> {
        val result = certificateService.verify(request.serialNumber, request.payload, request.signature)
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "signatureValid" to result.signatureValid,
                "certificateStatus" to result.certificateStatus,
                "userId" to result.userId,
                "serialNumber" to result.serialNumber,
            ),
        )
    }

    // Real Korean electronic-certificate renewal-notice manual trigger -- same "expose
    // the scheduler's own real logic as a callable endpoint" convention
    // SavingsController.processMaturityReminders/InsuranceController
    // .processRenewalReminders already establish, so a real certificate's real
    // expiresAt can be verified without waiting actual wall-clock days for it to enter
    // the reminder window.
    // Real gap found live (2026-08-31, market-readiness audit): this fires the
    // reminder job for EVERY user's due certificates system-wide, yet had no ADMIN gate
    // -- any authenticated user could call it. ADMIN-gated the same
    // @PreAuthorize("hasRole('ADMIN')") way WeeklySavingsController.processDue already
    // is (this route doesn't live under /api/v1/system/**, so it doesn't inherit
    // SecurityConfig's blanket ADMIN gate there).
    @PostMapping("/process-renewal-reminders")
    @PreAuthorize("hasRole('ADMIN')")
    fun processRenewalReminders(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val processed = certificateRenewalReminderScheduler.processDue()
        return ResponseEntity.ok(mapOf("success" to true, "processed" to processed))
    }

    @ExceptionHandler(CertificateUserNotFoundException::class)
    fun handleUserNotFound(ex: CertificateUserNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("USER_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(CertificateUserNotVerifiedException::class)
    fun handleNotVerified(ex: CertificateUserNotVerifiedException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("KYC_REQUIRED", ex.message ?: "Forbidden"))

    @ExceptionHandler(NoCertificateFoundException::class)
    fun handleNoCertificate(ex: NoCertificateFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("NO_ACTIVE_CERTIFICATE", ex.message ?: "Not found"))

    @ExceptionHandler(CertificateNotFoundException::class)
    fun handleCertificateNotFound(ex: CertificateNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("CERTIFICATE_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))
}
