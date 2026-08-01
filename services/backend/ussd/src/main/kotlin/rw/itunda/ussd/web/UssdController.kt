package rw.itunda.ussd.web

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.ussd.UssdInvalidPinException
import rw.itunda.ussd.UssdService

data class SetUssdPinRequest(val pin: String)

// Real USSD basic-banking access (item 231) -- see UssdService's own doc comment for
// the full sourced account.
@RestController
@RequestMapping("/api/v1/ussd")
class UssdController(private val ussdService: UssdService) {

    /**
     * Real USSD gateway webhook -- called by the real USSD gateway itself (an
     * Africa's Talking-style server-to-server call), not a logged-in itunda user, so
     * this is deliberately in `SecurityConfig`'s `permitAll()` list. Identity comes
     * entirely from the caller-supplied `phoneNumber` + the real in-flow PIN check
     * inside `UssdService`, matching the real regional gateway contract exactly:
     * form-encoded POST with `sessionId`/`phoneNumber`/`text`, a plain-text
     * `CON .../END ...` response (not JSON).
     */
    @PostMapping("/session", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE], produces = [MediaType.TEXT_PLAIN_VALUE])
    fun session(
        @RequestParam sessionId: String,
        @RequestParam phoneNumber: String,
        @RequestParam(defaultValue = "") text: String,
    ): ResponseEntity<String> {
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

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}
