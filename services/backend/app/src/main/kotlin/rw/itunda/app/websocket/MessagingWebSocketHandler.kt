package rw.itunda.app.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.GroupMessage
import rw.itunda.core.domain.Message
import rw.itunda.core.realtime.ReactionGroup
import rw.itunda.core.realtime.RealtimeMessagePublisher
import rw.itunda.core.repository.ConversationRepository
import rw.itunda.core.repository.GroupConversationMemberRepository
import java.time.Duration
import java.time.Instant
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
class MessagingWebSocketHandler(
    private val objectMapper: ObjectMapper,
    private val conversationRepository: ConversationRepository,
    private val groupConversationMemberRepository: GroupConversationMemberRepository,
    private val rateLimiter: RateLimiter,
) : TextWebSocketHandler(), RealtimeMessagePublisher {
    private val log = LoggerFactory.getLogger(MessagingWebSocketHandler::class.java)
    private val sessionsByUserId = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val userId = session.attributes[WS_USER_ID_ATTR] as? String
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE)
            return
        }
        val sessions = sessionsByUserId.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }
        val wasOffline = sessions.isEmpty()
        sessions.add(session)
        // Real transition-only push (2026-07-19): only the FIRST session for this user
        // fires "online" -- a second open tab/device shouldn't re-announce.
        if (wasOffline) publishPresenceChange(userId, online = true)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val userId = session.attributes[WS_USER_ID_ATTR] as? String ?: return
        val sessions = sessionsByUserId[userId] ?: return
        sessions.remove(session)
        // Real transition-only push: only the LAST session closing fires "offline".
        if (sessions.isEmpty()) publishPresenceChange(userId, online = false)
    }

    // Real typing indicators (2026-07-19) -- the first inbound (client-to-server) frame
    // this socket ever needed to actually read; every other push so far has been purely
    // server-to-client. A real, ephemeral (never persisted) ping relayed to the real
    // other participant(s), with the same IDOR discipline as every real endpoint in this
    // backend (a non-participant's typing frame is silently dropped, not relayed) and a
    // real per-(user, conversation) rate limit so a malicious/buggy client can't spam
    // relays -- typing indicators are best-effort, so a rate-limited or malformed frame
    // is silently dropped rather than erroring the socket.
    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val userId = session.attributes[WS_USER_ID_ATTR] as? String ?: return
        try {
            val json = objectMapper.readTree(message.payload)
            if (json.get("type")?.asText() != "typing") return
            json.get("conversationId")?.asText()?.let { conversationId ->
                rateLimiter.checkLimit("typing:$userId:$conversationId", limit = 1, window = Duration.ofSeconds(2))
                val conversation = conversationRepository.findById(conversationId).orElse(null) ?: return
                val otherId = when (userId) {
                    conversation.participantAId -> conversation.participantBId
                    conversation.participantBId -> conversation.participantAId
                    else -> return
                }
                sendToUser(otherId, objectMapper.writeValueAsString(mapOf("type" to "typing", "conversationId" to conversationId, "userId" to userId)))
            }
            json.get("groupConversationId")?.asText()?.let { groupId ->
                rateLimiter.checkLimit("typing:$userId:$groupId", limit = 1, window = Duration.ofSeconds(2))
                val members = groupConversationMemberRepository.findByGroupConversationId(groupId)
                if (members.none { it.userId == userId }) return
                val payload = objectMapper.writeValueAsString(mapOf("type" to "typing", "groupConversationId" to groupId, "userId" to userId))
                members.filter { it.userId != userId }.forEach { sendToUser(it.userId, payload) }
            }
        } catch (e: Exception) {
            // Real, non-critical -- a malformed/unexpected/rate-limited inbound frame
            // shouldn't kill the socket; typing indicators are best-effort.
        }
    }

    override fun isOnline(userId: String): Boolean = !sessionsByUserId[userId].isNullOrEmpty()

    override fun publishPresenceChange(userId: String, online: Boolean) {
        val payload = objectMapper.writeValueAsString(mapOf("type" to "presence", "userId" to userId, "online" to online))
        conversationRepository.findPartnerUserIds(userId).forEach { partnerId -> sendToUser(partnerId, payload) }
    }

    override fun publishNewMessage(conversationId: String, recipientUserId: String, message: Message) {
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
        sendToUser(recipientUserId, payload)
    }

    override fun publishNewGroupMessage(groupId: String, recipientUserIds: List<String>, message: GroupMessage) {
        val payload = objectMapper.writeValueAsString(
            mapOf(
                "type" to "group_message",
                "groupConversationId" to groupId,
                "message" to mapOf(
                    "id" to message.id,
                    "groupConversationId" to message.groupConversationId,
                    "senderId" to message.senderId,
                    "body" to message.body,
                    "sentAt" to message.sentAt.toString(),
                ),
            ),
        )
        recipientUserIds.forEach { sendToUser(it, payload) }
    }

    // Real reaction push (2026-07-19) -- fired after a real toggle is durably
    // persisted, to the real other 1:1 participant.
    override fun publishReactionChange(conversationId: String, recipientUserId: String, messageId: String, reactions: List<ReactionGroup>) {
        val payload = objectMapper.writeValueAsString(
            mapOf("type" to "reaction", "conversationId" to conversationId, "messageId" to messageId, "reactions" to reactions),
        )
        sendToUser(recipientUserId, payload)
    }

    override fun publishGroupReactionChange(groupId: String, recipientUserIds: List<String>, groupMessageId: String, reactions: List<ReactionGroup>) {
        val payload = objectMapper.writeValueAsString(
            mapOf("type" to "reaction", "groupConversationId" to groupId, "messageId" to groupMessageId, "reactions" to reactions),
        )
        recipientUserIds.forEach { sendToUser(it, payload) }
    }

    override fun publishGroupReadReceiptChange(groupId: String, recipientUserIds: List<String>, readByUserId: String, lastReadAt: Instant) {
        val payload = objectMapper.writeValueAsString(
            mapOf("type" to "group_read_receipt", "groupConversationId" to groupId, "readByUserId" to readByUserId, "lastReadAt" to lastReadAt.toString()),
        )
        recipientUserIds.forEach { sendToUser(it, payload) }
    }

    private fun sendToUser(userId: String, payload: String) {
        val sessions = sessionsByUserId[userId]
        if (sessions.isNullOrEmpty()) return
        val text = TextMessage(payload)
        for (session in sessions.toList()) {
            try {
                if (session.isOpen) session.sendMessage(text)
            } catch (e: Exception) {
                log.warn("Failed to push to session {} for user {}: {}", session.id, userId, e.message)
                sessions.remove(session)
            }
        }
    }
}
