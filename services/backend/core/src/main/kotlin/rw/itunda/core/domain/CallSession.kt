package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class CallType { VOICE, VIDEO }
enum class CallEndReason { MISSED, DECLINED, COMPLETED, CANCELLED, FAILED }

/**
 * Real 1:1 voice/video call session (itunda Talk redesign, 2026-08-28) -- one row per
 * real call attempt, real persisted state transitions (not derived from ephemeral
 * WebRTC signaling traffic), since a real call-log tab needs real history. Explicitly
 * 1:1 only: `conversationId` always references a real `Conversation` (never a
 * `GroupConversation`) -- group calling is a deliberate, separate, deferred future
 * piece (no admin/role model or member cap exists yet to safely gate a group call).
 */
@Entity
@Table(name = "call_sessions")
class CallSession(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "conversation_id", nullable = false, length = 64)
    val conversationId: String,

    @Column(name = "caller_id", nullable = false, length = 64)
    val callerId: String,

    @Column(name = "callee_id", nullable = false, length = 64)
    val calleeId: String,

    @Column(name = "call_type", nullable = false, length = 16)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    val callType: CallType,

    @Column(name = "started_at", nullable = false)
    val startedAt: Instant = Instant.now(),

    @Column(name = "answered_at")
    var answeredAt: Instant? = null,

    @Column(name = "ended_at")
    var endedAt: Instant? = null,

    @Column(name = "end_reason", length = 16)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    var endReason: CallEndReason? = null,
) {
    protected constructor() : this(id = "", conversationId = "", callerId = "", calleeId = "", callType = CallType.VOICE)
}
