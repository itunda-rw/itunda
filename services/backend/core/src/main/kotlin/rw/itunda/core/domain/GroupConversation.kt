package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A real group conversation (2026-07-18) -- the single most defining KakaoTalk
 * capability the original 1:1-only `Conversation`/`Message` pair didn't cover, closing
 * that gap at the user's direct request ("Talk should be 100% like KakaoTalk + 당근
 * 채팅 for Rwanda"). Deliberately a brand-new, additive set of entities
 * (`GroupConversation`/`GroupConversationMember`/`GroupMessage`) rather than widening
 * the existing `Conversation`'s fixed `participantAId`/`participantBId` columns to
 * support N participants -- that would mean migrating already-live, already-proven 1:1
 * conversation data and rewriting `MessagingService`'s own already-verified logic,
 * exactly the kind of risk to already-tested money-adjacent (real chat, real users)
 * code this session's own discipline avoids everywhere else. 1:1 messaging is
 * completely untouched by this.
 *
 * See `GroupMessagingService`'s own doc comment for the real membership/read-tracking
 * design.
 */
@Entity
@Table(name = "group_conversations")
class GroupConversation(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(name = "created_by", nullable = false, length = 64)
    val createdBy: String,

    @Column(name = "last_message_at", nullable = false)
    var lastMessageAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", name = "", createdBy = "")
}

/**
 * A real member of a [GroupConversation] -- `lastReadAt` tracks this member's own read
 * position (a real per-member cursor, updated whenever they fetch a page of messages),
 * the same "viewing the screen marks it read" convention 1:1 `Message.readAt` already
 * uses, just shaped for N members instead of exactly one recipient (a per-message,
 * per-member read-receipt row would be the more granular real WhatsApp/KakaoTalk
 * "seen by" behavior, but a real, explicit v1 simplification: this session's own
 * `unreadCount` needs only "how many messages after my last-read cursor," not who else
 * has seen them yet -- a genuinely separate, later feature if ever needed).
 */
@Entity
@Table(name = "group_conversation_members")
class GroupConversationMember(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "group_conversation_id", nullable = false, length = 64)
    val groupConversationId: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "joined_at", nullable = false)
    val joinedAt: Instant = Instant.now(),

    @Column(name = "last_read_at")
    var lastReadAt: Instant? = null,
) {
    protected constructor() : this(id = "", groupConversationId = "", userId = "")
}

/** A real message within a [GroupConversation] -- same shape as 1:1 `Message`, kept as
 * its own table (not a shared/polymorphic one) so neither entity's queries or FK
 * constraints have to reason about the other's conversation-id space. */
@Entity
@Table(name = "group_messages")
class GroupMessage(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "group_conversation_id", nullable = false, length = 64)
    val groupConversationId: String,

    @Column(name = "sender_id", nullable = false, length = 64)
    val senderId: String,

    @Column(nullable = false, length = 2000)
    val body: String,

    @Column(name = "sent_at", nullable = false)
    val sentAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", groupConversationId = "", senderId = "", body = "")
}
