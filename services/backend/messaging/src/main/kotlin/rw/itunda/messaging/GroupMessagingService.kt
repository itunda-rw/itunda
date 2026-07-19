package rw.itunda.messaging

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.GroupConversation
import rw.itunda.core.domain.GroupConversationMember
import rw.itunda.core.domain.GroupMessage
import rw.itunda.core.domain.GroupMessageReaction
import rw.itunda.core.domain.Notification
import rw.itunda.core.realtime.ReactionGroup
import rw.itunda.core.realtime.RealtimeMessagePublisher
import rw.itunda.core.repository.GroupConversationMemberRepository
import rw.itunda.core.repository.GroupConversationRepository
import rw.itunda.core.repository.GroupMessageReactionRepository
import rw.itunda.core.repository.GroupMessageRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID

class GroupNotFoundException(message: String) : RuntimeException(message)
class GroupNameRequiredException(message: String) : RuntimeException(message)
class GroupNeedsMoreMembersException(message: String) : RuntimeException(message)
class GroupMemberNotFoundException(message: String) : RuntimeException(message)
class AlreadyGroupMemberException(message: String) : RuntimeException(message)
class EmptyGroupMessageException(message: String) : RuntimeException(message)
class GroupMessageNotFoundException(message: String) : RuntimeException(message)
class InvalidGroupReactionException(message: String) : RuntimeException(message)

data class GroupSummary(
    val groupId: String,
    val name: String,
    val memberCount: Int,
    val lastMessageAt: Instant,
    val lastMessagePreview: String?,
    val unreadCount: Long,
)

data class GroupMemberInfo(val userId: String, val name: String)

/**
 * Real group chat -- the single most defining KakaoTalk capability the original
 * 1:1-only messaging pair didn't cover, built at the user's direct request ("Talk
 * should be 100% like KakaoTalk + 당근 채팅 for Rwanda"). See `GroupConversation.kt`'s
 * own doc comment for why this is a brand-new, additive entity set rather than a
 * widened `Conversation`.
 *
 * Real membership (`GroupConversationMember`), real per-member read cursors
 * (`lastReadAt`, not a per-message-per-member row -- an explicit v1 simplification,
 * see `GroupConversationMember`'s own doc comment), real notifications on every new
 * message to every other real member, and a real live push over the same
 * `RealtimeMessagePublisher` 1:1 messaging just added, fanned out to every member
 * instead of a single recipient. Reuses the exact same `RateLimiter` per-sender
 * convention 1:1 `MessagingService.sendMessage` already established.
 *
 * Honestly scoped v1: a group's membership is flat (no admin/owner role beyond
 * `createdBy` being recorded, no kick/promote), and there's no group photo/description
 * -- real, deliberately not attempted in this pass since the defining gap was "can more
 * than two people chat at once at all," not group-management tooling.
 */
@Service
class GroupMessagingService(
    private val groupConversationRepository: GroupConversationRepository,
    private val groupConversationMemberRepository: GroupConversationMemberRepository,
    private val groupMessageRepository: GroupMessageRepository,
    private val userRepository: UserRepository,
    private val notificationRepository: NotificationRepository,
    private val groupMessageReactionRepository: GroupMessageReactionRepository,
    private val rateLimiter: RateLimiter,
    private val realtimeMessagePublisher: RealtimeMessagePublisher,
) {
    @Transactional
    fun createGroup(creatorUserId: String, name: String, memberUserIds: List<String>): GroupConversation {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            throw GroupNameRequiredException("A group needs a name")
        }
        val distinctOtherMembers = memberUserIds.filter { it != creatorUserId }.distinct()
        if (distinctOtherMembers.isEmpty()) {
            throw GroupNeedsMoreMembersException("A group needs at least one other real member")
        }
        distinctOtherMembers.forEach { id ->
            userRepository.findById(id).orElseThrow { GroupMemberNotFoundException("No itunda account found for one of the invited members") }
        }
        return createGroupInternal(creatorUserId, trimmedName, distinctOtherMembers)
    }

    /** Same as [createGroup], resolved by each real member's real phone number -- the
     * human-friendly entry point a real UI needs, same reasoning
     * `MessagingService.startOrGetConversationByPhoneNumber` already established for
     * 1:1 chat (a user only ever knows someone else's phone number, never their
     * internal id). */
    @Transactional
    fun createGroupByPhoneNumbers(creatorUserId: String, name: String, memberPhoneNumbers: List<String>): GroupConversation {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            throw GroupNameRequiredException("A group needs a name")
        }
        val distinctOtherMembers = memberPhoneNumbers.map { it.trim() }.filter { it.isNotEmpty() }.distinct().map { phone ->
            userRepository.findByPhoneNumber(phone)?.id
                ?: throw GroupMemberNotFoundException("No itunda account found for phone number $phone")
        }.filter { it != creatorUserId }.distinct()
        if (distinctOtherMembers.isEmpty()) {
            throw GroupNeedsMoreMembersException("A group needs at least one other real member")
        }
        return createGroupInternal(creatorUserId, trimmedName, distinctOtherMembers)
    }

    private fun createGroupInternal(creatorUserId: String, trimmedName: String, distinctOtherMembers: List<String>): GroupConversation {
        val group = groupConversationRepository.save(
            GroupConversation(id = "group_${UUID.randomUUID()}", name = trimmedName, createdBy = creatorUserId),
        )
        val now = Instant.now()
        val members = (distinctOtherMembers + creatorUserId).map { userId ->
            GroupConversationMember(id = "group_member_${UUID.randomUUID()}", groupConversationId = group.id, userId = userId, joinedAt = now)
        }
        groupConversationMemberRepository.saveAll(members)
        return group
    }

    /** Real 404 (not 403) for a non-member -- same "don't reveal a resource exists to
     * someone who shouldn't see it" discipline `MessagingService.requireParticipant`
     * already established for 1:1 conversations. */
    private fun requireMember(userId: String, groupId: String): GroupConversation {
        val group = groupConversationRepository.findById(groupId).orElseThrow { GroupNotFoundException("Group not found") }
        groupConversationMemberRepository.findByGroupConversationIdAndUserId(groupId, userId)
            ?: throw GroupNotFoundException("Group not found")
        return group
    }

    @Transactional
    fun sendMessage(userId: String, groupId: String, body: String): GroupMessage {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) {
            throw EmptyGroupMessageException("Message body cannot be empty")
        }
        rateLimiter.checkLimit("messaging:group-send:$userId", limit = 30, window = Duration.ofMinutes(1))

        val group = requireMember(userId, groupId)
        val message = groupMessageRepository.save(
            GroupMessage(id = "group_message_${UUID.randomUUID()}", groupConversationId = groupId, senderId = userId, body = trimmed),
        )
        group.lastMessageAt = message.sentAt
        groupConversationRepository.save(group)

        val recipientIds = groupConversationMemberRepository.findByGroupConversationId(groupId)
            .map { it.userId }
            .filter { it != userId }
        val senderName = userRepository.findById(userId).map { "${it.firstName} ${it.lastName}" }.orElse("Someone")
        recipientIds.forEach { recipientId ->
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = recipientId, type = "NEW_GROUP_MESSAGE",
                    title = "${group.name}: $senderName", body = trimmed.take(120),
                    isRead = false, createdAt = Instant.now(), dataJson = "{\"groupConversationId\":\"$groupId\"}",
                ),
            )
        }
        realtimeMessagePublisher.publishNewGroupMessage(groupId, recipientIds, message)
        return message
    }

    @Transactional
    fun getMessages(userId: String, groupId: String, pageable: Pageable): Page<GroupMessage> {
        requireMember(userId, groupId)
        val page = groupMessageRepository.findByGroupConversationIdOrderBySentAtDesc(groupId, pageable)
        val member = groupConversationMemberRepository.findByGroupConversationIdAndUserId(groupId, userId)!!
        member.lastReadAt = Instant.now()
        groupConversationMemberRepository.save(member)
        return page
    }

    fun listMyGroups(userId: String, pageable: Pageable): Page<GroupSummary> {
        val page = groupConversationRepository.findByMember(userId, pageable)
        val summaries = page.content.map { group ->
            val member = groupConversationMemberRepository.findByGroupConversationIdAndUserId(group.id, userId)!!
            val lastMessage = groupMessageRepository.findByGroupConversationIdOrderBySentAtDesc(group.id, Pageable.ofSize(1))
                .content.firstOrNull()
            GroupSummary(
                groupId = group.id,
                name = group.name,
                memberCount = groupConversationMemberRepository.findByGroupConversationId(group.id).size,
                lastMessageAt = group.lastMessageAt,
                lastMessagePreview = lastMessage?.body,
                unreadCount = groupMessageRepository.countUnread(group.id, userId, member.lastReadAt),
            )
        }
        return PageImpl(summaries, pageable, page.totalElements)
    }

    /**
     * Real member list with real resolved display names (2026-07-18) -- closes the
     * honest, repeatedly-named limitation every group chat UI (bank-mfe, Android, iOS)
     * has carried since group chat first shipped: message bubbles showing a truncated
     * sender id instead of a real name. Batch-resolves every member's real name in one
     * `findAllById` call rather than one query per member.
     */
    fun getMembers(userId: String, groupId: String): List<GroupMemberInfo> {
        requireMember(userId, groupId)
        val members = groupConversationMemberRepository.findByGroupConversationId(groupId)
        val usersById = userRepository.findAllById(members.map { it.userId }).associateBy { it.id }
        return members.map { m ->
            val name = usersById[m.userId]?.let { "${it.firstName} ${it.lastName}" } ?: "Unknown user"
            GroupMemberInfo(userId = m.userId, name = name)
        }
    }

    // Real emoji reactions (2026-07-19) -- same real toggle shape as 1:1
    // MessagingService.toggleReaction, fanned out to every other real member.
    @Transactional
    fun toggleReaction(userId: String, groupMessageId: String, emoji: String): List<ReactionGroup> {
        val trimmedEmoji = emoji.trim()
        if (trimmedEmoji.isEmpty() || trimmedEmoji.length > 16) {
            throw InvalidGroupReactionException("Reaction must be between 1 and 16 characters")
        }
        // Real anti-spam limit -- see MessagingService.toggleReaction's own identical
        // note; a group toggle fans out to every other real member, so an unbounded
        // caller here amplifies further than the 1:1 case does.
        rateLimiter.checkLimit("messaging:group-reaction:$userId", limit = 60, window = Duration.ofMinutes(1))
        val message = groupMessageRepository.findById(groupMessageId).orElseThrow { GroupMessageNotFoundException("Message not found") }
        requireMember(userId, message.groupConversationId)

        val existing = groupMessageReactionRepository.findByGroupMessageIdAndUserIdAndEmoji(groupMessageId, userId, trimmedEmoji)
        if (existing != null) {
            groupMessageReactionRepository.delete(existing)
        } else {
            groupMessageReactionRepository.save(
                GroupMessageReaction(id = "group_message_reaction_${UUID.randomUUID()}", groupMessageId = groupMessageId, userId = userId, emoji = trimmedEmoji),
            )
        }

        val reactions = groupReactions(groupMessageReactionRepository.findByGroupMessageId(groupMessageId).map { it.emoji to it.userId })
        val recipientIds = groupConversationMemberRepository.findByGroupConversationId(message.groupConversationId)
            .map { it.userId }
            .filter { it != userId }
        realtimeMessagePublisher.publishGroupReactionChange(message.groupConversationId, recipientIds, groupMessageId, reactions)
        return reactions
    }

    fun getReactionSummaries(groupMessageIds: List<String>): Map<String, List<ReactionGroup>> {
        if (groupMessageIds.isEmpty()) return emptyMap()
        return groupMessageReactionRepository.findByGroupMessageIdIn(groupMessageIds)
            .groupBy { it.groupMessageId }
            .mapValues { (_, reactions) -> groupReactions(reactions.map { it.emoji to it.userId }) }
    }

    private fun groupReactions(emojiAndUserIds: List<Pair<String, String>>): List<ReactionGroup> =
        emojiAndUserIds.groupBy({ it.first }, { it.second }).map { (emoji, userIds) -> ReactionGroup(emoji, userIds) }

    @Transactional
    fun addMember(requesterId: String, groupId: String, newUserId: String): GroupConversation {
        val group = requireMember(requesterId, groupId)
        userRepository.findById(newUserId).orElseThrow { GroupMemberNotFoundException("No itunda account found for this user") }
        if (groupConversationMemberRepository.findByGroupConversationIdAndUserId(groupId, newUserId) != null) {
            throw AlreadyGroupMemberException("This user is already a member of the group")
        }
        groupConversationMemberRepository.save(
            GroupConversationMember(id = "group_member_${UUID.randomUUID()}", groupConversationId = groupId, userId = newUserId),
        )
        return group
    }

    @Transactional
    fun leaveGroup(userId: String, groupId: String) {
        val member = groupConversationMemberRepository.findByGroupConversationIdAndUserId(groupId, userId)
            ?: throw GroupNotFoundException("Group not found")
        groupConversationMemberRepository.delete(member)
    }
}
