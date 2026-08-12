package rw.itunda.partners.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.partners.IdentityVerificationRequestNotFoundException
import rw.itunda.partners.IdentityVerificationRequestNotPendingException
import rw.itunda.partners.IdentityVerificationService
import rw.itunda.partners.IdentityVerificationUserNotFoundException

// Real, informed-consent side of "verify/sign in with itunda" -- a real itunda user,
// reached via the itunda://verify/{requestId} deep link a partner's own site/app shows,
// reviewing exactly which partner is asking and exactly what will be shared before
// approving. JWT-gated by the default SecurityConfig rule, same as every other real
// itunda-user endpoint -- there is no unauthenticated path into this controller.
@RestController
@RequestMapping("/api/v1/identity/verification")
class IdentityVerificationController(private val identityVerificationService: IdentityVerificationService) {

    @GetMapping("/{requestId}")
    fun getRequest(@PathVariable requestId: String): ResponseEntity<Map<String, Any?>> {
        val (request, partnerName) = identityVerificationService.getForUser(requestId)
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "partnerName" to partnerName,
                "status" to request.status,
                "expiresAt" to request.expiresAt,
                // Fixed for v1 -- see IdentityVerificationService's own doc comment on
                // the user's explicit "full KYC-style" scope choice. A real future pass
                // with per-partner configurable scopes would replace this with whatever
                // that specific request actually asked for.
                "requestedFields" to listOf("First and last name", "Phone number", "ID-verification status", "Date of birth"),
            ),
        )
    }

    @PostMapping("/{requestId}/approve")
    fun approve(@PathVariable requestId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        identityVerificationService.approve(requestId, currentUser.userId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/{requestId}/decline")
    fun decline(@PathVariable requestId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        identityVerificationService.decline(requestId, currentUser.userId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @ExceptionHandler(IdentityVerificationRequestNotFoundException::class)
    fun handleNotFound(ex: IdentityVerificationRequestNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("VERIFICATION_REQUEST_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(IdentityVerificationRequestNotPendingException::class)
    fun handleNotPending(ex: IdentityVerificationRequestNotPendingException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("VERIFICATION_REQUEST_NOT_PENDING", ex.message ?: "Conflict"))

    @ExceptionHandler(IdentityVerificationUserNotFoundException::class)
    fun handleUserNotFound(ex: IdentityVerificationUserNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("USER_NOT_FOUND", ex.message ?: "Not found"))
}
