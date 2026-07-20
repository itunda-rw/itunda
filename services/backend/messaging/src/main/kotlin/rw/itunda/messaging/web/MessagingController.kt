package rw.itunda.messaging.web

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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import rw.itunda.messaging.ConversationNotFoundException
import rw.itunda.messaging.EmptyMessageException
import rw.itunda.messaging.InvalidReactionException
import rw.itunda.messaging.MessageNotFoundException
import rw.itunda.messaging.MessageTooLongException
import rw.itunda.messaging.MessagingService
import rw.itunda.messaging.RecipientNotFoundException
import rw.itunda.messaging.RecipientRequiredException
import rw.itunda.messaging.SelfConversationException

// One of the two must be set. phoneNumber is the real human-friendly entry point (see
// MessagingService.startOrGetConversationByPhoneNumber's own doc comment); otherUserId
// stays available for a future call site that already resolved a real user id (e.g. a
// "message this merchant" action from a merchant's own profile screen).
data class StartConversationRequest(val phoneNumber: String? = null, val otherUserId: String? = null)
data class SendMessageRequest(val body: String)
data class ToggleReactionRequest(val emoji: String)

// Real 1:1 messaging -- see MessagingService's own doc comment for the full account.
// Normal itunda-user JWT gate (default SecurityConfig .anyRequest().authenticated()),
// same as every other user-facing feature in this backend.
@RestController
@RequestMapping("/api/v1/messages")
class MessagingController(private val messagingService: MessagingService) {

    @PostMapping("/conversations")
    fun startConversation(
        @RequestBody request: StartConversationRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val conversation = when {
            !request.phoneNumber.isNullOrBlank() ->
                messagingService.startOrGetConversationByPhoneNumber(currentUser.userId, request.phoneNumber)
            !request.otherUserId.isNullOrBlank() ->
                messagingService.startOrGetConversation(currentUser.userId, request.otherUserId)
            else -> throw RecipientRequiredException("phoneNumber or otherUserId is required")
        }
        return ResponseEntity.ok(mapOf("success" to true, "conversation" to conversation))
    }

    @GetMapping("/conversations")
    fun listConversations(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = messagingService.listConversations(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "conversations" to page.content) + pageMeta(page))
    }

    @GetMapping("/conversations/{conversationId}/messages")
    fun getMessages(
        @PathVariable conversationId: String,
        @PageableDefault(size = 30) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = messagingService.getMessages(currentUser.userId, conversationId, pageable)
        // Real reaction summaries attached in one batch query (2026-07-19), not one
        // query per message -- see MessagingService.getReactionSummaries's own doc
        // comment.
        val reactionsByMessageId = messagingService.getReactionSummaries(page.content.map { it.id })
        val messages = page.content.map { m ->
            mapOf(
                "id" to m.id, "conversationId" to m.conversationId, "senderId" to m.senderId, "body" to m.body,
                "sentAt" to m.sentAt, "readAt" to m.readAt, "reactions" to (reactionsByMessageId[m.id] ?: emptyList()),
            )
        }
        return ResponseEntity.ok(mapOf("success" to true, "messages" to messages) + pageMeta(page))
    }

    // Real emoji reactions (2026-07-19) -- see MessagingService.toggleReaction's own
    // doc comment for why this is a real toggle (tapping an active reaction removes
    // it), not add/remove as two endpoints.
    @PostMapping("/messages/{messageId}/reactions")
    fun toggleReaction(
        @PathVariable messageId: String,
        @RequestBody request: ToggleReactionRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val reactions = messagingService.toggleReaction(currentUser.userId, messageId, request.emoji)
        return ResponseEntity.ok(mapOf("success" to true, "reactions" to reactions))
    }

    @PostMapping("/conversations/{conversationId}/messages")
    fun sendMessage(
        @PathVariable conversationId: String,
        @RequestBody request: SendMessageRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val message = messagingService.sendMessage(currentUser.userId, conversationId, request.body)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "message" to message))
    }

    // Real online/offline presence (2026-07-19) -- see MessagingService.getPresence's
    // own doc comment. Works for any set of user ids, not just 1:1 conversation
    // partners -- e.g. a group thread can pass every member's id.
    @GetMapping("/presence")
    fun getPresence(@RequestParam userIds: List<String>): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "presence" to messagingService.getPresence(userIds)))

    @ExceptionHandler(RecipientNotFoundException::class)
    fun handleRecipientNotFound(ex: RecipientNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("RECIPIENT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(RecipientRequiredException::class)
    fun handleRecipientRequired(ex: RecipientRequiredException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("RECIPIENT_REQUIRED", ex.message ?: "Bad request"))

    @ExceptionHandler(SelfConversationException::class)
    fun handleSelfConversation(ex: SelfConversationException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("SELF_CONVERSATION_NOT_ALLOWED", ex.message ?: "Bad request"))

    @ExceptionHandler(ConversationNotFoundException::class)
    fun handleConversationNotFound(ex: ConversationNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("CONVERSATION_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(EmptyMessageException::class)
    fun handleEmptyMessage(ex: EmptyMessageException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("EMPTY_MESSAGE", ex.message ?: "Bad request"))

    @ExceptionHandler(MessageTooLongException::class)
    fun handleMessageTooLong(ex: MessageTooLongException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("MESSAGE_TOO_LONG", ex.message ?: "Bad request"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))

    @ExceptionHandler(MessageNotFoundException::class)
    fun handleMessageNotFound(ex: MessageNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MESSAGE_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidReactionException::class)
    fun handleInvalidReaction(ex: InvalidReactionException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_REACTION", ex.message ?: "Bad request"))
}
