package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.Conversation
import rw.itunda.core.domain.Message
import rw.itunda.core.domain.MessageReaction

interface ConversationRepository : JpaRepository<Conversation, String> {
    fun findByParticipantAIdAndParticipantBId(participantAId: String, participantBId: String): Conversation?

    // Paginated from the start (this pass's own earlier finding: retrofitting
    // pagination onto an already-shipped unbounded endpoint is real, avoidable extra
    // work -- see docs/TOSS_PARITY_MATRIX.md's Partner SDK row history). A user's
    // conversation list is exactly this kind of unbounded-over-time list.
    @Query(
        "SELECT c FROM Conversation c WHERE c.participantAId = :userId OR c.participantBId = :userId " +
            "ORDER BY c.lastMessageAt DESC",
    )
    fun findByParticipant(@Param("userId") userId: String, pageable: Pageable): Page<Conversation>

    // Real presence push (2026-07-19) -- who to notify when this user comes online/goes
    // offline: every real 1:1 conversation partner. Deliberately unbounded (not
    // paginated like the conversation list above): this only runs once per real
    // connect/disconnect event, not on a hot request path, and a presence push is cheap
    // per recipient (see MessagingWebSocketHandler.sendToUser).
    @Query(
        "SELECT CASE WHEN c.participantAId = :userId THEN c.participantBId ELSE c.participantAId END " +
            "FROM Conversation c WHERE c.participantAId = :userId OR c.participantBId = :userId",
    )
    fun findPartnerUserIds(@Param("userId") userId: String): List<String>
}

interface MessageRepository : JpaRepository<Message, String> {
    fun findByConversationIdOrderBySentAtDesc(conversationId: String, pageable: Pageable): Page<Message>

    // Real unread-count support for MessagingController.listConversations -- counts
    // messages in a conversation the given user didn't send and hasn't read yet.
    fun countByConversationIdAndSenderIdNotAndReadAtIsNull(conversationId: String, senderId: String): Long

    fun findByConversationIdAndSenderIdNotAndReadAtIsNull(conversationId: String, senderId: String): List<Message>
}

interface MessageReactionRepository : JpaRepository<MessageReaction, String> {
    fun findByMessageIdAndUserIdAndEmoji(messageId: String, userId: String, emoji: String): MessageReaction?

    fun findByMessageId(messageId: String): List<MessageReaction>

    // Real batch fetch (2026-07-19) -- backs attaching reaction summaries to a whole
    // page of messages in one query rather than one query per message.
    fun findByMessageIdIn(messageIds: List<String>): List<MessageReaction>
}
