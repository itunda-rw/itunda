package rw.itunda.messaging

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Conversation
import rw.itunda.core.domain.Message
import rw.itunda.core.domain.MessageReaction
import rw.itunda.core.domain.Notification
import rw.itunda.core.realtime.ReactionGroup
import rw.itunda.core.realtime.RealtimeMessagePublisher
import rw.itunda.core.repository.ConversationRepository
import rw.itunda.core.repository.MessageReactionRepository
import rw.itunda.core.repository.MessageRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.UserBlockRepository
import rw.itunda.core.repository.ContactRepository
import rw.itunda.core.repository.ConversationPreferenceRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID

class RecipientNotFoundException(message: String) : RuntimeException(message)
class RecipientRequiredException(message: String) : RuntimeException(message)
class SelfConversationException(message: String) : RuntimeException(message)
class ConversationNotFoundException(message: String) : RuntimeException(message)
class EmptyMessageException(message: String) : RuntimeException(message)
class MessageTooLongException(message: String) : RuntimeException(message)
class MessageNotFoundException(message: String) : RuntimeException(message)
class InvalidReactionException(message: String) : RuntimeException(message)
class InvalidMessageSearchException(message: String) : RuntimeException(message)
class UserBlockedException(message: String) : RuntimeException(message)

data class ConversationSummary(
    val conversationId: String,
    val otherUserId: String,
    val otherUserName: String,
    val lastMessageAt: Instant,
    val lastMessagePreview: String?,
    val unreadCount: Long,
)
data class TalkContact(val userId: String, val name: String)

/**
 * Real 1:1 messaging -- the foundational Kakao-style chat primitive named in the
 * 2026-07-17 "super app" goal expansion. Picked as the first of the three newly-named
 * phases (Coupang/e-commerce, 당근마켓/neighborhood marketplace, Kakao/messaging) since
 * a real buyer/seller negotiation channel (당근마켓) and real customer support chat
 * (Coupang) would both eventually need this exact primitive -- building it once now as
 * a real, general-purpose 1:1 conversation system avoids building it three times.
 *
 * Honestly scoped like every other module this session: real conversations, real
 * persisted messages, real unread tracking, real spam rate-limiting. This pass also
 * reuses the existing real `Notification` system (the same one `WalletService`'s
 * budget alerts already use) so a new message always surfaces as a real in-app
 * notification, regardless of whether the recipient has a live connection open.
 *
 * **Real live-transport added 2026-07-18** (`RealtimeMessagePublisher`, implemented by
 * `rw.itunda.app.websocket.MessagingWebSocketHandler`): `sendMessage` pushes the new
 * message straight to the recipient's open WebSocket session, if any, the moment it's
 * persisted. A client without a live connection (or on a poll-only build that hasn't
 * adopted the socket yet) still gets the message via the unchanged poll-based
 * `getMessages`/`listConversations` -- the live push is a real latency improvement
 * layered on top of the always-correct poll path, not a replacement for it.
 */
@Service
class MessagingService(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val notificationRepository: NotificationRepository,
    private val messageReactionRepository: MessageReactionRepository,
    private val rateLimiter: RateLimiter,
    private val realtimeMessagePublisher: RealtimeMessagePublisher,
    private val userBlockRepository: UserBlockRepository,
    private val contactRepository: ContactRepository,
    private val conversationPreferenceRepository: ConversationPreferenceRepository,
) {
    private fun requireNotBlocked(userId: String, otherUserId: String) {
        if (
            userBlockRepository.existsByBlockerUserIdAndBlockedUserId(userId, otherUserId) ||
            userBlockRepository.existsByBlockerUserIdAndBlockedUserId(otherUserId, userId)
        ) throw UserBlockedException("This conversation is unavailable")
    }
    /** Canonical ordering so a real DB unique constraint on (participantAId,
     * participantBId) can enforce "at most one conversation per pair" without a
     * racy check-then-insert -- whichever id sorts first is always stored as A. */
    private fun canonicalPair(userId: String, otherUserId: String): Pair<String, String> =
        if (userId < otherUserId) userId to otherUserId else otherUserId to userId

    @Transactional
    fun startOrGetConversation(userId: String, otherUserId: String): Conversation {
        if (userId == otherUserId) {
            throw SelfConversationException("Cannot start a conversation with yourself")
        }
        userRepository.findById(otherUserId)
            .orElseThrow { RecipientNotFoundException("No itunda account found for this user") }
        requireNotBlocked(userId, otherUserId)

        val (a, b) = canonicalPair(userId, otherUserId)
        conversationRepository.findByParticipantAIdAndParticipantBId(a, b)?.let { return it }

        return conversationRepository.save(
            Conversation(id = "conversation_${UUID.randomUUID()}", participantAId = a, participantBId = b),
        )
    }

    /** Same as [startOrGetConversation], resolved by the other person's real phone
     * number -- the human-friendly entry point a real UI needs (a user doesn't know
     * anyone else's internal `user_...` id, only their phone number, the same real
     * identifier `PayrollService.addEmployee` already resolves a roster addition by). */
    @Transactional
    fun startOrGetConversationByPhoneNumber(userId: String, otherPhoneNumber: String): Conversation {
        val otherUser = userRepository.findByPhoneNumber(otherPhoneNumber)
            ?: throw RecipientNotFoundException("No itunda account found for this phone number")
        return startOrGetConversation(userId, otherUser.id)
    }

    private fun requireParticipant(userId: String, conversationId: String): Conversation {
        val conversation = conversationRepository.findById(conversationId)
            .orElseThrow { ConversationNotFoundException("Conversation not found") }
        // A 404 (not 403) for a non-participant -- same "don't reveal a resource exists
        // to someone who shouldn't see it" discipline MerchantProductService/
        // PayrollService already established for cross-tenant ownership checks.
        if (conversation.participantAId != userId && conversation.participantBId != userId) {
            throw ConversationNotFoundException("Conversation not found")
        }
        return conversation
    }

    /** Public wrapper over [requireParticipant] -- lets a feature built on top of an
     * already-open conversation (e.g. `rw.itunda.gift.GiftService`, a real chat-embedded
     * gift) resolve "the other participant" without duplicating this same IDOR check. */
    fun getConversationForParticipant(userId: String, conversationId: String): Conversation =
        requireParticipant(userId, conversationId)

    @Transactional
    fun sendMessage(userId: String, conversationId: String, body: String): Message {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) {
            throw EmptyMessageException("Message body cannot be empty")
        }
        // Real bound, matching `deliveryAddress`'s own fix on the Eats/Commerce rows
        // the same day -- `body` is VARCHAR(2000), and this DB's real
        // STRICT_TRANS_TABLES mode throws a raw, unhandled 500 on an over-length
        // insert rather than truncating.
        if (trimmed.length > 2000) {
            throw MessageTooLongException("Message body must be 2000 characters or fewer")
        }
        // Real anti-spam rate limit -- a message-send endpoint with no bound is exactly
        // the kind of gap this session's own security review already found and fixed
        // once (MerchantService.chargeCard); built in from the start here instead of
        // discovered later. 30/minute comfortably covers a real fast-typing
        // conversation while bounding a spam/flood burst.
        rateLimiter.checkLimit("messaging:send:$userId", limit = 30, window = Duration.ofMinutes(1))

        val conversation = requireParticipant(userId, conversationId)
        val recipientId = if (conversation.participantAId == userId) conversation.participantBId else conversation.participantAId
        requireNotBlocked(userId, recipientId)
        val message = messageRepository.save(
            Message(id = "message_${UUID.randomUUID()}", conversationId = conversationId, senderId = userId, body = trimmed),
        )
        conversation.lastMessageAt = message.sentAt
        conversationRepository.save(conversation)

        val senderName = userRepository.findById(userId).map { "${it.firstName} ${it.lastName}" }.orElse("Someone")
        // A quiet room is explicitly an auto-mute decision made by this recipient.
        // Keep the message durable and live-delivered if they are already viewing it,
        // but don't create a notification that would surface it outside the room.
        if (conversationPreferenceRepository.findByConversationIdAndUserId(conversationId, recipientId)?.quiet != true) {
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = recipientId, type = "NEW_MESSAGE",
                    title = senderName, body = trimmed.take(120),
                    isRead = false, createdAt = Instant.now(), dataJson = "{\"conversationId\":\"$conversationId\"}",
                ),
            )
        }
        // Real live push, on a best-effort basis -- the message is already durably
        // persisted above regardless of whether anyone is listening right now.
        realtimeMessagePublisher.publishNewMessage(conversationId, recipientId, message)
        return message
    }

    @Transactional
    fun blockConversationParticipant(userId: String, conversationId: String) {
        val conversation = requireParticipant(userId, conversationId)
        val otherUserId = if (conversation.participantAId == userId) conversation.participantBId else conversation.participantAId
        if (!userBlockRepository.existsByBlockerUserIdAndBlockedUserId(userId, otherUserId)) {
            userBlockRepository.save(rw.itunda.core.domain.UserBlock(
                id = "user_block_${UUID.randomUUID()}", blockerUserId = userId, blockedUserId = otherUserId,
            ))
        }
    }

    @Transactional
    fun unblockConversationParticipant(userId: String, conversationId: String) {
        val conversation = requireParticipant(userId, conversationId)
        val otherUserId = if (conversation.participantAId == userId) conversation.participantBId else conversation.participantAId
        userBlockRepository.findByBlockerUserIdAndBlockedUserId(userId, otherUserId)?.let(userBlockRepository::delete)
    }

    /**
     * A quiet room is private to one participant: it is removed from their active
     * attention surface and notifications should be suppressed by clients, without
     * leaving, deleting history, or affecting the other participant.
     */
    @Transactional
    fun setConversationQuiet(userId: String, conversationId: String, quiet: Boolean) {
        requireParticipant(userId, conversationId)
        val preference = conversationPreferenceRepository.findByConversationIdAndUserId(conversationId, userId)
        if (preference == null) {
            if (quiet) conversationPreferenceRepository.save(
                rw.itunda.core.domain.ConversationPreference(
                    id = "conversation_preference_${UUID.randomUUID()}",
                    conversationId = conversationId,
                    userId = userId,
                    quiet = true,
                ),
            )
        } else {
            preference.quiet = quiet
            preference.updatedAt = Instant.now()
            conversationPreferenceRepository.save(preference)
        }
    }

    fun isConversationQuiet(userId: String, conversationId: String): Boolean {
        requireParticipant(userId, conversationId)
        return conversationPreferenceRepository.findByConversationIdAndUserId(conversationId, userId)?.quiet ?: false
    }

    // Real online/offline presence (2026-07-19) -- reads the real WebSocket session
    // registry via RealtimeMessagePublisher.isOnline, never a fabricated status. No
    // participant/membership check on the requested ids -- presence is a real,
    // low-sensitivity signal (same as any messaging app showing a contact's online
    // dot without requiring an existing conversation first).
    fun getPresence(userIds: List<String>): Map<String, Boolean> =
        userIds.distinct().associateWith { realtimeMessagePublisher.isOnline(it) }

    /** A contact directory based only on the caller's own saved contacts, never a public user search. */
    fun listTalkContacts(userId: String): List<TalkContact> {
        val contacts = contactRepository.findByUserId(userId)
        val usersByPhone = userRepository.findAllByPhoneNumberIn(contacts.map { it.phoneNumber }.distinct()).associateBy { it.phoneNumber }
        return contacts.mapNotNull { contact -> usersByPhone[contact.phoneNumber]?.takeIf { it.id != userId }?.let { TalkContact(it.id, contact.name) } }
            .distinctBy { it.userId }.sortedBy { it.name.lowercase() }
    }

    // Real emoji reactions (2026-07-19) -- closes the "message reactions" item on the
    // Talk polish roadmap. Deliberately a toggle: tapping an already-active reaction
    // removes it rather than erroring, the same "add is idempotent-by-toggling, not by
    // 409ing" UX [[project_itunda_toss_parity]] already established for Eats favorites.
    @Transactional
    fun toggleReaction(userId: String, messageId: String, emoji: String): List<ReactionGroup> {
        val trimmedEmoji = emoji.trim()
        if (trimmedEmoji.isEmpty() || trimmedEmoji.length > 16) {
            throw InvalidReactionException("Reaction must be between 1 and 16 characters")
        }
        // Real anti-spam limit (found missing in a 2026-07-19 security review, the same
        // category of gap this codebase's own security reviews have caught before on
        // other endpoints -- toggleReaction shipped without the rate limit sendMessage
        // right above it already has). A toggle is cheap per-call, but unbounded it's
        // still a real DB-write + WebSocket-push amplification vector.
        rateLimiter.checkLimit("messaging:reaction:$userId", limit = 60, window = Duration.ofMinutes(1))
        val message = messageRepository.findById(messageId).orElseThrow { MessageNotFoundException("Message not found") }
        val conversation = requireParticipant(userId, message.conversationId)

        val existing = messageReactionRepository.findByMessageIdAndUserIdAndEmoji(messageId, userId, trimmedEmoji)
        if (existing != null) {
            messageReactionRepository.delete(existing)
        } else {
            messageReactionRepository.save(
                MessageReaction(id = "message_reaction_${UUID.randomUUID()}", messageId = messageId, userId = userId, emoji = trimmedEmoji),
            )
        }

        val reactions = groupReactions(messageReactionRepository.findByMessageId(messageId).map { it.emoji to it.userId })
        val recipientId = if (conversation.participantAId == userId) conversation.participantBId else conversation.participantAId
        realtimeMessagePublisher.publishReactionChange(message.conversationId, recipientId, messageId, reactions)
        return reactions
    }

    // Real batch fetch (2026-07-19) -- backs attaching a reaction summary to every
    // message in a fetched page with a single query, not one query per message.
    fun getReactionSummaries(messageIds: List<String>): Map<String, List<ReactionGroup>> {
        if (messageIds.isEmpty()) return emptyMap()
        return messageReactionRepository.findByMessageIdIn(messageIds)
            .groupBy { it.messageId }
            .mapValues { (_, reactions) -> groupReactions(reactions.map { it.emoji to it.userId }) }
    }

    /** Search stays strictly inside one conversation after the normal participant check. */
    fun searchMessages(userId: String, conversationId: String, query: String, pageable: Pageable): Page<Message> {
        requireParticipant(userId, conversationId)
        val trimmed = query.trim()
        if (trimmed.length < 2 || trimmed.length > 120) {
            throw InvalidMessageSearchException("Search must be between 2 and 120 characters")
        }
        return messageRepository.searchByConversationIdAndBody(conversationId, trimmed, pageable)
    }

    private fun groupReactions(emojiAndUserIds: List<Pair<String, String>>): List<ReactionGroup> =
        emojiAndUserIds.groupBy({ it.first }, { it.second }).map { (emoji, userIds) -> ReactionGroup(emoji, userIds) }

    @Transactional
    fun getMessages(userId: String, conversationId: String, pageable: Pageable): Page<Message> {
        requireParticipant(userId, conversationId)
        val page = messageRepository.findByConversationIdOrderBySentAtDesc(conversationId, pageable)
        // Real read-receipt tracking: fetching a page of a conversation's messages
        // marks the other participant's unread messages in it as read, the same
        // "viewing the screen that shows it is what marks it read" convention every
        // real messaging app uses (not a separate explicit markRead call the client
        // could forget to make).
        val now = Instant.now()
        messageRepository.findByConversationIdAndSenderIdNotAndReadAtIsNull(conversationId, userId)
            .forEach { it.readAt = now }
        return page
    }

    // Real batch fetch (2026-07-19, found in a security/performance sweep) -- was a real
    // N+1: up to 3 queries per conversation (other-user lookup, last message, unread
    // count), so a real 20-item page cost up to 60 queries. Now 4 queries total
    // regardless of page size: the page itself, one batched `findAllById` for every
    // other-participant's real name, one batched ordered fetch for last messages
    // (grouped in-app to "first per conversationId", see
    // MessageRepository.findByConversationIdInOrderBySentAtDesc's own doc comment for
    // the real bound this relies on), and one batched GROUP BY for unread counts.
    fun listConversations(userId: String, pageable: Pageable): Page<ConversationSummary> {
        val page = conversationRepository.findByParticipant(userId, pageable)
        val conversations = page.content
        if (conversations.isEmpty()) return PageImpl(emptyList(), pageable, page.totalElements)

        val otherUserIdByConversationId = conversations.associate { c ->
            c.id to (if (c.participantAId == userId) c.participantBId else c.participantAId)
        }
        val otherUsersById = userRepository.findAllById(otherUserIdByConversationId.values.distinct()).associateBy { it.id }
        val conversationIds = conversations.map { it.id }
        val lastMessageByConversationId = messageRepository
            .findByConversationIdInOrderBySentAtDesc(conversationIds, Pageable.ofSize(500))
            .groupBy { it.conversationId }
            .mapValues { (_, messages) -> messages.first() }
        val unreadCountByConversationId = messageRepository.countUnreadByConversationIds(conversationIds, userId)
            .associate { it.conversationId to it.unreadCount }

        val summaries = conversations.map { conversation ->
            val otherUserId = otherUserIdByConversationId.getValue(conversation.id)
            val otherUser = otherUsersById[otherUserId]
            ConversationSummary(
                conversationId = conversation.id,
                otherUserId = otherUserId,
                otherUserName = otherUser?.let { "${it.firstName} ${it.lastName}" } ?: "Unknown user",
                lastMessageAt = conversation.lastMessageAt,
                lastMessagePreview = lastMessageByConversationId[conversation.id]?.body,
                unreadCount = unreadCountByConversationId[conversation.id] ?: 0L,
            )
        }
        return PageImpl(summaries, pageable, page.totalElements)
    }
}
