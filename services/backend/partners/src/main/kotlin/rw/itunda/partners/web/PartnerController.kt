package rw.itunda.partners.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.web.ApiError
import rw.itunda.partners.InvalidApiKeyException
import rw.itunda.partners.InvalidMiniAppCategoryException
import rw.itunda.partners.InvalidMiniAppDecisionReasonException
import rw.itunda.partners.InvalidMiniAppSubmissionException
import rw.itunda.partners.InvalidPermissionScopeException
import rw.itunda.partners.PartnerEmailAlreadyRegisteredException
import rw.itunda.partners.InvalidPartnerEmailException
import rw.itunda.partners.PartnerMiniAppPermissions
import rw.itunda.partners.PartnerService
import rw.itunda.partners.PartnerSuspendedException

data class RegisterPartnerRequest(val companyName: String, val contactEmail: String)
data class SubmitMiniAppRequest(
    val name: String, val description: String, val iconUrl: String? = null, val bundleUrl: String, val permissions: List<String>,
    val category: String? = null,
)

// Partner-facing developer platform -- mapped outside /api/v1/system/** since a partner
// authenticates with its own real API key (see PartnerService's own doc comment), not a
// itunda-user JWT. permitAll at the Spring Security layer (see SecurityConfig); the real
// auth check happens inside PartnerService.resolvePartner for every endpoint but
// /register, which is how a partner gets a key in the first place.
@RestController
@RequestMapping("/api/v1/partners")
class PartnerController(private val partnerService: PartnerService) {

    @PostMapping("/register")
    fun register(@RequestBody request: RegisterPartnerRequest): ResponseEntity<Map<String, Any?>> {
        val (partner, rawApiKey) = partnerService.register(request.companyName, request.contactEmail)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            mapOf(
                "success" to true,
                "partner" to partner,
                // Shown exactly once -- see Partner.kt's own doc comment. A real
                // developer-portal UX would tell the caller to save this now.
                "apiKey" to rawApiKey,
            ),
        )
    }

    @PostMapping("/mini-apps")
    fun submitMiniApp(
        @RequestBody request: SubmitMiniAppRequest,
        @RequestHeader("X-Api-Key") apiKey: String,
    ): ResponseEntity<Map<String, Any?>> {
        val miniApp = partnerService.submitMiniApp(
            apiKey, request.name, request.description, request.iconUrl, request.bundleUrl, request.permissions,
            request.category,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "miniApp" to miniApp))
    }

    @GetMapping("/mini-apps")
    fun getMyMiniApps(@RequestHeader("X-Api-Key") apiKey: String): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "miniApps" to partnerService.getMyMiniApps(apiKey)))

    @GetMapping("/permissions")
    fun getAllowedPermissions(): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "permissions" to PartnerMiniAppPermissions.ALLOWED))

    @ExceptionHandler(PartnerEmailAlreadyRegisteredException::class)
    fun handleAlreadyRegistered(ex: PartnerEmailAlreadyRegisteredException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("PARTNER_EMAIL_ALREADY_REGISTERED", ex.message ?: "Conflict"))

    @ExceptionHandler(InvalidPartnerEmailException::class)
    fun handleInvalidEmail(ex: InvalidPartnerEmailException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_EMAIL", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidApiKeyException::class)
    fun handleInvalidApiKey(ex: InvalidApiKeyException) =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError("INVALID_API_KEY", ex.message ?: "Unauthorized"))

    @ExceptionHandler(PartnerSuspendedException::class)
    fun handleSuspended(ex: PartnerSuspendedException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("PARTNER_SUSPENDED", ex.message ?: "Forbidden"))

    @ExceptionHandler(InvalidPermissionScopeException::class)
    fun handleInvalidScope(ex: InvalidPermissionScopeException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_PERMISSION_SCOPE", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidMiniAppSubmissionException::class)
    fun handleInvalidSubmission(ex: InvalidMiniAppSubmissionException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_MINI_APP_SUBMISSION", ex.message ?: "Bad request"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError("API_KEY_REQUIRED", "X-Api-Key header is required"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))

    @ExceptionHandler(InvalidMiniAppDecisionReasonException::class)
    fun handleInvalidDecisionReason(ex: InvalidMiniAppDecisionReasonException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_DECISION_REASON", ex.message ?: "Invalid decision reason"))

    @ExceptionHandler(InvalidMiniAppCategoryException::class)
    fun handleInvalidCategory(ex: InvalidMiniAppCategoryException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_MINI_APP_CATEGORY", ex.message ?: "Invalid category"))
}
