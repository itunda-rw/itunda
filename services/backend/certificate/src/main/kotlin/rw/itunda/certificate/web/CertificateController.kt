package rw.itunda.certificate.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.certificate.CertificateNotFoundException
import rw.itunda.certificate.CertificateService
import rw.itunda.certificate.CertificateUserNotFoundException
import rw.itunda.certificate.CertificateUserNotVerifiedException
import rw.itunda.certificate.NoCertificateFoundException
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
class CertificateController(private val certificateService: CertificateService) {

    @PostMapping("/issue")
    fun issue(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val (certificate, privateKey) = certificateService.issue(currentUser.userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            mapOf(
                "success" to true,
                "certificate" to certificate,
                // Shown exactly once -- see CertificateService's own doc comment. This
                // backend never persists it and can never show it again.
                "privateKey" to privateKey,
            ),
        )
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
}
