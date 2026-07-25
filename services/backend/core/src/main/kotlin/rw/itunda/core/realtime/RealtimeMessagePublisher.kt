package rw.itunda.core.realtime

import rw.itunda.core.domain.GroupMessage
import rw.itunda.core.domain.Message
import java.time.Instant

/** Real per-emoji reaction breakdown (2026-07-19) -- a raw list of who reacted with
 * each emoji, never a pre-computed "reactedByMe"/count on the wire: every recipient of
 * a reaction push (or REST response) gets the exact same payload and computes their
 * own "did I react"/count locally by checking their own id against `userIds`, the same
 * "one real payload, no per-recipient server-side computation" shape this session's
 * other broadcast pushes (group messages, presence) already use. */
data class ReactionGroup(val emoji: String, val userIds: List<String>)

/**
 * Real live-transport hook for messaging (2026-07-18) -- the "separate, genuinely
 * larger infrastructure concern" `MessagingService`'s own doc comment named as the
 * natural next step after poll-based delivery. Lives in `:core` (not `:messaging`)
 * so the real implementation (`rw.itunda.app.websocket.MessagingWebSocketHandler`,
 * which needs `spring-boot-starter-websocket` and app-level wiring) can live in
 * `:app` without `:messaging` depending on `:app` -- the same dependency-direction
 * discipline this session's module boundaries already follow everywhere else.
 *
 * Deliberately synchronous, in-process, single-instance: a message is pushed
 * directly to whatever WebSocket session the recipient's own JVM process is
 * holding open, not routed through Kafka/the outbox. A Kafka-relayed push would
 * still be real, but the 2s `OutboxRelay` poll interval would make it barely
 * faster than the existing 4s HTTP poll it's meant to replace -- and this repo's own
 * Kafka connectivity has been genuinely flaky in local/dev environments (hostname
 * resolution failures against the private-cloud broker), which would make
 * "real-time" delivery silently depend on infrastructure this backend doesn't
 * reliably have yet. Honestly scoped:
 * this only reaches a recipient whose WebSocket session is held open on the SAME
 * backend instance that processed the send -- a real, named limitation for any
 * future multi-instance/horizontally-scaled deployment (a session-affinity load
 * balancer or a pub/sub fan-out across instances would be the right fix then, not
 * attempted here since itunda currently runs single-instance).
 */
interface RealtimeMessagePublisher {
    fun publishNewMessage(conversationId: String, recipientUserId: String, message: Message)

    /** Same real push, fanned out to every other real member of a group conversation
     * (2026-07-18) -- see `GroupMessagingService`'s own doc comment for the full
     * group-chat account. */
    fun publishNewGroupMessage(groupId: String, recipientUserIds: List<String>, message: GroupMessage)

    /** Real online/offline presence (2026-07-19) -- reads the real WebSocket session
     * registry directly (a user is "online" iff they hold at least one open socket),
     * never a fabricated/cached status. Backs a real `GET /api/v1/messages/presence`
     * poll endpoint for on-demand checks (e.g. any set of group member ids), and see
     * `publishPresenceChange` below for the real-time push half. */
    fun isOnline(userId: String): Boolean

    /** Real-time presence push (2026-07-19) -- fired exactly once per real transition
     * (first session opened -> online, last session closed -> offline), not once per
     * duplicate tab/device connect. Honestly scoped: only 1:1 conversation partners are
     * notified in real time (see `ConversationRepository.findPartnerUserIds`'s own doc
     * comment) -- group members are NOT proactively pushed a presence change, only ever
     * resolved via the poll endpoint above, since a group's fan-out size is unbounded
     * and this is a low-value push for a large/inactive group. */
    fun publishPresenceChange(userId: String, online: Boolean)

    /** Real-time reaction push (2026-07-19) -- fired after a real toggle (add or
     * remove) is durably persisted, to the real other 1:1 conversation partner. */
    fun publishReactionChange(conversationId: String, recipientUserId: String, messageId: String, reactions: List<ReactionGroup>)

    /** Same real push, fanned out to every other real member of a group conversation. */
    fun publishGroupReactionChange(groupId: String, recipientUserIds: List<String>, groupMessageId: String, reactions: List<ReactionGroup>)

    /**
     * Real KakaoTalk-style group read-receipt push (2026-07-26) -- see
     * `GroupMessagingService.getUnreadCounts`'s own doc comment for the real per-member
     * cursor this is built on. Fired every time a real member's `lastReadAt` cursor
     * advances (i.e. they open the thread), fanned out to every other real member so an
     * open thread's per-message countdown decrements live instead of only on next
     * refetch -- the same "live, not polled" bar every other real-time push in this
     * interface already holds itself to.
     */
    fun publishGroupReadReceiptChange(groupId: String, recipientUserIds: List<String>, readByUserId: String, lastReadAt: Instant)
}
