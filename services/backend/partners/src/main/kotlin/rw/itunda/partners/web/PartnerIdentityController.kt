package rw.itunda.partners.web

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.domain.IdentityVerificationStatus
import rw.itunda.core.web.ApiError
import rw.itunda.partners.IdentityVerificationRequestNotFoundException
import rw.itunda.partners.IdentityVerificationService
import rw.itunda.partners.InvalidApiKeyException
import rw.itunda.partners.PartnerSuspendedException

// Real partner-facing side of "verify/sign in with itunda" -- same permitAll-at-the-
// Spring-Security-layer, real-auth-inside-the-service shape PartnerController already
// establishes (a partner has no itunda-user JWT, it authenticates with its own API key).
@RestController
@RequestMapping("/api/v1/partners/identity")
class PartnerIdentityController(
    private val identityVerificationService: IdentityVerificationService,
    private val objectMapper: ObjectMapper,
) {

    @PostMapping("/requests")
    fun createRequest(@RequestHeader("X-Api-Key") apiKey: String): ResponseEntity<Map<String, Any?>> {
        val request = identityVerificationService.createRequest(apiKey)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            mapOf(
                "success" to true,
                "requestId" to request.id,
                // Real deep-link scheme this repo already establishes for authenticated
                // handoffs (see AndroidManifest.xml's own itunda://maps intent-filter).
                "verifyUrl" to "itunda://verify/${request.id}",
                "expiresAt" to request.expiresAt,
            ),
        )
    }

    @GetMapping("/requests/{requestId}")
    fun getRequest(@PathVariable requestId: String, @RequestHeader("X-Api-Key") apiKey: String): ResponseEntity<Map<String, Any?>> {
        val request = identityVerificationService.getForPartner(requestId, apiKey)
        val body = mutableMapOf<String, Any?>("success" to true, "status" to request.status)
        // The disclosed payload/signature only ever exist once a real user has actually
        // approved -- never returned for PENDING/DECLINED/EXPIRED, matching the entity's
        // own "no silent or default disclosure" discipline. Re-parsed into a real nested
        // object here (not returned as the raw stored string) so a partner's own JSON
        // client sees `identity` as structured data, not a double-encoded string -- the
        // stored string itself stays the exact byte sequence `signature` was computed
        // over, so re-serializing it before returning never invalidates the signature
        // (the partner reconstructs the same canonical bytes from these same fields).
        if (request.status == IdentityVerificationStatus.APPROVED) {
            body["identity"] = objectMapper.readValue(request.disclosedPayloadJson, Map::class.java)
            body["signature"] = request.signature
        }
        return ResponseEntity.ok(body)
    }

    @GetMapping("/public-key")
    fun getPublicKey(): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok(
        mapOf("success" to true, "algorithm" to "Ed25519", "publicKey" to identityVerificationService.publicKeyBase64()),
    )

    @ExceptionHandler(IdentityVerificationRequestNotFoundException::class)
    fun handleNotFound(ex: IdentityVerificationRequestNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("VERIFICATION_REQUEST_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidApiKeyException::class)
    fun handleInvalidApiKey(ex: InvalidApiKeyException) =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError("INVALID_API_KEY", ex.message ?: "Unauthorized"))

    @ExceptionHandler(PartnerSuspendedException::class)
    fun handleSuspended(ex: PartnerSuspendedException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("PARTNER_SUSPENDED", ex.message ?: "Forbidden"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError("API_KEY_REQUIRED", "X-Api-Key header is required"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}
