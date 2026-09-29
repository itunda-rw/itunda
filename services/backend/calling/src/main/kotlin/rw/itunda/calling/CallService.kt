package rw.itunda.calling

import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.CallEndReason
import rw.itunda.core.domain.CallSession
import rw.itunda.core.domain.CallType
import rw.itunda.core.domain.Notification
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.realtime.RealtimeMessagePublisher
import rw.itunda.core.repository.CallSessionRepository
import rw.itunda.core.repository.ConversationRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository

class CallNotFoundException(message: String) : RuntimeException(message)
class CallNotParticipantException(message: String) : RuntimeException(message)
class CallAlreadyEndedException(message: String) : RuntimeException(message)
class CallConversationNotFoundException(message: String) : RuntimeException(message)

data class TurnCredentials(val username: String, val password: String, val ttlSeconds: Long)

/**
 * Real 1:1 voice/video calling (itunda Talk redesign, 2026-08-28) -- see
 * `CallSession`'s own doc comment. A call is always scoped to a real, existing 1:1
 * `Conversation` between exactly two real participants. Deliberately depends on
 * `ConversationRepository` directly (`:core`) rather than the full `MessagingService`
 * (`:messaging`), inlining the same small IDOR check `MessagingService
 * .requireParticipant` already does -- avoids an unnecessary `:calling -> :messaging`
 * module dependency for a two-line check. Real SDP offer/answer/ICE-candidate
 * signaling is relayed entirely inside `MessagingWebSocketHandler.handleTextMessage`
 * (see `RealtimeMessagePublisher`'s own doc comment on why) -- this service owns only
 * the real, persisted call-state transitions (ring/answer/end) a call-log tab needs,
 * plus [verifyActiveParticipant], the one real hook the WS handler calls into for its
 * own IDOR check before relaying a signaling frame. See the `@Lazy` annotation below
 * for the real, direct bean-construction cycle this creates with
 * `MessagingWebSocketHandler` and how it's resolved.
 */
@Service
class CallService(
    private val callSessionRepository: CallSessionRepository,
    private val conversationRepository: ConversationRepository,
    // Real, sanctioned Spring fix for a real, direct 2-node bean cycle (both
    // directions legitimate, not incidental): MessagingWebSocketHandler needs
    // CallService for its own IDOR check on relayed signaling frames, and this
    // class needs RealtimeMessagePublisher (whose only implementation IS
    // MessagingWebSocketHandler) to push call_ring/call_ended. @Lazy defers actual
    // bean resolution to a proxy until first real call (publishCallRing/
    // publishCallEnded below), breaking the eager-construction cycle Spring hit
    // live at deploy (2026-08-28) -- caught by a real deploy attempt, not the
    // Kotlin compiler, since the module graph itself has no compile-time cycle.
    @Lazy private val realtimeMessagePublisher: RealtimeMessagePublisher,
    private val rateLimiter: RateLimiter,
    private val userRepository: UserRepository,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
    @Value("\${itunda.turn.secret:}") private val turnSecret: String,
) {
    private val log = LoggerFactory.getLogger(CallService::class.java)

    companion object {
        private val TURN_CREDENTIAL_TTL = Duration.ofHours(6)
    }

    /** Real durable-notification fallback for a callee with no live WebSocket session --
     * `realtimeMessagePublisher.publishCallRing` is a silent no-op when nobody's
     * connected (`MessagingWebSocketHandler.sendToUser`'s own doc comment). Matches
     * `MessagingService.sendMessage`'s own established "save a Notification row, push
     * after commit" convention for exactly this offline-recipient case -- a missed call
     * is the single most important case for that fallback to exist at all. */
    private fun notifyIncomingCall(calleeId: String, callerId: String, callId: String, callType: CallType) {
        val callerName = userRepository.findById(callerId).map { "${it.firstName} ${it.lastName}" }.orElse("Someone")
        val body = "Incoming ${callType.name.lowercase()} call"
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = calleeId, type = "INCOMING_CALL",
                title = callerName, body = body,
                isRead = false, createdAt = Instant.now(), dataJson = "{\"callId\":\"$callId\"}",
            ),
        )
        val send = {
            try {
                pushNotificationService.sendToUser(calleeId, callerName, body, mapOf("callId" to callId))
            } catch (e: Exception) {
                log.warn("Could not send incoming-call push for call {}", callId, e)
            }
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
    }

    @Transactional
    fun initiateCall(callerId: String, conversationId: String, callType: CallType): CallSession {
        rateLimiter.checkLimit("call:initiate:$callerId", limit = 10, window = Duration.ofMinutes(1))
        val conversation = conversationRepository.findById(conversationId).orElseThrow { CallConversationNotFoundException("Conversation not found") }
        // Real 404 (not 403) for a non-participant -- same "don't reveal a resource
        // exists to someone who shouldn't see it" discipline MessagingService
        // .requireParticipant already establishes.
        if (conversation.participantAId != callerId && conversation.participantBId != callerId) {
            throw CallConversationNotFoundException("Conversation not found")
        }
        val calleeId = if (conversation.participantAId == callerId) conversation.participantBId else conversation.participantAId
        val call = callSessionRepository.save(
            CallSession(id = "call_session_${UUID.randomUUID()}", conversationId = conversationId, callerId = callerId, calleeId = calleeId, callType = callType),
        )
        realtimeMessagePublisher.publishCallRing(calleeId, call.id, callerId, callType)
        notifyIncomingCall(calleeId, callerId, call.id, callType)
        return call
    }

    @Transactional
    fun answerCall(userId: String, callId: String): CallSession {
        val call = requireParticipant(userId, callId)
        if (call.endedAt != null) throw CallAlreadyEndedException("This call has already ended")
        call.answeredAt = Instant.now()
        return callSessionRepository.save(call)
    }

    @Transactional
    fun endCall(userId: String, callId: String, reason: CallEndReason): CallSession {
        val call = requireParticipant(userId, callId)
        if (call.endedAt != null) return call
        call.endedAt = Instant.now()
        call.endReason = reason
        val saved = callSessionRepository.save(call)
        val otherUserId = if (call.callerId == userId) call.calleeId else call.callerId
        realtimeMessagePublisher.publishCallEnded(otherUserId, callId, reason.name)
        return saved
    }

    fun getHistory(userId: String, pageable: Pageable): Page<CallSession> = callSessionRepository.findByParticipant(userId, pageable)

    /**
     * Real state transition for a call whose answer/decline/hangup signal arrived over
     * an already-live WebSocket -- see `MessagingWebSocketHandler.handleCallSignal`'s
     * own doc comment. `answerCall`/`endCall` above are real, but only ever reachable
     * via `CallController`'s own separate REST path (used before either side is
     * connected, e.g. cancelling before the callee ever answers); the far more common
     * "both sides already have a live socket, one hangs up" path only ever relayed the
     * raw SDP frame and never touched this real persisted state at all -- a genuine gap
     * found live in this already-shipped, already-tested code (2026-09-13). `call_end`'s
     * real reason is inferred from the call's own already-persisted `answeredAt`
     * (COMPLETED if it was ever answered, CANCELLED if the caller gave up first) rather
     * than trusted from the client -- the raw signaling frame doesn't even carry a
     * reason field, and this backend never trusts a client-asserted domain decision it
     * can derive itself. `call_decline` is unambiguous on its own. Deliberately its own
     * self-contained method (not delegating to `answerCall`/`endCall` via a same-class
     * call) to avoid this codebase's own documented self-invocation `@Transactional`
     * pitfall -- see `project_itunda_bills_autopay_transaction_bug`.
     */
    @Transactional
    fun recordSignalState(userId: String, callId: String, type: String) {
        val call = requireParticipant(userId, callId)
        if (call.endedAt != null) return
        when (type) {
            "call_answer" -> {
                call.answeredAt = Instant.now()
                callSessionRepository.save(call)
            }
            "call_decline" -> endCallState(call, userId, CallEndReason.DECLINED)
            "call_end" -> endCallState(call, userId, if (call.answeredAt != null) CallEndReason.COMPLETED else CallEndReason.CANCELLED)
        }
    }

    private fun endCallState(call: CallSession, userId: String, reason: CallEndReason) {
        call.endedAt = Instant.now()
        call.endReason = reason
        callSessionRepository.save(call)
        val otherUserId = if (call.callerId == userId) call.calleeId else call.callerId
        realtimeMessagePublisher.publishCallEnded(otherUserId, call.id, reason.name)
    }

    /** Real IDOR check for `MessagingWebSocketHandler`'s own inline signaling relay
     * -- never trust a client-asserted callId/recipient. Returns the real other
     * participant to relay to, or throws if the caller isn't a real participant of a
     * real, still-active call. */
    fun verifyActiveParticipant(userId: String, callId: String): String {
        val call = requireParticipant(userId, callId)
        if (call.endedAt != null) throw CallAlreadyEndedException("This call has already ended")
        return if (call.callerId == userId) call.calleeId else call.callerId
    }

    private fun requireParticipant(userId: String, callId: String): CallSession {
        val call = callSessionRepository.findById(callId).orElseThrow { CallNotFoundException("Call not found") }
        if (call.callerId != userId && call.calleeId != userId) throw CallNotParticipantException("Call not found")
        return call
    }

    /** Real ephemeral TURN credentials, minted per coturn's own real-time REST API
     * convention (username = "<expiryEpochSeconds>:<userId>", password =
     * base64(HMAC-SHA1(secret, username))) -- never a static long-lived secret
     * shipped to a client. */
    fun getTurnCredentials(userId: String): TurnCredentials {
        val expiresAt = Instant.now().plus(TURN_CREDENTIAL_TTL).epochSecond
        val username = "$expiresAt:$userId"
        val mac = javax.crypto.Mac.getInstance("HmacSHA1")
        mac.init(javax.crypto.spec.SecretKeySpec(turnSecret.toByteArray(), "HmacSHA1"))
        val password = java.util.Base64.getEncoder().encodeToString(mac.doFinal(username.toByteArray()))
        return TurnCredentials(username = username, password = password, ttlSeconds = TURN_CREDENTIAL_TTL.seconds)
    }
}
