package rw.itunda.calling

import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.CallEndReason
import rw.itunda.core.domain.CallSession
import rw.itunda.core.domain.CallType
import rw.itunda.core.realtime.RealtimeMessagePublisher
import rw.itunda.core.repository.CallSessionRepository
import rw.itunda.messaging.MessagingService

class CallNotFoundException(message: String) : RuntimeException(message)
class CallNotParticipantException(message: String) : RuntimeException(message)
class CallAlreadyEndedException(message: String) : RuntimeException(message)

data class TurnCredentials(val username: String, val password: String, val ttlSeconds: Long)

/**
 * Real 1:1 voice/video calling (itunda Talk redesign, 2026-08-28) -- see
 * `CallSession`'s own doc comment. A call is always scoped to a real, existing 1:1
 * `Conversation` between exactly two real participants -- `MessagingService
 * .getConversationForParticipant` is reused for that IDOR check, the same real
 * public wrapper `GiftService`/`SplitBillService` already establish the precedent
 * for. Real SDP offer/answer/ICE-candidate signaling is relayed entirely inside
 * `MessagingWebSocketHandler.handleTextMessage` (see `RealtimeMessagePublisher`'s
 * own doc comment on why) -- this service owns only the real, persisted call-state
 * transitions (ring/answer/end) a call-log tab needs, plus [verifyActiveParticipant],
 * the one real hook that WS handler calls into for its own IDOR check before
 * relaying a signaling frame.
 */
@Service
class CallService(
    private val callSessionRepository: CallSessionRepository,
    private val messagingService: MessagingService,
    private val realtimeMessagePublisher: RealtimeMessagePublisher,
    private val rateLimiter: RateLimiter,
    @Value("\${itunda.turn.secret:}") private val turnSecret: String,
) {
    companion object {
        private val TURN_CREDENTIAL_TTL = Duration.ofHours(6)
    }

    @Transactional
    fun initiateCall(callerId: String, conversationId: String, callType: CallType): CallSession {
        rateLimiter.checkLimit("call:initiate:$callerId", limit = 10, window = Duration.ofMinutes(1))
        val conversation = messagingService.getConversationForParticipant(callerId, conversationId)
        val calleeId = if (conversation.participantAId == callerId) conversation.participantBId else conversation.participantAId
        val call = callSessionRepository.save(
            CallSession(id = "call_session_${UUID.randomUUID()}", conversationId = conversationId, callerId = callerId, calleeId = calleeId, callType = callType),
        )
        realtimeMessagePublisher.publishCallRing(calleeId, call.id, callerId, callType)
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
