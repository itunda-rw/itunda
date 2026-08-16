package rw.itunda.messaging.web

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.DeleteMapping
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
import rw.itunda.messaging.EmptyGroupMessageException
import rw.itunda.messaging.EmptyMessageException
import rw.itunda.messaging.GroupMessageNotFoundException
import rw.itunda.messaging.GroupMessageTooLongException
import rw.itunda.messaging.GroupNotFoundException
import rw.itunda.messaging.InvalidForwardDestinationException
import rw.itunda.messaging.InvalidReactionException
import rw.itunda.messaging.MessageNotFoundException
import rw.itunda.messaging.MessageDeleteForbiddenException
import rw.itunda.messaging.MessageDestinationType
import rw.itunda.messaging.MessageForwardService
import rw.itunda.messaging.MessageTooLongException
import rw.itunda.messaging.ForwardResult
import rw.itunda.messaging.MessagingService
import rw.itunda.messaging.RecipientNotFoundException
import rw.itunda.messaging.RecipientRequiredException
import rw.itunda.messaging.SelfConversationException
import rw.itunda.messaging.UserBlockedException
import rw.itunda.messaging.InvalidMessageSearchException
import rw.itunda.messaging.InvalidMessageImageException

// One of the two must be set. phoneNumber is the real human-friendly entry point (see
// MessagingService.startOrGetConversationByPhoneNumber's own doc comment); otherUserId
// stays available for a future call site that already resolved a real user id (e.g. a
// "message this merchant" action from a merchant's own profile screen).
data class StartConversationRequest(val phoneNumber: String? = null, val otherUserId: String? = null)
data class SendMessageRequest(val body: String, val replyToMessageId: String? = null, val imageUrl: String? = null)
data class ToggleReactionRequest(val emoji: String)
data class SetConversationQuietRequest(val quiet: Boolean)
data class SetConversationArchivedRequest(val archived: Boolean)
data class SetConversationPinnedToTopRequest(val pinned: Boolean)
// Real message forwarding (2026-07-25) -- see MessageForwardService's own doc comment.
data class ForwardMessageRequest(val destinationType: String, val destinationId: String)

// Real 1:1 messaging -- see MessagingService's own doc comment for the full account.
// Normal itunda-user JWT gate (default SecurityConfig .anyRequest().authenticated()),
// same as every other user-facing feature in this backend.
@RestController
@RequestMapping("/api/v1/messages")
class MessagingController(
    private val messagingService: MessagingService,
    private val messageForwardService: MessageForwardService,
) {

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
        @RequestParam(required = false, defaultValue = "false") archived: Boolean,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = messagingService.listConversations(currentUser.userId, pageable, archived)
        return ResponseEntity.ok(mapOf("success" to true, "conversations" to page.content) + pageMeta(page))
    }

    @GetMapping("/contacts")
    fun listTalkContacts(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "contacts" to messagingService.listTalkContacts(currentUser.userId)))

    // Real KakaoTalk "오늘의 생일" (Today's Birthday) -- see
    // MessagingService.getTodaysBirthdays's own doc comment.
    @GetMapping("/contacts/birthdays-today")
    fun getTodaysBirthdays(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "contacts" to messagingService.getTodaysBirthdays(currentUser.userId)))

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
        // Real Thread support (2026-08-05) -- same batch-fetch discipline as reactions.
        val replyCountsByMessageId = messagingService.getReplyCounts(page.content.map { it.id })
        val messages = page.content.map { m ->
            mapOf(
                "id" to m.id, "conversationId" to m.conversationId, "senderId" to m.senderId, "body" to if (m.deletedAt == null) m.body else "This message was deleted",
                "sentAt" to m.sentAt, "readAt" to m.readAt, "deletedAt" to m.deletedAt, "replyToMessageId" to m.replyToMessageId, "reactions" to (reactionsByMessageId[m.id] ?: emptyList()),
                "replyCount" to (replyCountsByMessageId[m.id] ?: 0L),
                // Real, pre-existing gap fixed 2026-07-26, found while live-verifying
                // the new Emoticon Store send path: imageUrl/forwardedFromMessageId/
                // forwardedFromType were real fields on Message (composer photo send
                // 2026-07-25, forwarding 2026-07-25) but never surfaced here -- a
                // recipient re-fetching this conversation's history (not just the
                // sender's own immediate POST response) never saw a photo, a forwarded-
                // message label, or now an emoticon, only ever the placeholder body text.
                "imageUrl" to m.imageUrl, "emoticonId" to m.emoticonId,
                "forwardedFromMessageId" to m.forwardedFromMessageId, "forwardedFromType" to m.forwardedFromType,
            )
        }
        return ResponseEntity.ok(mapOf("success" to true, "messages" to messages) + pageMeta(page))
    }

    // Real Thread support (2026-08-05) -- closes docs/DESIGN_REFERENCES.md Talk section
    // recommendation #3's remaining gap: Kakao's confirmed 2025 toolkit includes a real
    // reply-expands-into-its-own-sub-conversation view, not just an inline "replying to"
    // tag. Root message first, then every direct reply oldest-first -- the same shape a
    // dedicated thread screen renders directly.
    @GetMapping("/conversations/{conversationId}/messages/{messageId}/thread")
    fun getThread(
        @PathVariable conversationId: String,
        @PathVariable messageId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val thread = messagingService.getThread(currentUser.userId, conversationId, messageId)
        val reactions = messagingService.getReactionSummaries(thread.map { it.id })
        val messages = thread.map { m ->
            mapOf(
                "id" to m.id, "conversationId" to m.conversationId, "senderId" to m.senderId, "body" to if (m.deletedAt == null) m.body else "This message was deleted",
                "sentAt" to m.sentAt, "readAt" to m.readAt, "deletedAt" to m.deletedAt, "replyToMessageId" to m.replyToMessageId, "reactions" to (reactions[m.id] ?: emptyList()),
                "imageUrl" to m.imageUrl, "emoticonId" to m.emoticonId,
                "forwardedFromMessageId" to m.forwardedFromMessageId, "forwardedFromType" to m.forwardedFromType,
            )
        }
        return ResponseEntity.ok(mapOf("success" to true, "messages" to messages))
    }

    @GetMapping("/conversations/{conversationId}/messages/search")
    fun searchMessages(
        @PathVariable conversationId: String,
        @RequestParam query: String,
        @PageableDefault(size = 30) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = messagingService.searchMessages(currentUser.userId, conversationId, query, pageable)
        val reactions = messagingService.getReactionSummaries(page.content.map { it.id })
        val messages = page.content.map { m -> mapOf("id" to m.id, "conversationId" to m.conversationId, "senderId" to m.senderId, "body" to m.body, "sentAt" to m.sentAt, "readAt" to m.readAt, "deletedAt" to m.deletedAt, "replyToMessageId" to m.replyToMessageId, "reactions" to (reactions[m.id] ?: emptyList())) }
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
        val message = messagingService.sendMessage(currentUser.userId, conversationId, request.body, request.replyToMessageId, imageUrl = request.imageUrl)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "message" to message))
    }

    @DeleteMapping("/conversations/{conversationId}/messages/{messageId}")
    fun deleteMessage(@PathVariable conversationId: String, @PathVariable messageId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Boolean>> {
        messagingService.deleteMessage(currentUser.userId, conversationId, messageId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    // Real message forwarding (2026-07-25) -- see MessageForwardService's own doc
    // comment. This message is always the real DIRECT source; destinationType picks
    // whether it lands in another 1:1 conversation or a group.
    @PostMapping("/messages/{messageId}/forward")
    fun forwardMessage(
        @PathVariable messageId: String,
        @RequestBody request: ForwardMessageRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val destinationType = try {
            MessageDestinationType.valueOf(request.destinationType)
        } catch (e: IllegalArgumentException) {
            throw InvalidForwardDestinationException("destinationType must be DIRECT or GROUP")
        }
        val result = messageForwardService.forward(currentUser.userId, MessageDestinationType.DIRECT, messageId, destinationType, request.destinationId)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            when (result) {
                is ForwardResult.Direct -> mapOf("success" to true, "message" to result.message, "destinationType" to "DIRECT")
                is ForwardResult.Group -> mapOf("success" to true, "message" to result.message, "destinationType" to "GROUP")
            },
        )
    }

    @PostMapping("/conversations/{conversationId}/block")
    fun blockParticipant(@PathVariable conversationId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Boolean>> {
        messagingService.blockConversationParticipant(currentUser.userId, conversationId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @DeleteMapping("/conversations/{conversationId}/block")
    fun unblockParticipant(@PathVariable conversationId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Boolean>> {
        messagingService.unblockConversationParticipant(currentUser.userId, conversationId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/conversations/{conversationId}/pin/{messageId}")
    fun pinMessage(@PathVariable conversationId: String, @PathVariable messageId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Boolean>> {
        messagingService.setPinnedMessage(currentUser.userId, conversationId, messageId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @DeleteMapping("/conversations/{conversationId}/pin")
    fun unpinMessage(@PathVariable conversationId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Boolean>> {
        messagingService.setPinnedMessage(currentUser.userId, conversationId, null)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @GetMapping("/conversations/{conversationId}/pin")
    fun getPinnedMessage(@PathVariable conversationId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val message = messagingService.getPinnedMessage(currentUser.userId, conversationId)
        val payload = message?.let {
            mapOf("id" to it.id, "conversationId" to it.conversationId, "senderId" to it.senderId,
                "body" to if (it.deletedAt == null) it.body else "This message was deleted", "sentAt" to it.sentAt, "readAt" to it.readAt,
                "replyToMessageId" to it.replyToMessageId, "reactions" to emptyList<Any>())
        }
        return ResponseEntity.ok(mapOf("success" to true, "message" to payload))
    }

    @PostMapping("/conversations/{conversationId}/quiet")
    fun setConversationQuiet(
        @PathVariable conversationId: String,
        @RequestBody request: SetConversationQuietRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> {
        messagingService.setConversationQuiet(currentUser.userId, conversationId, request.quiet)
        return ResponseEntity.ok(mapOf("success" to true, "quiet" to request.quiet))
    }

    @GetMapping("/conversations/{conversationId}/quiet")
    fun getConversationQuiet(
        @PathVariable conversationId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "quiet" to messagingService.isConversationQuiet(currentUser.userId, conversationId)))

    // Real recoverable archive (2026-08-05) -- see ConversationPreference.archived's
    // own doc comment. Same request/response shape as quiet above, deliberately
    // reusing SetConversationQuietRequest's own {archived:Bool}-shaped sibling rather
    // than a third near-identical DTO.
    @PostMapping("/conversations/{conversationId}/archive")
    fun setConversationArchived(
        @PathVariable conversationId: String,
        @RequestBody request: SetConversationArchivedRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> {
        messagingService.setConversationArchived(currentUser.userId, conversationId, request.archived)
        return ResponseEntity.ok(mapOf("success" to true, "archived" to request.archived))
    }

    @GetMapping("/conversations/{conversationId}/archive")
    fun getConversationArchived(
        @PathVariable conversationId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "archived" to messagingService.isConversationArchived(currentUser.userId, conversationId)))

    // Real KakaoTalk 채팅방 상단 고정 (pin chat room to top) -- see
    // ConversationPreference.pinned's own doc comment. `/pin-to-top`, not `/pin`, to
    // stay distinct from the existing per-message pin at POST/GET
    // /conversations/{conversationId}/pin(/{messageId}) above -- pinning a MESSAGE
    // inside a room and pinning the ROOM itself to the top of the chat list are two
    // real, different KakaoTalk features.
    @PostMapping("/conversations/{conversationId}/pin-to-top")
    fun setConversationPinnedToTop(
        @PathVariable conversationId: String,
        @RequestBody request: SetConversationPinnedToTopRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> {
        messagingService.setConversationPinnedToTop(currentUser.userId, conversationId, request.pinned)
        return ResponseEntity.ok(mapOf("success" to true, "pinned" to request.pinned))
    }

    @GetMapping("/conversations/{conversationId}/pin-to-top")
    fun getConversationPinnedToTop(
        @PathVariable conversationId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "pinned" to messagingService.isConversationPinnedToTop(currentUser.userId, conversationId)))

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

    @ExceptionHandler(InvalidMessageImageException::class)
    fun handleInvalidMessageImage(ex: InvalidMessageImageException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_MESSAGE_IMAGE", ex.message ?: "Bad request"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))

    @ExceptionHandler(MessageNotFoundException::class)
    fun handleMessageNotFound(ex: MessageNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MESSAGE_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidReactionException::class)
    fun handleInvalidReaction(ex: InvalidReactionException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_REACTION", ex.message ?: "Bad request"))

    @ExceptionHandler(UserBlockedException::class)
    fun handleBlocked(ex: UserBlockedException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("CONVERSATION_BLOCKED", ex.message ?: "Unavailable"))

    @ExceptionHandler(InvalidMessageSearchException::class)
    fun handleInvalidSearch(ex: InvalidMessageSearchException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_MESSAGE_SEARCH", ex.message ?: "Bad request"))

    @ExceptionHandler(MessageDeleteForbiddenException::class)
    fun handleDeleteForbidden(ex: MessageDeleteForbiddenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("MESSAGE_DELETE_FORBIDDEN", ex.message ?: "Forbidden"))

    @ExceptionHandler(InvalidForwardDestinationException::class)
    fun handleInvalidForwardDestination(ex: InvalidForwardDestinationException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_FORWARD_DESTINATION", ex.message ?: "Bad request"))

    // Real message forwarding (2026-07-25) -- these four can only surface here when
    // forwarding a direct message TO a group destination (MessageForwardService then
    // calls straight into GroupMessagingService).
    @ExceptionHandler(GroupMessageNotFoundException::class)
    fun handleGroupMessageNotFound(ex: GroupMessageNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MESSAGE_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(GroupNotFoundException::class)
    fun handleGroupNotFound(ex: GroupNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("GROUP_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(EmptyGroupMessageException::class)
    fun handleEmptyGroupMessage(ex: EmptyGroupMessageException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("EMPTY_MESSAGE", ex.message ?: "Bad request"))

    @ExceptionHandler(GroupMessageTooLongException::class)
    fun handleGroupMessageTooLong(ex: GroupMessageTooLongException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("MESSAGE_TOO_LONG", ex.message ?: "Bad request"))
}
