package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A real emoji reaction on a real 1:1 [Message] (2026-07-19) -- closes the "message
 * reactions" item on the Talk polish roadmap. One row per (message, user, emoji): a
 * real DB unique constraint on that triple means a user can react to the same message
 * with several different emoji, but never twice with the same one -- tapping an
 * already-active reaction is a real toggle-off (`MessagingService.toggleReaction`
 * deletes the row rather than erroring), not a duplicate/409.
 */
@Entity
@Table(name = "message_reactions")
class MessageReaction(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "message_id", nullable = false, length = 64)
    val messageId: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(nullable = false, length = 16)
    val emoji: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", messageId = "", userId = "", emoji = "")
}

/** Same real toggle-reaction shape as [MessageReaction], for a real [GroupMessage]
 * instead -- kept as its own table rather than a shared/polymorphic one, matching this
 * codebase's own established "separate tables for separate conversation kinds"
 * convention (see [GroupMessage]'s own doc comment). */
@Entity
@Table(name = "group_message_reactions")
class GroupMessageReaction(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "group_message_id", nullable = false, length = 64)
    val groupMessageId: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(nullable = false, length = 16)
    val emoji: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", groupMessageId = "", userId = "", emoji = "")
}
