package rw.itunda.messaging.web

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
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
import rw.itunda.core.web.pageMeta
import rw.itunda.messaging.AiChatBusyException
import rw.itunda.messaging.AiChatService

data class SendAiChatMessageRequest(val text: String)

// Real AI chatbot channel (2026-08-28) -- see AiChatService's own doc comment.
@RestController
@RequestMapping("/api/v1/talk/ai-chat")
class AiChatController(private val aiChatService: AiChatService) {

    @PostMapping("/messages")
    fun sendMessage(
        @RequestBody request: SendAiChatMessageRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (userMessage, assistantMessage) = aiChatService.sendMessage(currentUser.userId, request.text)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "message" to userMessage, "reply" to assistantMessage))
    }

    @GetMapping("/messages")
    fun getHistory(
        @PageableDefault(size = 30) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = aiChatService.getHistory(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "messages" to page.content) + pageMeta(page))
    }

    @ExceptionHandler(AiChatBusyException::class)
    fun handleBusy(ex: AiChatBusyException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(mapOf("success" to false, "reason" to "busy"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}
