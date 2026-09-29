package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Real group 공지 (announcement) (itunda Talk redesign, 2026-08-28) -- one active
 * announcement per group at a time (the reference's own real UX: pinning a new one
 * replaces the old, same "one banner" model as GroupConversation.pinnedMessageId).
 * Creation is deliberately open to "any member," matching every other real group
 * action in this codebase (set photo, set description, pin a message) -- itunda has
 * no admin/owner role beyond `createdBy` anywhere in group chat yet
 * (`GroupMessagingService`'s own doc comment names this as the one still-open
 * follow-up); this feature does not invent a new role system as a side effect.
 */
@Entity
@Table(name = "group_announcements")
class GroupAnnouncement(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "group_conversation_id", nullable = false, length = 64)
    val groupConversationId: String,

    @Column(name = "created_by", nullable = false, length = 64)
    val createdBy: String,

    @Column(nullable = false, length = 1000)
    var body: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", groupConversationId = "", createdBy = "", body = "")
}
