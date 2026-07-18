package rw.itunda.app.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import rw.itunda.core.domain.Message
import rw.itunda.core.realtime.RealtimeMessagePublisher
import java.util.concurrent.ConcurrentHashMap

internal const val WS_USER_ID_ATTR = "userId"

/**
 * Real live-transport for messaging (2026-07-18) -- see `RealtimeMessagePublisher`'s
 * own doc comment (`:core`) for the full "why this shape, not Kafka" account. A plain
 * `TextWebSocketHandler` (not STOMP/SockJS -- this backend has exactly one real-time
 * use case so far; a full message-broker abstraction would be solving a problem that
 * doesn't exist yet), registered at `/ws/messaging` by `MessagingWebSocketConfig`.
 *
 * Authenticated at handshake time (`MessagingWebSocketConfig`'s interceptor), not per
 * message -- a WebSocket session is a single long-lived authenticated connection, the
 * same trust model any real chat app's socket layer uses. A user can hold more than
 * one open session at once (multiple tabs/devices); every open session for a userId
 * receives the push, not just the first one that connected.
 */
@Component
class MessagingWebSocketHandler(private val objectMapper: ObjectMapper) : TextWebSocketHandler(), RealtimeMessagePublisher {
    private val log = LoggerFactory.getLogger(MessagingWebSocketHandler::class.java)
    private val sessionsByUserId = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val userId = session.attributes[WS_USER_ID_ATTR] as? String
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE)
            return
        }
        sessionsByUserId.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }.add(session)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val userId = session.attributes[WS_USER_ID_ATTR] as? String ?: return
        sessionsByUserId[userId]?.remove(session)
    }

    override fun publishNewMessage(conversationId: String, recipientUserId: String, message: Message) {
        val sessions = sessionsByUserId[recipientUserId]
        if (sessions.isNullOrEmpty()) return

        val payload = objectMapper.writeValueAsString(
            mapOf(
                "type" to "message",
                "conversationId" to conversationId,
                "message" to mapOf(
                    "id" to message.id,
                    "conversationId" to message.conversationId,
                    "senderId" to message.senderId,
                    "body" to message.body,
                    "sentAt" to message.sentAt.toString(),
                ),
            ),
        )
        val text = TextMessage(payload)
        for (session in sessions.toList()) {
            try {
                if (session.isOpen) session.sendMessage(text)
            } catch (e: Exception) {
                log.warn("Failed to push to session {} for user {}: {}", session.id, recipientUserId, e.message)
                sessions.remove(session)
            }
        }
    }
}
