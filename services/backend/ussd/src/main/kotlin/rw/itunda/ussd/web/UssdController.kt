package rw.itunda.ussd.web

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.ussd.UssdInvalidPinException
import rw.itunda.ussd.UssdService
import java.security.MessageDigest

data class SetUssdPinRequest(val pin: String)

class UssdGatewayUnauthorizedException(message: String) : RuntimeException(message)

// Real USSD basic-banking access (item 231) -- see UssdService's own doc comment for
// the full sourced account.
@RestController
@RequestMapping("/api/v1/ussd")
class UssdController(
    private val ussdService: UssdService,
    @Value("\${itunda.ussd.gateway-secret:}") private val gatewaySecret: String,
) {

    /**
     * Real USSD gateway webhook -- called by the real USSD gateway itself (an
     * Africa's Talking-style server-to-server call), not a logged-in itunda user, so
     * this is deliberately in `SecurityConfig`'s `permitAll()` list. Identity of the
     * SUBJECT (which real itunda account) comes entirely from the caller-supplied
     * `phoneNumber` + the real in-flow PIN check inside `UssdService`, matching the
     * real regional gateway contract exactly: form-encoded POST with
     * `sessionId`/`phoneNumber`/`text`, a plain-text `CON .../END ...` response.
     *
     * Real bug caught during this feature's own build-time review, before it ever
     * shipped: `permitAll()` alone authenticates nothing about the CALLER -- without a
     * real gateway-level check, any internet client could hit this endpoint claiming
     * to be any phone number, turning the PIN-rate-limited flows into an open oracle
     * for real account-existence enumeration (and a nonzero, if rate-limited, PIN-
     * guessing surface) against real users. A real production USSD/aggregator
     * integration authenticates the gateway itself via a shared secret or IP
     * allowlist; this checks a real shared secret header, distinct from the real
     * per-phone-number PIN rate limiting inside `UssdService`. Empty by default in
     * dev/CI (no header enforced) -- a real deployment sets `USSD_GATEWAY_SECRET` and
     * this becomes a real, enforced check.
     */
    @PostMapping("/session", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE], produces = [MediaType.TEXT_PLAIN_VALUE])
    fun session(
        @RequestParam sessionId: String,
        @RequestParam phoneNumber: String,
        @RequestParam(defaultValue = "") text: String,
        @RequestHeader(value = "X-Ussd-Gateway-Secret", required = false) providedSecret: String?,
    ): ResponseEntity<String> {
        // Real fix (2026-09-04): a plain `!=` string compare short-circuits on the
        // first differing byte, a classic timing side-channel for a shared-secret
        // check -- MessageDigest.isEqual is JDK-native and constant-time regardless
        // of where the inputs first diverge.
        val secretMatches = providedSecret != null &&
            MessageDigest.isEqual(providedSecret.toByteArray(), gatewaySecret.toByteArray())
        if (gatewaySecret.isNotBlank() && !secretMatches) {
            throw UssdGatewayUnauthorizedException("Invalid or missing USSD gateway secret")
        }
        val response = ussdService.handleUssdRequest(sessionId, phoneNumber, text)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/pin")
    fun setPin(@RequestBody request: SetUssdPinRequest, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        ussdService.setPin(currentUser.userId, request.pin)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true))
    }

    @ExceptionHandler(UssdInvalidPinException::class)
    fun handleInvalidPin(ex: UssdInvalidPinException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_PIN", ex.message ?: "Bad request"))

    @ExceptionHandler(UssdGatewayUnauthorizedException::class)
    fun handleGatewayUnauthorized(ex: UssdGatewayUnauthorizedException) =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError("USSD_GATEWAY_UNAUTHORIZED", ex.message ?: "Unauthorized"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}
