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
import rw.itunda.messaging.EmptyGroupMessageException
import rw.itunda.messaging.GroupMemberNotFoundException
import rw.itunda.messaging.GroupMessagingService
import rw.itunda.messaging.GroupNameRequiredException
import rw.itunda.messaging.GroupNeedsMoreMembersException
import rw.itunda.messaging.GroupNotFoundException

// memberPhoneNumbers is the real human-friendly entry point (same reasoning as
// StartConversationRequest.phoneNumber); memberUserIds stays available for a call site
// that already resolved real user ids.
data class CreateGroupRequest(val name: String, val memberUserIds: List<String> = emptyList(), val memberPhoneNumbers: List<String> = emptyList())
data class SendGroupMessageRequest(val body: String)
data class AddGroupMemberRequest(val userId: String)

// Real group chat -- see GroupMessagingService's own doc comment for the full account.
// Normal itunda-user JWT gate, same as every other user-facing feature in this backend.
@RestController
@RequestMapping("/api/v1/messages/groups")
class GroupMessagingController(private val groupMessagingService: GroupMessagingService) {

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
        return ResponseEntity.ok(mapOf("success" to true, "messages" to page.content) + pageMeta(page))
    }

    @PostMapping("/{groupId}/messages")
    fun sendMessage(
        @PathVariable groupId: String,
        @RequestBody request: SendGroupMessageRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val message = groupMessagingService.sendMessage(currentUser.userId, groupId, request.body)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "message" to message))
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

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}
