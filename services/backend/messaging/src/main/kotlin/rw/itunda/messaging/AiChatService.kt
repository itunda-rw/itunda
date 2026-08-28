package rw.itunda.messaging

import java.time.Duration
import java.util.UUID
import java.util.concurrent.Semaphore
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import rw.itunda.auth.RateLimiter
import rw.itunda.core.ai.AiSummaryClient
import rw.itunda.core.domain.AiChatMessage
import rw.itunda.core.repository.AiChatMessageRepository

class AiChatBusyException(message: String) : RuntimeException(message)

/**
 * Real AI chatbot channel (itunda Talk redesign, 2026-08-28, matching the "ChatGPT for
 * Kakao" reference) -- reuses the exact same self-hosted `llama-server`/
 * `AiSummaryClient` this session's Maps/Hood passes already deployed, via the new
 * `completeChat` sibling method. No new AI infrastructure.
 *
 * This is the **first-ever live/on-demand** caller against that shared model
 * instance -- every existing consumer (Maps place summaries, Hood meetup summaries)
 * is a low-frequency scheduled batch job, explicitly reasoned in their own doc
 * comments as batch-not-live specifically because itunda's private cloud has already
 * hit real, severe overload incidents under ordinary load. Real, mandatory
 * guardrails, not optional polish:
 * - A global single-flight guard ([Semaphore] of 1): one shared model instance, no
 *   queue, no autoscaling -- a second concurrent request gets a real, honest
 *   [AiChatBusyException] rather than blocking indefinitely or corrupting the first.
 * - A per-user cooldown via the existing [RateLimiter] (the same real Redis-backed
 *   primitive `MessagingWebSocketHandler` already uses for typing) -- no new
 *   rate-limiting mechanism invented.
 * - A firm, conservative `max_tokens` cap, justified against the model's own
 *   documented resource fragility (a 1.3GB-RAM, CPU-only single instance).
 */
@Service
class AiChatService(
    private val aiChatMessageRepository: AiChatMessageRepository,
    private val aiSummaryClient: AiSummaryClient,
    private val rateLimiter: RateLimiter,
) {
    companion object {
        // Real, right-sized bound (2026-08-28, adjusted after a live deploy incident
        // -- see AiSummaryClient's own doc comment on the real read-timeout fix this
        // pairs with): this node's own real CPU-only inference throughput for a 1B
        // model is genuinely slow, and 300 tokens risked outrunning even a generous
        // read timeout. 150 keeps a real completion's total generation time bounded
        // well under that timeout under normal contention.
        private const val MAX_TOKENS = 150
        private const val CONTEXT_TURNS = 10
        private val COOLDOWN = Duration.ofSeconds(10)
        private const val SYSTEM_PROMPT =
            "You are itunda's assistant inside itunda Talk. Answer briefly and honestly. " +
                "If you don't know something, say so -- never invent a fact about itunda's real services."
    }

    // Real single-flight guard -- one shared model instance, no queue. A held permit
    // means a completion is already in flight for SOME user; a second concurrent
    // caller gets the honest busy response rather than waiting or corrupting the
    // in-flight request.
    private val modelSemaphore = Semaphore(1)

    fun sendMessage(userId: String, text: String): Pair<AiChatMessage, AiChatMessage> {
        rateLimiter.checkLimit("ai-chat:$userId", limit = 1, window = COOLDOWN)

        val userMessage = aiChatMessageRepository.save(
            AiChatMessage(id = "ai_chat_message_${UUID.randomUUID()}", userId = userId, role = "user", content = text),
        )

        if (!modelSemaphore.tryAcquire()) {
            throw AiChatBusyException("itunda AI is busy right now, try again shortly")
        }
        try {
            val history = aiChatMessageRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, CONTEXT_TURNS))
                .content.reversed()
            val messages = listOf(mapOf("role" to "system", "content" to SYSTEM_PROMPT)) +
                history.map { mapOf("role" to it.role, "content" to it.content) }
            val reply = aiSummaryClient.completeChat(messages, MAX_TOKENS)
                ?: "itunda AI couldn't generate a real response just now -- please try again."
            val assistantMessage = aiChatMessageRepository.save(
                AiChatMessage(id = "ai_chat_message_${UUID.randomUUID()}", userId = userId, role = "assistant", content = reply),
            )
            return userMessage to assistantMessage
        } finally {
            modelSemaphore.release()
        }
    }

    fun getHistory(userId: String, pageable: Pageable): Page<AiChatMessage> =
        aiChatMessageRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
}
