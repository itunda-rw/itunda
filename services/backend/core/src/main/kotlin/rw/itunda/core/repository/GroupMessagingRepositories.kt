package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.GroupConversation
import rw.itunda.core.domain.GroupConversationMember
import rw.itunda.core.domain.GroupMessage
import rw.itunda.core.domain.GroupMessageReaction

interface GroupConversationRepository : JpaRepository<GroupConversation, String> {
    // Real pagination from day one, same discipline the 1:1 ConversationRepository
    // already established.
    @Query(
        "SELECT g FROM GroupConversation g JOIN GroupConversationMember m ON m.groupConversationId = g.id " +
            "WHERE m.userId = :userId ORDER BY g.lastMessageAt DESC",
    )
    fun findByMember(@Param("userId") userId: String, pageable: Pageable): Page<GroupConversation>
}

interface GroupConversationMemberRepository : JpaRepository<GroupConversationMember, String> {
    fun findByGroupConversationId(groupConversationId: String): List<GroupConversationMember>
    fun findByGroupConversationIdAndUserId(groupConversationId: String, userId: String): GroupConversationMember?

    // Real batch fetch (2026-07-19) -- backs GroupMessagingService.listMyGroups' batched
    // lookup of the caller's own per-group read cursor across a whole page of groups in
    // one query instead of one per group.
    fun findByGroupConversationIdInAndUserId(groupConversationIds: List<String>, userId: String): List<GroupConversationMember>

    // Real batch projection (2026-07-19) -- a real COUNT per group rather than fetching
    // every member row just to call .size on it (what listMyGroups did before).
    @Query(
        "SELECT m.groupConversationId AS groupConversationId, COUNT(m) AS memberCount FROM GroupConversationMember m " +
            "WHERE m.groupConversationId IN :groupConversationIds GROUP BY m.groupConversationId",
    )
    fun countMembersByGroupConversationIds(@Param("groupConversationIds") groupConversationIds: List<String>): List<GroupMemberCount>
}

// Property names must match the JPQL SELECT aliases above (Spring Data's
// interface-projection binding is by getter name, not declaration order).
interface GroupMemberCount {
    val groupConversationId: String
    val memberCount: Long
}

interface GroupMessageRepository : JpaRepository<GroupMessage, String> {
    fun findByGroupConversationIdOrderBySentAtDesc(groupConversationId: String, pageable: Pageable): Page<GroupMessage>

    // Real batch fetch (2026-07-19) -- see MessageRepository.findByConversationIdInOrderBySentAtDesc's
    // own doc comment for the identical real-bound reasoning; this is the group-chat
    // analogue, used by GroupMessagingService.listMyGroups' batched last-message fetch.
    fun findByGroupConversationIdInOrderBySentAtDesc(groupConversationIds: List<String>, pageable: Pageable): List<GroupMessage>

    // Real unread-count support, the group-chat analogue of MessageRepository's
    // countByConversationIdAndSenderIdNotAndReadAtIsNull -- since group read state is a
    // per-member cursor (GroupConversationMember.lastReadAt), not a per-message flag,
    // this counts messages sent by anyone else after that cursor instead. Deliberately
    // NOT batched across groups (found in the same 2026-07-19 sweep that batched the
    // other three per-group lookups in listMyGroups): each group has its own real
    // lastReadAt cutoff, and expressing "count per group using that group's own cutoff"
    // in one query needs either a correlated subquery or raw SQL this codebase doesn't
    // use elsewhere -- an honest, named partial fix (3 of 4 N+1 sources eliminated) is
    // better than blocking the other three on solving this one, harder case.
    @Query(
        "SELECT COUNT(m) FROM GroupMessage m WHERE m.groupConversationId = :groupId AND m.senderId <> :userId " +
            "AND (:lastReadAt IS NULL OR m.sentAt > :lastReadAt)",
    )
    fun countUnread(
        @Param("groupId") groupId: String,
        @Param("userId") userId: String,
        @Param("lastReadAt") lastReadAt: java.time.Instant?,
    ): Long
}

interface GroupMessageReactionRepository : JpaRepository<GroupMessageReaction, String> {
    fun findByGroupMessageIdAndUserIdAndEmoji(groupMessageId: String, userId: String, emoji: String): GroupMessageReaction?

    fun findByGroupMessageId(groupMessageId: String): List<GroupMessageReaction>

    // Real batch fetch (2026-07-19) -- backs attaching reaction summaries to a whole
    // page of group messages in one query rather than one query per message.
    fun findByGroupMessageIdIn(groupMessageIds: List<String>): List<GroupMessageReaction>
}
