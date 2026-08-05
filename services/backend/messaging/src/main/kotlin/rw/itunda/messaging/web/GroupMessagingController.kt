package rw.itunda.messaging.web

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import rw.itunda.messaging.AlreadyGroupMemberException
import rw.itunda.messaging.ConversationNotFoundException
import rw.itunda.messaging.EmptyGroupMessageException
import rw.itunda.messaging.EmptyMessageException
import rw.itunda.messaging.ForwardResult
import rw.itunda.messaging.GroupMemberNotFoundException
import rw.itunda.messaging.GroupMessageNotFoundException
import rw.itunda.messaging.GroupMessageDeleteForbiddenException
import rw.itunda.messaging.GroupMessagingService
import rw.itunda.messaging.GroupMessageTooLongException
import rw.itunda.messaging.GroupNameRequiredException
import rw.itunda.messaging.GroupNameTooLongException
import rw.itunda.messaging.GroupNeedsMoreMembersException
import rw.itunda.messaging.GroupNotFoundException
import rw.itunda.messaging.GroupPhotoUrlTooLongException
import rw.itunda.messaging.GroupDescriptionTooLongException
import rw.itunda.messaging.InvalidForwardDestinationException
import rw.itunda.messaging.InvalidGroupReactionException
import rw.itunda.messaging.InvalidGroupMessageImageException
import rw.itunda.messaging.MessageDestinationType
import rw.itunda.messaging.MessageForwardService
import rw.itunda.messaging.MessageNotFoundException
import rw.itunda.messaging.MessageTooLongException
import rw.itunda.messaging.UserBlockedException

// memberPhoneNumbers is the real human-friendly entry point (same reasoning as
// StartConversationRequest.phoneNumber); memberUserIds stays available for a call site
// that already resolved real user ids.
data class CreateGroupRequest(val name: String, val memberUserIds: List<String> = emptyList(), val memberPhoneNumbers: List<String> = emptyList())
data class SendGroupMessageRequest(val body: String, val replyToMessageId: String? = null, val imageUrl: String? = null)
data class AddGroupMemberRequest(val userId: String)
data class ToggleGroupReactionRequest(val emoji: String)
// Real message forwarding (2026-07-25) -- see MessageForwardService's own doc comment.
data class ForwardGroupMessageRequest(val destinationType: String, val destinationId: String)
// Real group photo/description (2026-07-28) -- see GroupMessagingService
// .setGroupPhotoUrl/setGroupDescription's own doc comments.
data class SetGroupPhotoUrlRequest(val photoUrl: String)
data class SetGroupDescriptionRequest(val description: String)

// Real group chat -- see GroupMessagingService's own doc comment for the full account.
// Normal itunda-user JWT gate, same as every other user-facing feature in this backend.
@RestController
@RequestMapping("/api/v1/messages/groups")
class GroupMessagingController(
    private val groupMessagingService: GroupMessagingService,
    private val messageForwardService: MessageForwardService,
) {

    @PostMapping
    fun createGroup(
        @RequestBody request: CreateGroupRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val group = if (request.memberPhoneNumbers.isNotEmpty()) {
            groupMessagingService.createGroupByPhoneNumbers(currentUser.userId, request.name, request.memberPhoneNumbers)
        } else {
            groupMessagingService.createGroup(currentUser.userId, request.name, request.memberUserIds)
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "group" to group))
    }

    @GetMapping
    fun listMyGroups(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = groupMessagingService.listMyGroups(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "groups" to page.content) + pageMeta(page))
    }

    @GetMapping("/{groupId}/messages")
    fun getMessages(
        @PathVariable groupId: String,
        @PageableDefault(size = 30) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = groupMessagingService.getMessages(currentUser.userId, groupId, pageable)
        // Real reaction summaries attached in one batch query (2026-07-19) -- see
        // GroupMessagingService.getReactionSummaries's own doc comment.
        val reactionsByMessageId = groupMessagingService.getReactionSummaries(page.content.map { it.id })
        // Real Kakao-style per-message read-receipt countdown (2026-07-26) -- see
        // GroupMessagingService.getUnreadCounts's own doc comment.
        val unreadCountByMessageId = groupMessagingService.getUnreadCounts(groupId, page.content)
        // Real Thread support (2026-08-05) -- same batch-fetch discipline as reactions.
        val replyCountsByMessageId = groupMessagingService.getReplyCounts(page.content.map { it.id })
        val messages = page.content.map { m ->
            mapOf(
                "id" to m.id, "groupConversationId" to m.groupConversationId, "senderId" to m.senderId, "body" to if (m.deletedAt == null) m.body else "This message was deleted",
                "sentAt" to m.sentAt, "deletedAt" to m.deletedAt, "replyToMessageId" to m.replyToMessageId, "reactions" to (reactionsByMessageId[m.id] ?: emptyList()),
                "unreadCount" to (unreadCountByMessageId[m.id] ?: 0),
                "replyCount" to (replyCountsByMessageId[m.id] ?: 0L),
                // Real, pre-existing gap fixed 2026-07-26 -- see MessagingController
                // .getMessages's own identical fix for the full account; same real
                // fields (composer photo send, forwarding, @mentions, now emoticons)
                // that were real columns on GroupMessage but never surfaced on refetch.
                "imageUrl" to m.imageUrl, "emoticonId" to m.emoticonId,
                "forwardedFromMessageId" to m.forwardedFromMessageId, "forwardedFromType" to m.forwardedFromType,
                "mentionedUserIds" to m.mentionedUserIds,
            )
        }
        return ResponseEntity.ok(mapOf("success" to true, "messages" to messages) + pageMeta(page))
    }

    // Real Thread support (2026-08-05) -- see MessagingController.getThread's own doc
    // comment for the full sourced account; identical shape for group chat.
    @GetMapping("/{groupId}/messages/{messageId}/thread")
    fun getThread(
        @PathVariable groupId: String,
        @PathVariable messageId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val thread = groupMessagingService.getThread(currentUser.userId, groupId, messageId)
        val reactions = groupMessagingService.getReactionSummaries(thread.map { it.id })
        val messages = thread.map { m ->
            mapOf(
                "id" to m.id, "groupConversationId" to m.groupConversationId, "senderId" to m.senderId, "body" to if (m.deletedAt == null) m.body else "This message was deleted",
                "sentAt" to m.sentAt, "deletedAt" to m.deletedAt, "replyToMessageId" to m.replyToMessageId, "reactions" to (reactions[m.id] ?: emptyList()),
                "imageUrl" to m.imageUrl, "emoticonId" to m.emoticonId,
                "forwardedFromMessageId" to m.forwardedFromMessageId, "forwardedFromType" to m.forwardedFromType,
                "mentionedUserIds" to m.mentionedUserIds,
            )
        }
        return ResponseEntity.ok(mapOf("success" to true, "messages" to messages))
    }

    // Real emoji reactions (2026-07-19) -- see GroupMessagingService.toggleReaction's
    // own doc comment.
    @PostMapping("/messages/{groupMessageId}/reactions")
    fun toggleReaction(
        @PathVariable groupMessageId: String,
        @RequestBody request: ToggleGroupReactionRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val reactions = groupMessagingService.toggleReaction(currentUser.userId, groupMessageId, request.emoji)
        return ResponseEntity.ok(mapOf("success" to true, "reactions" to reactions))
    }

    @PostMapping("/{groupId}/messages")
    fun sendMessage(
        @PathVariable groupId: String,
        @RequestBody request: SendGroupMessageRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val message = groupMessagingService.sendMessage(currentUser.userId, groupId, request.body, request.replyToMessageId, imageUrl = request.imageUrl)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "message" to message))
    }

    @DeleteMapping("/{groupId}/messages/{messageId}")
    fun deleteMessage(@PathVariable groupId: String, @PathVariable messageId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Boolean>> {
        groupMessagingService.deleteMessage(currentUser.userId, groupId, messageId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    // Real group photo/description (2026-07-28) -- see GroupMessagingService
    // .setGroupPhotoUrl/setGroupDescription's own doc comments.
    @PostMapping("/{groupId}/photo")
    fun setGroupPhotoUrl(
        @PathVariable groupId: String,
        @RequestBody request: SetGroupPhotoUrlRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val group = groupMessagingService.setGroupPhotoUrl(currentUser.userId, groupId, request.photoUrl)
        return ResponseEntity.ok(mapOf("success" to true, "group" to group))
    }

    @PostMapping("/{groupId}/description")
    fun setGroupDescription(
        @PathVariable groupId: String,
        @RequestBody request: SetGroupDescriptionRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val group = groupMessagingService.setGroupDescription(currentUser.userId, groupId, request.description)
        return ResponseEntity.ok(mapOf("success" to true, "group" to group))
    }

    // Real group-chat pin (2026-07-26) -- see GroupMessagingService.setPinnedMessage's
    // own doc comment; mirrors MessagingController's own 1:1 pin/unpin/get endpoints.
    @PostMapping("/{groupId}/pin/{messageId}")
    fun pinMessage(@PathVariable groupId: String, @PathVariable messageId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Boolean>> {
        groupMessagingService.setPinnedMessage(currentUser.userId, groupId, messageId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @DeleteMapping("/{groupId}/pin")
    fun unpinMessage(@PathVariable groupId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Boolean>> {
        groupMessagingService.setPinnedMessage(currentUser.userId, groupId, null)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @GetMapping("/{groupId}/pin")
    fun getPinnedMessage(@PathVariable groupId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val message = groupMessagingService.getPinnedMessage(currentUser.userId, groupId)
        val payload = message?.let {
            mapOf(
                "id" to it.id, "groupConversationId" to it.groupConversationId, "senderId" to it.senderId,
                "body" to if (it.deletedAt == null) it.body else "This message was deleted", "sentAt" to it.sentAt,
                "replyToMessageId" to it.replyToMessageId, "reactions" to emptyList<Any>(),
            )
        }
        return ResponseEntity.ok(mapOf("success" to true, "message" to payload))
    }

    // Real message forwarding (2026-07-25) -- see MessageForwardService's own doc
    // comment. This message is always the real GROUP source; destinationType picks
    // whether it lands in another group or a 1:1 conversation.
    @PostMapping("/messages/{messageId}/forward")
    fun forwardMessage(
        @PathVariable messageId: String,
        @RequestBody request: ForwardGroupMessageRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val destinationType = try {
            MessageDestinationType.valueOf(request.destinationType)
        } catch (e: IllegalArgumentException) {
            throw InvalidForwardDestinationException("destinationType must be DIRECT or GROUP")
        }
        val result = messageForwardService.forward(currentUser.userId, MessageDestinationType.GROUP, messageId, destinationType, request.destinationId)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            when (result) {
                is ForwardResult.Direct -> mapOf("success" to true, "message" to result.message, "destinationType" to "DIRECT")
                is ForwardResult.Group -> mapOf("success" to true, "message" to result.message, "destinationType" to "GROUP")
            },
        )
    }

    // Real member list with real resolved display names (2026-07-18) -- see
    // GroupMessagingService.getMembers's own doc comment.
    @GetMapping("/{groupId}/members")
    fun getMembers(
        @PathVariable groupId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val members = groupMessagingService.getMembers(currentUser.userId, groupId)
        return ResponseEntity.ok(mapOf("success" to true, "members" to members))
    }

    @PostMapping("/{groupId}/members")
    fun addMember(
        @PathVariable groupId: String,
        @RequestBody request: AddGroupMemberRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val group = groupMessagingService.addMember(currentUser.userId, groupId, request.userId)
        return ResponseEntity.ok(mapOf("success" to true, "group" to group))
    }

    @DeleteMapping("/{groupId}/members/me")
    fun leaveGroup(
        @PathVariable groupId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        groupMessagingService.leaveGroup(currentUser.userId, groupId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @ExceptionHandler(GroupNotFoundException::class)
    fun handleGroupNotFound(ex: GroupNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("GROUP_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(GroupNameRequiredException::class)
    fun handleGroupNameRequired(ex: GroupNameRequiredException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("GROUP_NAME_REQUIRED", ex.message ?: "Bad request"))

    @ExceptionHandler(GroupNameTooLongException::class)
    fun handleGroupNameTooLong(ex: GroupNameTooLongException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("GROUP_NAME_TOO_LONG", ex.message ?: "Bad request"))

    @ExceptionHandler(GroupPhotoUrlTooLongException::class)
    fun handleGroupPhotoUrlTooLong(ex: GroupPhotoUrlTooLongException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("GROUP_PHOTO_URL_TOO_LONG", ex.message ?: "Bad request"))

    @ExceptionHandler(GroupDescriptionTooLongException::class)
    fun handleGroupDescriptionTooLong(ex: GroupDescriptionTooLongException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("GROUP_DESCRIPTION_TOO_LONG", ex.message ?: "Bad request"))

    @ExceptionHandler(GroupNeedsMoreMembersException::class)
    fun handleGroupNeedsMoreMembers(ex: GroupNeedsMoreMembersException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("GROUP_NEEDS_MORE_MEMBERS", ex.message ?: "Bad request"))

    @ExceptionHandler(GroupMemberNotFoundException::class)
    fun handleGroupMemberNotFound(ex: GroupMemberNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MEMBER_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(AlreadyGroupMemberException::class)
    fun handleAlreadyGroupMember(ex: AlreadyGroupMemberException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("ALREADY_MEMBER", ex.message ?: "Conflict"))

    @ExceptionHandler(EmptyGroupMessageException::class)
    fun handleEmptyMessage(ex: EmptyGroupMessageException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("EMPTY_MESSAGE", ex.message ?: "Bad request"))

    @ExceptionHandler(GroupMessageTooLongException::class)
    fun handleMessageTooLong(ex: GroupMessageTooLongException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("MESSAGE_TOO_LONG", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidGroupMessageImageException::class)
    fun handleInvalidMessageImage(ex: InvalidGroupMessageImageException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_MESSAGE_IMAGE", ex.message ?: "Bad request"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))

    @ExceptionHandler(GroupMessageNotFoundException::class)
    fun handleGroupMessageNotFound(ex: GroupMessageNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MESSAGE_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(GroupMessageDeleteForbiddenException::class)
    fun handleDeleteForbidden(ex: GroupMessageDeleteForbiddenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("MESSAGE_DELETE_FORBIDDEN", ex.message ?: "Forbidden"))

    @ExceptionHandler(InvalidGroupReactionException::class)
    fun handleInvalidReaction(ex: InvalidGroupReactionException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_REACTION", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidForwardDestinationException::class)
    fun handleInvalidForwardDestination(ex: InvalidForwardDestinationException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_FORWARD_DESTINATION", ex.message ?: "Bad request"))

    // Real message forwarding (2026-07-25) -- these four can only surface here when
    // forwarding a group message TO a 1:1 destination (MessageForwardService then calls
    // straight into MessagingService), same "handle the other service's exceptions too"
    // discipline MessagingController.forwardMessage's own doc comment names.
    @ExceptionHandler(MessageNotFoundException::class)
    fun handleMessageNotFound(ex: MessageNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MESSAGE_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(ConversationNotFoundException::class)
    fun handleConversationNotFound(ex: ConversationNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("CONVERSATION_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(UserBlockedException::class)
    fun handleBlocked(ex: UserBlockedException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("CONVERSATION_BLOCKED", ex.message ?: "Unavailable"))

    @ExceptionHandler(EmptyMessageException::class)
    fun handleEmptyMessage(ex: EmptyMessageException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("EMPTY_MESSAGE", ex.message ?: "Bad request"))

    @ExceptionHandler(MessageTooLongException::class)
    fun handleMessageTooLong(ex: MessageTooLongException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("MESSAGE_TOO_LONG", ex.message ?: "Bad request"))
}
