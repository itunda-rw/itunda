package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A real message within a [Conversation] -- see that class's own doc comment for why
 * this exists now. `readAt` is null until the recipient (the participant who isn't
 * `senderId`) has fetched a page of messages including this one -- see
 * MessagingService.markConversationRead.
 */
@Entity
@Table(name = "messages")
class Message(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "conversation_id", nullable = false, length = 64)
    val conversationId: String,

    @Column(name = "sender_id", nullable = false, length = 64)
    val senderId: String,

    @Column(nullable = false, length = 2000)
    val body: String,

    @Column(name = "reply_to_message_id", length = 64)
    val replyToMessageId: String? = null,

    @Column(name = "sent_at", nullable = false)
    val sentAt: Instant = Instant.now(),

    @Column(name = "read_at")
    var readAt: Instant? = null,
) {
    protected constructor() : this(id = "", conversationId = "", senderId = "", body = "")
}
