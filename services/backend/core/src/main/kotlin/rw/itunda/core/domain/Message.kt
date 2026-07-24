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

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,

    @Column(name = "deleted_by_user_id", length = 64)
    var deletedByUserId: String? = null,

    // Real message forwarding (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Talk
    // section recommendation #3: Kakao's confirmed real per-message toolkit is Copy/
    // Reply/Forward/Pin/Delete/@mention -- reply/pin/delete already existed, this adds
    // Forward. The source message is always resolved server-side (see
    // MessageForwardService.forward's own doc comment) and its real body copied here --
    // never a client-asserted body -- so `forwardedFromMessageId`/`forwardedFromType`
    // are a genuine, verifiable provenance label, not just a cosmetic tag.
    @Column(name = "forwarded_from_message_id", length = 64)
    val forwardedFromMessageId: String? = null,

    @Column(name = "forwarded_from_type", length = 16)
    val forwardedFromType: String? = null,
) {
    protected constructor() : this(id = "", conversationId = "", senderId = "", body = "")
}
