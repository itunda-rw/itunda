package rw.itunda.app.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
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
internal const val WS_TOKEN_ID_ATTR = "tokenId"
internal const val WS_TOKEN_EXPIRES_AT_ATTR = "tokenExpiresAt"

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
    private val meterRegistry: MeterRegistry,
    @Value("\${itunda.websocket.max-sessions-per-user:5}")
    private val maxSessionsPerUser: Int,
) : TextWebSocketHandler(), RealtimeMessagePublisher {
    private val log = LoggerFactory.getLogger(MessagingWebSocketHandler::class.java)
    private val sessionsByUserId = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()

    init {
        require(maxSessionsPerUser in 1..50) {
            "itunda.websocket.max-sessions-per-user must be between 1 and 50"
        }
        Gauge.builder("itunda.messaging.websocket.sessions", sessionsByUserId) { sessionsByUser ->
            sessionsByUser.values.sumOf { it.size }.toDouble()
        }
            .description("Open Itunda messaging WebSocket sessions on this backend instance")
            .register(meterRegistry)
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val userId = session.attributes[WS_USER_ID_ATTR] as? String
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE)
            return
        }
        // Use the map's per-key atomic operation rather than isEmpty()+add(). A
        // tab opening while another closes must not produce a false offline/online
        // pair, and empty sets must not accumulate forever for every historical
        // user ID that has connected.
        var wasOffline = false
        var accepted = false
        sessionsByUserId.compute(userId) { _, existingSessions ->
            val sessions = existingSessions ?: ConcurrentHashMap.newKeySet()
            if (sessions.size >= maxSessionsPerUser) return@compute sessions
            wasOffline = sessions.isEmpty()
            sessions.add(session)
            accepted = true
            sessions
        }
        if (!accepted) {
            session.close(CloseStatus.POLICY_VIOLATION)
            meterRegistry.counter("itunda.messaging.websocket.session_rejections", "reason", "per_user_limit").increment()
            return
        }
        // Real transition-only push (2026-07-19): only the FIRST session for this user
        // fires "online" -- a second open tab/device shouldn't re-announce.
        if (wasOffline) publishPresenceChange(userId, online = true)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val userId = session.attributes[WS_USER_ID_ATTR] as? String ?: return
        var wentOffline = false
        sessionsByUserId.computeIfPresent(userId) { _, sessions ->
            sessions.remove(session)
            if (sessions.isEmpty()) {
                wentOffline = true
                null
            } else {
                sessions
            }
        }
        // Real transition-only push: only the LAST session closing fires "offline".
        if (wentOffline) publishPresenceChange(userId, online = false)
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
            val conversationId = typingTargetId(json, "conversationId")
            val groupId = typingTargetId(json, "groupConversationId")
            // One typing frame means one target. Reject dual/missing/oversized IDs
            // before they reach the rate limiter or any repository lookup.
            if ((conversationId == null) == (groupId == null)) return
            conversationId?.let { targetConversationId ->
                rateLimiter.checkLimit("typing:$userId:$targetConversationId", limit = 1, window = Duration.ofSeconds(2))
                val conversation = conversationRepository.findById(targetConversationId).orElse(null) ?: return
                val otherId = when (userId) {
                    conversation.participantAId -> conversation.participantBId
                    conversation.participantBId -> conversation.participantAId
                    else -> return
                }
                sendToUser(otherId, objectMapper.writeValueAsString(mapOf("type" to "typing", "conversationId" to targetConversationId, "userId" to userId)))
            }
            groupId?.let { targetGroupId ->
                rateLimiter.checkLimit("typing:$userId:$targetGroupId", limit = 1, window = Duration.ofSeconds(2))
                val members = groupConversationMemberRepository.findByGroupConversationId(targetGroupId)
                if (members.none { it.userId == userId }) return
                val payload = objectMapper.writeValueAsString(mapOf("type" to "typing", "groupConversationId" to targetGroupId, "userId" to userId))
                members.filter { it.userId != userId }.forEach { sendToUser(it.userId, payload) }
            }
        } catch (e: Exception) {
            // Real, non-critical -- a malformed/unexpected/rate-limited inbound frame
            // shouldn't kill the socket; typing indicators are best-effort.
        }
    }

    private fun typingTargetId(json: JsonNode, field: String): String? =
        json.get(field)
            ?.takeIf { it.isTextual }
            ?.asText()
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= 128 }

    override fun isOnline(userId: String): Boolean = !sessionsByUserId[userId].isNullOrEmpty()

    override fun closeSessionsForToken(userId: String, tokenId: String) {
        // Logout revokes one access token, not every device a user owns. Session close
        // callbacks perform normal registry cleanup and publish an offline transition
        // only if this was the user's final open socket.
        sessionsByUserId[userId]
            ?.filter { it.attributes[WS_TOKEN_ID_ATTR] == tokenId }
            ?.forEach { session ->
                try {
                    if (session.isOpen) {
                        session.close(CloseStatus.POLICY_VIOLATION)
                        meterRegistry.counter("itunda.messaging.websocket.session_closures", "reason", "token_revoked").increment()
                    }
                } catch (e: Exception) {
                    log.warn("Failed to close revoked session {} for user {}: {}", session.id, userId, e.message)
                }
            }
    }

    /**
     * A WebSocket remains authenticated after its initial handshake, so it must not
     * outlive the access token that established it. Logout closes matching sessions
     * immediately; this local sweep enforces ordinary expiry without calling Redis for
     * every open socket on the application's shared scheduler thread.
     */
    @Scheduled(fixedDelayString = "\${itunda.websocket.session-validation-interval-ms:1800000}")
    fun closeExpiredSessions() {
        val now = Instant.now()
        sessionsByUserId.values
            .flatMap { it.toList() }
            .filter { session ->
                val expiresAt = session.attributes[WS_TOKEN_EXPIRES_AT_ATTR] as? Instant
                expiresAt == null || !expiresAt.isAfter(now)
            }
            .forEach { session ->
                try {
                    if (session.isOpen) {
                        session.close(CloseStatus.POLICY_VIOLATION)
                        meterRegistry.counter("itunda.messaging.websocket.session_closures", "reason", "token_expired").increment()
                    }
                } catch (e: Exception) {
                    log.warn("Failed to close expired session {}: {}", session.id, e.message)
                }
            }
    }

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
                    // Real, pre-existing gap fixed 2026-07-26 -- see
                    // MessagingController.getMessages's own identical fix. A live
                    // recipient's real-time push never carried these real fields
                    // either, only the sender's own immediate POST response did.
                    "replyToMessageId" to message.replyToMessageId,
                    "imageUrl" to message.imageUrl,
                    "emoticonId" to message.emoticonId,
                    "forwardedFromMessageId" to message.forwardedFromMessageId,
                    "forwardedFromType" to message.forwardedFromType,
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
                    // Real, pre-existing gap fixed 2026-07-26 -- see this class's own
                    // publishNewMessage fix for the full account.
                    "replyToMessageId" to message.replyToMessageId,
                    "imageUrl" to message.imageUrl,
                    "emoticonId" to message.emoticonId,
                    "forwardedFromMessageId" to message.forwardedFromMessageId,
                    "forwardedFromType" to message.forwardedFromType,
                    "mentionedUserIds" to message.mentionedUserIds,
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
