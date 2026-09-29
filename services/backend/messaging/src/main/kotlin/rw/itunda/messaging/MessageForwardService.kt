package rw.itunda.messaging

import org.springframework.stereotype.Service
import rw.itunda.core.domain.GroupMessage
import rw.itunda.core.domain.Message

enum class MessageDestinationType { DIRECT, GROUP }

class InvalidForwardDestinationException(message: String) : RuntimeException(message)

sealed class ForwardResult {
    data class Direct(val message: Message) : ForwardResult()
    data class Group(val message: GroupMessage) : ForwardResult()
}

/**
 * Real message forwarding (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Talk
 * section recommendation #3: Kakao's confirmed real per-message toolkit is Copy/
 * Reply/Forward/Pin/Delete/@mention. itunda already had Reply/Pin/Delete (just not
 * unified behind a real long-press menu); Forward was the one genuinely missing
 * capability, and Copy needs no backend at all (a client-side clipboard action).
 *
 * Deliberately its own small orchestrating service rather than adding cross-calls
 * directly inside `MessagingService`/`GroupMessagingService` -- forwarding is the one
 * real feature that needs BOTH (a source message can be a 1:1 or group message, and
 * the destination can independently be either), and this is the natural place for that
 * without making either service depend on the other. Same module (`:messaging`), so no
 * new cross-module dependency.
 *
 * The source message is always resolved and read-authorized server-side
 * (`MessagingService.getMessageForParticipant`/`GroupMessagingService.getMessageForMember`,
 * both of which also reject an already-deleted message) -- the real body forwarded is
 * never a client-asserted string, so a forwarded message's provenance label is always
 * genuine, not just cosmetic.
 */
@Service
class MessageForwardService(
    private val messagingService: MessagingService,
    private val groupMessagingService: GroupMessagingService,
) {
    fun forward(
        userId: String,
        sourceType: MessageDestinationType,
        sourceMessageId: String,
        destinationType: MessageDestinationType,
        destinationId: String,
    ): ForwardResult {
        val body = when (sourceType) {
            MessageDestinationType.DIRECT -> messagingService.getMessageForParticipant(userId, sourceMessageId).body
            MessageDestinationType.GROUP -> groupMessagingService.getMessageForMember(userId, sourceMessageId).body
        }
        return when (destinationType) {
            MessageDestinationType.DIRECT -> ForwardResult.Direct(
                messagingService.sendMessage(
                    userId, destinationId, body,
                    forwardedFromMessageId = sourceMessageId, forwardedFromType = sourceType.name,
                ),
            )
            MessageDestinationType.GROUP -> ForwardResult.Group(
                groupMessagingService.sendMessage(
                    userId, destinationId, body,
                    forwardedFromMessageId = sourceMessageId, forwardedFromType = sourceType.name,
                ),
            )
        }
    }
}
