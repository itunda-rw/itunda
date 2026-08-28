package rw.itunda.community

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.ai.AiSummaryClient
import rw.itunda.core.domain.CommunityPost
import rw.itunda.core.repository.CommunityPostRepository
import rw.itunda.core.repository.GroupConversationMemberRepository
import java.time.Duration
import java.time.Instant

/**
 * Real, self-hosted AI 모임 (meetup) summary batch generation -- reuses the exact
 * same self-hosted `llama-server`/`AiSummaryClient` this session's Maps work already
 * deployed, not a new AI dependency. See `AiSummaryClient`'s own doc comment for the
 * self-hosting account, and `eats.AiSummaryService`'s own doc comment for the batch/
 * scheduler shape this mirrors.
 *
 * **Real honesty lesson applied from the start, not fixed after the fact** -- this
 * session's Maps AI-summary pass found LIVE that a small self-hosted model invents
 * plausible-sounding detail when given too little real signal, even with an explicit
 * "never invent a fact" system-prompt instruction (see `eats.AiSummaryService
 * .generateSummaryFor`'s own "descriptive facts" gate, added after that finding). A
 * 모임 post always has real user-written body text (a post can't be created with an
 * empty body -- see `CommunityService.createPost`), so the failure mode here is a
 * THIN body rather than a MISSING one: gated on a real minimum length, not just
 * presence.
 */
@Service
class HoodAiSummaryService(
    private val postRepository: CommunityPostRepository,
    private val groupConversationMemberRepository: GroupConversationMemberRepository,
    private val aiSummaryClient: AiSummaryClient,
) {
    private val logger = LoggerFactory.getLogger(HoodAiSummaryService::class.java)

    companion object {
        private val STALE_AFTER = Duration.ofDays(14)
        private const val DEFAULT_BATCH_LIMIT = 20
        private const val MIN_BODY_LENGTH = 20

        private const val SYSTEM_PROMPT = "You write a single short, honest, one-sentence summary of a real neighborhood meetup group for a community app, in plain English, under 40 words. " +
            "Use ONLY the facts given to you below -- never state a member count, an activity, a schedule, or any other fact that wasn't explicitly given. " +
            "If few facts are given, write a shorter, more general sentence rather than inventing detail. Do not use markdown or quotation marks."
    }

    /** Real one-post summary generation -- only ever for a real meetup with a real,
     * substantive body. Returns null (and leaves the post's existing summary
     * untouched) if unconfigured, unreachable, too thin to summarize honestly, or the
     * model returns nothing usable. */
    fun generateSummaryFor(post: CommunityPost): String? {
        if (post.category != "meetup") return null
        val trimmedBody = post.body.trim()
        if (trimmedBody.length < MIN_BODY_LENGTH) return null // Not enough real signal to summarize honestly.

        val memberCount = post.groupConversationId?.let { groupId ->
            groupConversationMemberRepository.countMembersByGroupConversationIds(listOf(groupId)).firstOrNull()?.memberCount
        } ?: 0L

        val facts = buildList {
            add("Title: ${post.title}")
            add("Description: $trimmedBody")
            if (memberCount > 0) add("Real member count: $memberCount")
            if (post.commentCount > 0) add("Real comment count: ${post.commentCount}")
        }
        return aiSummaryClient.complete(SYSTEM_PROMPT, facts.joinToString("\n"))
    }

    /** Real bounded batch: real meetup posts with no summary yet, or one older than
     * [STALE_AFTER] -- never "every post, every call." */
    @Transactional
    fun generateMissing(limit: Int = DEFAULT_BATCH_LIMIT): Int {
        if (!aiSummaryClient.isConfigured) return 0
        val staleBefore = Instant.now().minus(STALE_AFTER)
        val candidates = postRepository.findAll()
            .filter { it.category == "meetup" && (it.aiSummary == null || (it.aiSummaryGeneratedAt?.isBefore(staleBefore) == true)) }
            .take(limit)

        var generated = 0
        for (post in candidates) {
            val summary = generateSummaryFor(post) ?: continue
            post.aiSummary = summary.take(500)
            post.aiSummaryGeneratedAt = Instant.now()
            postRepository.save(post)
            generated++
            logger.info("Generated real AI summary for community post {}", post.id)
        }
        return generated
    }
}
