package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * Per-user controls for a private Talk room. They deliberately do not live on
 * [Conversation]: muting or moving a room aside must never change what the other
 * participant sees. This is the durable basis for KakaoTalk-style quiet rooms.
 */
@Entity
@Table(
    name = "conversation_preferences",
    uniqueConstraints = [UniqueConstraint(name = "uk_conversation_preference_user_room", columnNames = ["conversation_id", "user_id"])],
)
class ConversationPreference(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "conversation_id", nullable = false, length = 64)
    val conversationId: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "quiet", nullable = false)
    var quiet: Boolean = false,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", conversationId = "", userId = "")
}
