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
import rw.itunda.core.domain.ConversationPreference

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

    // Real recoverable archive filtering, done at the DB level (not the post-hoc
    // in-app filtering `quiet` uses) so pagination stays correct -- filtering an
    // already-paged result in Kotlin would return short pages whenever any row on
    // that page was archived. `archived = false` (the common case, "not explicitly
    // archived by me") only excludes rows this user actually archived; a user with no
    // ConversationPreference row at all still sees the conversation, matching quiet's
    // own "no row = default false" semantics.
    // Real KakaoTalk pin-to-top ordering, done at the DB level for the same
    // "pagination stays correct" reason archived filtering above already is -- a
    // post-hoc in-app re-sort of an already-paged result would put a pinned room from
    // page 2 above unpinned rooms already returned on page 1. The CASE subselect
    // mirrors the archived NOT IN subselect's own shape/cost, just for `pinned = true`.
    @Query(
        "SELECT c FROM Conversation c WHERE (c.participantAId = :userId OR c.participantBId = :userId) " +
            "AND c.id NOT IN (SELECT p.conversationId FROM ConversationPreference p WHERE p.userId = :userId AND p.archived = true) " +
            "ORDER BY (CASE WHEN c.id IN (SELECT p2.conversationId FROM ConversationPreference p2 WHERE p2.userId = :userId AND p2.pinned = true) THEN 0 ELSE 1 END), c.lastMessageAt DESC",
    )
    fun findByParticipantNotArchived(@Param("userId") userId: String, pageable: Pageable): Page<Conversation>

    @Query(
        "SELECT c FROM Conversation c WHERE (c.participantAId = :userId OR c.participantBId = :userId) " +
            "AND c.id IN (SELECT p.conversationId FROM ConversationPreference p WHERE p.userId = :userId AND p.archived = true) " +
            "ORDER BY c.lastMessageAt DESC",
    )
    fun findByParticipantArchived(@Param("userId") userId: String, pageable: Pageable): Page<Conversation>

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

interface ConversationPreferenceRepository : JpaRepository<ConversationPreference, String> {
    fun findByConversationIdAndUserId(conversationId: String, userId: String): ConversationPreference?
    fun findByUserIdAndConversationIdIn(userId: String, conversationIds: List<String>): List<ConversationPreference>
}

// Real batch projection (2026-07-19) -- one row per conversation with an unread
// message, backing MessagingService.listConversations' batched unread-count fetch.
// Property names must match the JPQL SELECT aliases below (Spring Data's
// interface-projection binding is by getter name, not declaration order).
interface ConversationUnreadCount {
    val conversationId: String
    val unreadCount: Long
}

// Real Thread support (2026-08-05) -- closes docs/DESIGN_REFERENCES.md Talk section
// recommendation #3's remaining "Thread (reply expanding into its own sub-conversation)"
// gap: Kakao's confirmed 2025 toolkit is Copy/Reply/Forward/Pin/Delete/@mention, and
// itunda already has real Reply (a message's own replyToMessageId), just rendered as an
// inline "replying to" tag in the flat timeline, never a real expandable thread view.
// Same batch-projection shape as ConversationUnreadCount, one row per root message that
// has at least one reply -- backs a "N replies" affordance on the main timeline without
// an N+1 query per message.
interface MessageReplyCount {
    val rootMessageId: String
    val replyCount: Long
}

interface MessageRepository : JpaRepository<Message, String> {
    fun findByConversationIdOrderBySentAtDesc(conversationId: String, pageable: Pageable): Page<Message>

    // Real unread-count support for MessagingController.listConversations -- counts
    // messages in a conversation the given user didn't send and hasn't read yet.
    fun countByConversationIdAndSenderIdNotAndReadAtIsNull(conversationId: String, senderId: String): Long

    fun findByConversationIdAndSenderIdNotAndReadAtIsNull(conversationId: String, senderId: String): List<Message>

    @Query(
        "SELECT m FROM Message m WHERE m.conversationId = :conversationId " +
            "AND m.deletedAt IS NULL AND LOWER(m.body) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY m.sentAt DESC",
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

    fun findByReplyToMessageIdAndDeletedAtIsNullOrderBySentAtAsc(replyToMessageId: String): List<Message>

    @Query(
        "SELECT m.replyToMessageId AS rootMessageId, COUNT(m) AS replyCount FROM Message m " +
            "WHERE m.replyToMessageId IN :messageIds AND m.deletedAt IS NULL GROUP BY m.replyToMessageId",
    )
    fun countRepliesByMessageIds(@Param("messageIds") messageIds: List<String>): List<MessageReplyCount>
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
