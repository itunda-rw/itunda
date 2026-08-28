package rw.itunda.eats

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.ai.AiSummaryClient
import rw.itunda.core.domain.Merchant
import rw.itunda.core.repository.MerchantRepository
import java.time.Duration
import java.time.Instant

/**
 * Real, self-hosted AI place-summary batch generation -- see AiSummaryClient's own
 * doc comment for the full account of why/how this is self-hosted, not a third-party
 * AI API. Lives in `:eats`, not `:merchant`, specifically so it can reuse
 * EatsReviewService's own real rating/tag-aggregate methods directly rather than
 * duplicating them -- confirmed via a real module-dependency check before placing this
 * here: `:eats` already depends on `:core` (for Merchant/MerchantRepository) and
 * doesn't need a new `:merchant` dependency, avoiding any risk of the reverse
 * (`:merchant` -> `:eats`) creating a real circular module dependency later.
 *
 * **Batch, never live-per-request** -- see AiSummaryClient's own doc comment on why:
 * itunda's private cloud is severely resource-constrained and has already hit a real
 * severe overload incident this same session under ordinary load. `generateMissing`
 * processes a real, small, bounded batch per call (never "all merchants at once"),
 * meant to be invoked by a real low-frequency scheduled job (AiSummaryScheduler) or a
 * manual admin trigger -- both explicitly opt-in, not automatic on every request path.
 *
 * **Real fact-grounding discipline**: the prompt supplied to the model contains ONLY
 * real, already-known structured fields about one real merchant (rating, real review
 * tag counts, category, hours) -- the system prompt explicitly instructs the model
 * never to state a fact not given to it. This bounds hallucination risk but doesn't
 * eliminate it the way a template-only approach would; the real, honest tradeoff for a
 * genuinely generative (not just templated) summary, matching the user's own direct
 * request for real AI, not a fancier template.
 */
@Service
class AiSummaryService(
    private val merchantRepository: MerchantRepository,
    private val eatsReviewService: EatsReviewService,
    private val aiSummaryClient: AiSummaryClient,
) {
    private val logger = LoggerFactory.getLogger(AiSummaryService::class.java)

    companion object {
        private val STALE_AFTER = Duration.ofDays(14)
        private const val DEFAULT_BATCH_LIMIT = 20

        private const val SYSTEM_PROMPT = "You write a single short, honest, one-sentence summary of a real local restaurant for a map app, in plain English, under 40 words. " +
            "Use ONLY the facts given to you below -- never state a rating, a dish, a price, an amenity, or any other fact that wasn't explicitly given. " +
            "If few facts are given, write a shorter, more general sentence rather than inventing detail. Do not use markdown or quotation marks."
    }

    /** Real one-merchant summary generation -- builds a strictly fact-grounded prompt
     * from real, already-known fields only, never invented ones. Returns null (and
     * leaves the merchant's existing summary untouched) if the model is unconfigured,
     * unreachable, or returns nothing usable. */
    fun generateSummaryFor(merchant: Merchant): String? {
        val rating = eatsReviewService.getRestaurantRating(merchant.id)
        val goodPointCounts = eatsReviewService.restaurantGoodPointCounts(merchant.id)
        val topTags = goodPointCounts.entries.sortedByDescending { it.value }.take(3).map { it.key.lowercase().replace('_', ' ') }

        // Real, live-verified finding (2026-08-28): with only a name + rating, the
        // model would rather invent a plausible-sounding cuisine/location than admit
        // it has nothing to describe -- a real small-model instruction-following gap,
        // not fixable by prompt wording alone. So generation requires at least one
        // real DESCRIPTIVE fact (what the place actually IS/does), not just a number.
        val descriptiveFacts = buildList {
            merchant.category?.let { add("Category: $it") }
            if (topTags.isNotEmpty()) add("What reviewers most often praised: ${topTags.joinToString(", ")}")
            merchant.openingHours?.let { add("Opening hours: $it") }
        }
        if (descriptiveFacts.isEmpty()) return null // Nothing real to describe -- an honest general sentence would still risk sounding invented.

        val facts = buildList {
            add("Business name: ${merchant.businessName}")
            addAll(descriptiveFacts)
            if (rating.count > 0 && rating.average != null) add("Real average rating: %.1f out of 5, from ${rating.count} real reviews".format(rating.average))
        }

        return aiSummaryClient.complete(SYSTEM_PROMPT, facts.joinToString("\n"))
    }

    /** Real bounded batch: merchants with no summary yet, or one older than
     * [STALE_AFTER] -- never "every merchant, every call." Returns how many were
     * actually (re)generated; a merchant is silently skipped (not retried this run) if
     * the model returns nothing usable for it. */
    @Transactional
    fun generateMissing(limit: Int = DEFAULT_BATCH_LIMIT): Int {
        if (!aiSummaryClient.isConfigured) return 0
        val staleBefore = Instant.now().minus(STALE_AFTER)
        val candidates = merchantRepository.findAll()
            .filter { it.aiSummary == null || (it.aiSummaryGeneratedAt?.isBefore(staleBefore) == true) }
            .take(limit)

        var generated = 0
        for (merchant in candidates) {
            val summary = generateSummaryFor(merchant) ?: continue
            merchant.aiSummary = summary.take(500)
            merchant.aiSummaryGeneratedAt = Instant.now()
            merchantRepository.save(merchant)
            generated++
            logger.info("Generated real AI summary for merchant {}", merchant.id)
        }
        return generated
    }
}
