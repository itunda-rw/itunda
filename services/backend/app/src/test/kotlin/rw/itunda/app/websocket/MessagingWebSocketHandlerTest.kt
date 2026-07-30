package rw.itunda.app.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import rw.itunda.auth.RateLimiter
import rw.itunda.core.repository.ConversationRepository
import rw.itunda.core.repository.GroupConversationMemberRepository
import java.time.Instant
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession
import io.micrometer.core.instrument.simple.SimpleMeterRegistry

class MessagingWebSocketHandlerTest {
    private val conversationRepository = mock(ConversationRepository::class.java)
    private val handler = MessagingWebSocketHandler(
        ObjectMapper(),
        conversationRepository,
        mock(GroupConversationMemberRepository::class.java),
        mock(RateLimiter::class.java),
        SimpleMeterRegistry(),
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
            1,
        )
        `when`(conversationRepository.findPartnerUserIds("user_1")).thenReturn(emptyList())

        limitedHandler.afterConnectionEstablished(first)
        limitedHandler.afterConnectionEstablished(second)

        verify(second).close(CloseStatus.POLICY_VIOLATION)
    }

    private fun sessionWithExpiry(expiresAt: Instant): WebSocketSession = mock(WebSocketSession::class.java).also { session ->
        `when`(session.attributes).thenReturn(
            mutableMapOf<String, Any>(
                WS_USER_ID_ATTR to "user_1",
                WS_TOKEN_ID_ATTR to "token_1",
                WS_TOKEN_EXPIRES_AT_ATTR to expiresAt,
            ),
        )
        `when`(session.isOpen).thenReturn(true)
        `when`(session.id).thenReturn("socket_1")
    }
}
