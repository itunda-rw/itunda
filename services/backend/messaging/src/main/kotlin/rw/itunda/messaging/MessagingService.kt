package rw.itunda.messaging

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Conversation
import rw.itunda.core.domain.Message
import rw.itunda.core.domain.Notification
import rw.itunda.core.repository.ConversationRepository
import rw.itunda.core.repository.MessageRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID

class RecipientNotFoundException(message: String) : RuntimeException(message)
class RecipientRequiredException(message: String) : RuntimeException(message)
class SelfConversationException(message: String) : RuntimeException(message)
class ConversationNotFoundException(message: String) : RuntimeException(message)
class EmptyMessageException(message: String) : RuntimeException(message)

data class ConversationSummary(
    val conversationId: String,
    val otherUserId: String,
    val otherUserName: String,
    val lastMessageAt: Instant,
    val lastMessagePreview: String?,
    val unreadCount: Long,
)

/**
 * Real 1:1 messaging -- the foundational Kakao-style chat primitive named in the
 * 2026-07-17 "super app" goal expansion. Picked as the first of the three newly-named
 * phases (Coupang/e-commerce, 당근마켓/neighborhood marketplace, Kakao/messaging) since
 * a real buyer/seller negotiation channel (당근마켓) and real customer support chat
 * (Coupang) would both eventually need this exact primitive -- building it once now as
 * a real, general-purpose 1:1 conversation system avoids building it three times.
 *
 * Honestly scoped like every other module this session: real conversations, real
 * persisted messages, real unread tracking, real spam rate-limiting -- but delivery is
 * poll-based (a client re-fetches `getMessages`/`listConversations`), not a live
 * WebSocket/push channel. A real-time transport is a separate, genuinely larger
 * infrastructure concern (a persistent connection layer this backend has never needed
 * before) -- this pass reuses the existing real `Notification` system (the same one
 * `WalletService`'s budget alerts already use) so a new message at least surfaces as a
 * real in-app notification, rather than inventing a half-real push mechanism. A live
 * transport is the natural next step, not attempted here.
 */
@Service
class MessagingService(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val notificationRepository: NotificationRepository,
    private val rateLimiter: RateLimiter,
) {
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

    @Transactional
    fun sendMessage(userId: String, conversationId: String, body: String): Message {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) {
            throw EmptyMessageException("Message body cannot be empty")
        }
        // Real anti-spam rate limit -- a message-send endpoint with no bound is exactly
        // the kind of gap this session's own security review already found and fixed
        // once (MerchantService.chargeCard); built in from the start here instead of
        // discovered later. 30/minute comfortably covers a real fast-typing
        // conversation while bounding a spam/flood burst.
        rateLimiter.checkLimit("messaging:send:$userId", limit = 30, window = Duration.ofMinutes(1))

        val conversation = requireParticipant(userId, conversationId)
        val message = messageRepository.save(
            Message(id = "message_${UUID.randomUUID()}", conversationId = conversationId, senderId = userId, body = trimmed),
        )
        conversation.lastMessageAt = message.sentAt
        conversationRepository.save(conversation)

        val recipientId = if (conversation.participantAId == userId) conversation.participantBId else conversation.participantAId
        val senderName = userRepository.findById(userId).map { "${it.firstName} ${it.lastName}" }.orElse("Someone")
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = recipientId, type = "NEW_MESSAGE",
                title = senderName, body = trimmed.take(120),
                isRead = false, createdAt = Instant.now(), dataJson = "{\"conversationId\":\"$conversationId\"}",
            ),
        )
        return message
    }

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

    fun listConversations(userId: String, pageable: Pageable): Page<ConversationSummary> {
        val page = conversationRepository.findByParticipant(userId, pageable)
        val summaries = page.content.map { conversation ->
            val otherUserId = if (conversation.participantAId == userId) conversation.participantBId else conversation.participantAId
            val otherUser = userRepository.findById(otherUserId).orElse(null)
            val lastMessage = messageRepository.findByConversationIdOrderBySentAtDesc(conversation.id, Pageable.ofSize(1))
                .content.firstOrNull()
            ConversationSummary(
                conversationId = conversation.id,
                otherUserId = otherUserId,
                otherUserName = otherUser?.let { "${it.firstName} ${it.lastName}" } ?: "Unknown user",
                lastMessageAt = conversation.lastMessageAt,
                lastMessagePreview = lastMessage?.body,
                unreadCount = messageRepository.countByConversationIdAndSenderIdNotAndReadAtIsNull(conversation.id, userId),
            )
        }
        return PageImpl(summaries, pageable, page.totalElements)
    }
}
