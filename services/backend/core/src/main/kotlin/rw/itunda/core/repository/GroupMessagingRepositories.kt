package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.GroupConversation
import rw.itunda.core.domain.GroupConversationMember
import rw.itunda.core.domain.GroupMessage

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
}

interface GroupMessageRepository : JpaRepository<GroupMessage, String> {
    fun findByGroupConversationIdOrderBySentAtDesc(groupConversationId: String, pageable: Pageable): Page<GroupMessage>

    // Real unread-count support, the group-chat analogue of MessageRepository's
    // countByConversationIdAndSenderIdNotAndReadAtIsNull -- since group read state is a
    // per-member cursor (GroupConversationMember.lastReadAt), not a per-message flag,
    // this counts messages sent by anyone else after that cursor instead.
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
