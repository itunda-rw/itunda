package rw.itunda.app.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import rw.itunda.auth.RateLimiter
import rw.itunda.calling.CallNotParticipantException
import rw.itunda.calling.CallService
import rw.itunda.core.repository.ConversationRepository
import rw.itunda.core.repository.GroupConversationMemberRepository
import java.time.Instant
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import io.micrometer.core.instrument.simple.SimpleMeterRegistry

class MessagingWebSocketHandlerTest {
    private val conversationRepository = mock(ConversationRepository::class.java)
    private val callService = mock(CallService::class.java)
    private val handler = MessagingWebSocketHandler(
        ObjectMapper(),
        conversationRepository,
        mock(GroupConversationMemberRepository::class.java),
        mock(RateLimiter::class.java),
        SimpleMeterRegistry(),
        callService,
        5,
    )

    @Test
    fun `expiry sweep closes an expired authenticated session`() {
        val session = sessionWithExpiry(Instant.now().minusSeconds(1))
        `when`(conversationRepository.findPartnerUserIds("user_1")).thenReturn(emptyList())

        handler.afterConnectionEstablished(session)
        handler.closeExpiredSessions()

        verify(session).close(CloseStatus.POLICY_VIOLATION)
    }

    @Test
    fun `expiry sweep leaves a valid session open`() {
        val session = sessionWithExpiry(Instant.now().plusSeconds(3600))
        `when`(conversationRepository.findPartnerUserIds("user_1")).thenReturn(emptyList())

        handler.afterConnectionEstablished(session)
        handler.closeExpiredSessions()

        verify(session, never()).close(CloseStatus.POLICY_VIOLATION)
    }

    @Test
    fun `connection limit rejects a session over the per user cap`() {
        val first = sessionWithExpiry(Instant.now().plusSeconds(3600))
        val second = sessionWithExpiry(Instant.now().plusSeconds(3600))
        val limitedHandler = MessagingWebSocketHandler(
            ObjectMapper(),
            conversationRepository,
            mock(GroupConversationMemberRepository::class.java),
            mock(RateLimiter::class.java),
            SimpleMeterRegistry(),
            mock(CallService::class.java),
            1,
        )
        `when`(conversationRepository.findPartnerUserIds("user_1")).thenReturn(emptyList())

        limitedHandler.afterConnectionEstablished(first)
        limitedHandler.afterConnectionEstablished(second)

        verify(second).close(CloseStatus.POLICY_VIOLATION)
    }

    // Real SDP offer relay for 1:1 calling (2026-08-28) -- see
    // MessagingWebSocketHandler.handleCallSignal's own doc comment.
    @Test
    fun `a real call_offer frame from a verified active participant is relayed to the real other participant`() {
        val callerSession = sessionWithExpiry(Instant.now().plusSeconds(3600), userId = "user_1")
        val calleeSession = sessionWithExpiry(Instant.now().plusSeconds(3600), userId = "user_2")
        `when`(conversationRepository.findPartnerUserIds("user_1")).thenReturn(emptyList())
        `when`(conversationRepository.findPartnerUserIds("user_2")).thenReturn(emptyList())
        `when`(callService.verifyActiveParticipant("user_1", "call_1")).thenReturn("user_2")

        handler.afterConnectionEstablished(callerSession)
        handler.afterConnectionEstablished(calleeSession)
        handler.handleMessage(callerSession, TextMessage("""{"type":"call_offer","callId":"call_1","sdp":"real-sdp-payload"}"""))

        val captor = ArgumentCaptor.forClass(TextMessage::class.java)
        verify(calleeSession).sendMessage(captor.capture())
        val relayed = ObjectMapper().readTree(captor.value.payload)
        assertEquals("real-sdp-payload", relayed.get("sdp").asText())
        assertEquals("user_1", relayed.get("fromUserId").asText())
    }

    // Real gap found live (2026-09-13): answer/decline/end signals used to only relay
    // the raw frame -- CallService's own persisted CallSession state (answeredAt/
    // endedAt/endReason) never updated for a call ended over an already-live socket.
    @Test
    fun `a real call_answer frame records the real CallSession state and still relays`() {
        val callerSession = sessionWithExpiry(Instant.now().plusSeconds(3600), userId = "user_1")
        val calleeSession = sessionWithExpiry(Instant.now().plusSeconds(3600), userId = "user_2")
        `when`(conversationRepository.findPartnerUserIds("user_1")).thenReturn(emptyList())
        `when`(conversationRepository.findPartnerUserIds("user_2")).thenReturn(emptyList())
        `when`(callService.verifyActiveParticipant("user_1", "call_1")).thenReturn("user_2")

        handler.afterConnectionEstablished(callerSession)
        handler.afterConnectionEstablished(calleeSession)
        handler.handleMessage(callerSession, TextMessage("""{"type":"call_answer","callId":"call_1"}"""))

        verify(callService).recordSignalState("user_1", "call_1", "call_answer")
        verify(calleeSession).sendMessage(any(TextMessage::class.java))
    }

    @Test
    fun `a real call_end frame still relays even if recording the real state fails`() {
        val callerSession = sessionWithExpiry(Instant.now().plusSeconds(3600), userId = "user_1")
        val calleeSession = sessionWithExpiry(Instant.now().plusSeconds(3600), userId = "user_2")
        `when`(conversationRepository.findPartnerUserIds("user_1")).thenReturn(emptyList())
        `when`(conversationRepository.findPartnerUserIds("user_2")).thenReturn(emptyList())
        `when`(callService.verifyActiveParticipant("user_1", "call_1")).thenReturn("user_2")
        org.mockito.Mockito.doThrow(RuntimeException("racing hangup")).`when`(callService).recordSignalState("user_1", "call_1", "call_end")

        handler.afterConnectionEstablished(callerSession)
        handler.afterConnectionEstablished(calleeSession)
        handler.handleMessage(callerSession, TextMessage("""{"type":"call_end","callId":"call_1"}"""))

        verify(calleeSession).sendMessage(any(TextMessage::class.java))
    }

    @Test
    fun `a real call_offer frame from a non-participant is silently dropped, never relayed`() {
        val strangerSession = sessionWithExpiry(Instant.now().plusSeconds(3600), userId = "user_stranger")
        val calleeSession = sessionWithExpiry(Instant.now().plusSeconds(3600), userId = "user_2")
        `when`(conversationRepository.findPartnerUserIds("user_stranger")).thenReturn(emptyList())
        `when`(conversationRepository.findPartnerUserIds("user_2")).thenReturn(emptyList())
        `when`(callService.verifyActiveParticipant("user_stranger", "call_1")).thenThrow(CallNotParticipantException("Call not found"))

        handler.afterConnectionEstablished(strangerSession)
        handler.afterConnectionEstablished(calleeSession)
        handler.handleMessage(strangerSession, TextMessage("""{"type":"call_offer","callId":"call_1","sdp":"real-sdp-payload"}"""))

        verify(calleeSession, never()).sendMessage(any(TextMessage::class.java))
    }

    private fun sessionWithExpiry(expiresAt: Instant, userId: String = "user_1"): WebSocketSession = mock(WebSocketSession::class.java).also { session ->
        `when`(session.attributes).thenReturn(
            mutableMapOf<String, Any>(
                WS_USER_ID_ATTR to userId,
                WS_TOKEN_ID_ATTR to "token_1",
                WS_TOKEN_EXPIRES_AT_ATTR to expiresAt,
            ),
        )
        `when`(session.isOpen).thenReturn(true)
        `when`(session.id).thenReturn("socket_$userId")
    }
}
