package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.Conversation
import rw.itunda.core.domain.Message
import rw.itunda.core.domain.MessageReaction
import rw.itunda.core.domain.UserBlock
import rw.itunda.core.domain.ChatReport

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

// Real batch projection (2026-07-19) -- one row per conversation with an unread
// message, backing MessagingService.listConversations' batched unread-count fetch.
// Property names must match the JPQL SELECT aliases below (Spring Data's
// interface-projection binding is by getter name, not declaration order).
interface ConversationUnreadCount {
    val conversationId: String
    val unreadCount: Long
}

interface MessageRepository : JpaRepository<Message, String> {
    fun findByConversationIdOrderBySentAtDesc(conversationId: String, pageable: Pageable): Page<Message>

    // Real unread-count support for MessagingController.listConversations -- counts
    // messages in a conversation the given user didn't send and hasn't read yet.
    fun countByConversationIdAndSenderIdNotAndReadAtIsNull(conversationId: String, senderId: String): Long

    fun findByConversationIdAndSenderIdNotAndReadAtIsNull(conversationId: String, senderId: String): List<Message>

    @Query(
        "SELECT m FROM Message m WHERE m.conversationId = :conversationId " +
            "AND LOWER(m.body) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY m.sentAt DESC",
    )
    fun searchByConversationIdAndBody(
        @Param("conversationId") conversationId: String,
        @Param("query") query: String,
        pageable: Pageable,
    ): Page<Message>

    // Real batch fetch (2026-07-19) -- see MessagingService.listConversations' own doc
    // comment for the real N+1 this replaces (was one query per conversation for the
    // other user, last message, and unread count each -- up to 3x the page size).
    // Ordered so the caller can take the first row per conversationId as "the last
    // message" without a per-conversation query; bounded via Pageable rather than an
    // unbounded fetch, since a real per-group LIMIT needs a window function this
    // codebase doesn't use raw SQL for yet -- a real, named simplification, not a bug,
    // for the (currently unrealistic) case where >500 total messages across a single
    // page of conversations were sent more recently than a real conversation's own
    // actual last message.
    fun findByConversationIdInOrderBySentAtDesc(conversationIds: List<String>, pageable: Pageable): List<Message>

    @Query(
        "SELECT m.conversationId AS conversationId, COUNT(m) AS unreadCount FROM Message m " +
            "WHERE m.conversationId IN :conversationIds AND m.senderId <> :userId AND m.readAt IS NULL " +
            "GROUP BY m.conversationId",
    )
    fun countUnreadByConversationIds(
        @Param("conversationIds") conversationIds: List<String>,
        @Param("userId") userId: String,
    ): List<ConversationUnreadCount>
}

interface MessageReactionRepository : JpaRepository<MessageReaction, String> {
    fun findByMessageIdAndUserIdAndEmoji(messageId: String, userId: String, emoji: String): MessageReaction?

    fun findByMessageId(messageId: String): List<MessageReaction>

    // Real batch fetch (2026-07-19) -- backs attaching reaction summaries to a whole
    // page of messages in one query rather than one query per message.
    fun findByMessageIdIn(messageIds: List<String>): List<MessageReaction>
}

interface UserBlockRepository : JpaRepository<UserBlock, String> {
    fun existsByBlockerUserIdAndBlockedUserId(blockerUserId: String, blockedUserId: String): Boolean
    fun findByBlockerUserIdAndBlockedUserId(blockerUserId: String, blockedUserId: String): UserBlock?
}

interface ChatReportRepository : JpaRepository<ChatReport, String> {
    fun findByReporterUserIdAndMessageIdAndStatus(reporterUserId: String, messageId: String, status: rw.itunda.core.domain.HoodReportStatus): ChatReport?
    fun findByStatusOrderByCreatedAtAsc(status: rw.itunda.core.domain.HoodReportStatus, pageable: Pageable): Page<ChatReport>
}
