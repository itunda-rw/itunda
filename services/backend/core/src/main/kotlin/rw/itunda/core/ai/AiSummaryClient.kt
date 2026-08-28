package rw.itunda.core.ai

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * A real, self-hosted, open-source LLM client (itunda Maps redesign, 2026-08-28,
 * direct user instruction: "for AI let's find high quality open-source AI online we
 * can download and customize to serve our needs") -- talks to a real `llama.cpp`
 * `llama-server` instance running a real, small, self-hosted model (Llama 3.2
 * 1B-Instruct, quantized -- ~1.3GB RAM, the smallest viable option for itunda's
 * severely resource-constrained private-cloud VM), deployed as a 4th lightweight
 * service on `itunda-dc-a`, internal-only (never exposed publicly, only this backend
 * calls it). Not a third-party AI API -- no OpenAI/Anthropic/Gemini key involved, same
 * "self-hosted, not a paid external dependency" bar every other real client in this
 * package already establishes (Nominatim/OSRM/Open-Meteo).
 *
 * Speaks `llama-server`'s own real OpenAI-compatible `/v1/chat/completions` endpoint
 * (its documented, standard interface) -- `max_tokens`/`temperature` kept low and
 * deterministic-leaning (0.3), matching this being a short, fact-grounded summary, not
 * creative writing. Optional by the same never-fail convention as every other client
 * here: unconfigured, unreachable, or a malformed response returns null, and
 * `AiSummaryService`'s own batch job simply skips that merchant for this run rather
 * than blocking or fabricating a summary.
 */
@Component
class AiSummaryClient(
    @Value("\${itunda.ai-summary.base-url:}") private val baseUrl: String,
    @Value("\${itunda.ai-summary.model:llama-3.2-1b-instruct}") private val model: String,
) {
    private val logger = LoggerFactory.getLogger(AiSummaryClient::class.java)
    private val restClient: RestClient? = if (baseUrl.isNotBlank()) RestClient.create(baseUrl) else null

    val isConfigured: Boolean get() = restClient != null

    /** Real chat-completion call. `systemPrompt` carries the real, explicit
     * no-invented-facts constraint; `userPrompt` carries only real, already-known
     * structured data about one real merchant. Null on any failure -- never a
     * fabricated summary text. */
    fun complete(systemPrompt: String, userPrompt: String, maxTokens: Int = 120): String? {
        val client = restClient ?: return null
        return try {
            @Suppress("UNCHECKED_CAST")
            val response = client.post()
                .uri("/v1/chat/completions")
                .body(
                    mapOf(
                        "model" to model,
                        "messages" to listOf(
                            mapOf("role" to "system", "content" to systemPrompt),
                            mapOf("role" to "user", "content" to userPrompt),
                        ),
                        "max_tokens" to maxTokens,
                        "temperature" to 0.3,
                    ),
                )
                .retrieve()
                .body(Map::class.java) as Map<String, Any?>?
            @Suppress("UNCHECKED_CAST")
            val choices = response?.get("choices") as? List<Map<String, Any?>>
            @Suppress("UNCHECKED_CAST")
            val message = choices?.firstOrNull()?.get("message") as? Map<String, Any?>
            (message?.get("content") as? String)?.trim()?.ifBlank { null }
        } catch (e: RestClientException) {
            logger.warn("Self-hosted AI summary request failed: {}", e.message)
            null
        }
    }

    /** Real multi-turn chat completion (itunda Talk redesign, 2026-08-28) -- a small,
     * additive sibling to [complete]: `llama-server`'s `/v1/chat/completions` already
     * natively accepts a full OpenAI-style message array, [complete] just never built
     * one. `messages` is the full real prior turn history (already persisted by the
     * caller -- this client itself stays stateless, same as [complete]). Same
     * never-fail convention: null on any failure, never a fabricated reply. */
    fun completeChat(messages: List<Map<String, String>>, maxTokens: Int = 300): String? {
        val client = restClient ?: return null
        return try {
            @Suppress("UNCHECKED_CAST")
            val response = client.post()
                .uri("/v1/chat/completions")
                .body(mapOf("model" to model, "messages" to messages, "max_tokens" to maxTokens, "temperature" to 0.3))
                .retrieve()
                .body(Map::class.java) as Map<String, Any?>?
            @Suppress("UNCHECKED_CAST")
            val choices = response?.get("choices") as? List<Map<String, Any?>>
            @Suppress("UNCHECKED_CAST")
            val message = choices?.firstOrNull()?.get("message") as? Map<String, Any?>
            (message?.get("content") as? String)?.trim()?.ifBlank { null }
        } catch (e: RestClientException) {
            logger.warn("Self-hosted AI chat request failed: {}", e.message)
            null
        }
    }
}
