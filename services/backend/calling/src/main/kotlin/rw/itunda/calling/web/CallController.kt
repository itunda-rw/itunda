package rw.itunda.calling.web

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
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.calling.CallAlreadyEndedException
import rw.itunda.calling.CallNotFoundException
import rw.itunda.calling.CallNotParticipantException
import rw.itunda.calling.CallService
import rw.itunda.core.domain.CallEndReason
import rw.itunda.core.domain.CallType
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import rw.itunda.messaging.ConversationNotFoundException

data class InitiateCallRequest(val conversationId: String, val callType: CallType)
data class EndCallRequest(val reason: CallEndReason)

// Real 1:1 voice/video calling (2026-08-28) -- see CallService's own doc comment.
@RestController
@RequestMapping("/api/v1/calls")
class CallController(private val callService: CallService) {

    @PostMapping
    fun initiateCall(
        @RequestBody request: InitiateCallRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val call = callService.initiateCall(currentUser.userId, request.conversationId, request.callType)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "call" to call))
    }

    @PostMapping("/{callId}/answer")
    fun answerCall(@PathVariable callId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "call" to callService.answerCall(currentUser.userId, callId)))

    @PostMapping("/{callId}/end")
    fun endCall(
        @PathVariable callId: String,
        @RequestBody request: EndCallRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "call" to callService.endCall(currentUser.userId, callId, request.reason)))

    @GetMapping("/history")
    fun getHistory(
        @PageableDefault(size = 30) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = callService.getHistory(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "calls" to page.content) + pageMeta(page))
    }

    // Real ephemeral TURN credentials -- see CallService.getTurnCredentials's own
    // doc comment. Never a static long-lived secret shipped to a client.
    @GetMapping("/turn-credentials")
    fun getTurnCredentials(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "credentials" to callService.getTurnCredentials(currentUser.userId)))

    @ExceptionHandler(CallNotFoundException::class)
    fun handleCallNotFound(ex: CallNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("CALL_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(CallNotParticipantException::class)
    fun handleNotParticipant(ex: CallNotParticipantException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("CALL_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(CallAlreadyEndedException::class)
    fun handleAlreadyEnded(ex: CallAlreadyEndedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("CALL_ALREADY_ENDED", ex.message ?: "Conflict"))

    @ExceptionHandler(ConversationNotFoundException::class)
    fun handleConversationNotFound(ex: ConversationNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("CONVERSATION_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}
