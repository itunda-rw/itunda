package rw.itunda.core.ai

import java.time.Duration

/**
 * Real gap found+fixed 2026-09-06, same shape as this session's own duplicated-
 * constant sweep (`core/pricing/PlatformFees`/`TipPolicy`/`ReminderWindows`/
 * `CancellationPolicy`): `STALE_AFTER = Duration.ofDays(14)` and
 * `DEFAULT_BATCH_LIMIT = 20` were independently re-declared, byte-for-byte
 * identical, in both `AiSummaryService` (Maps place summaries, built 2026-08-28
 * 10:42) and `HoodAiSummaryService` (Community meetup summaries, built the SAME DAY
 * at 19:00) -- unlike every other family in this sweep, neither one's own doc
 * comment explicitly admits the duplication with a "reused, not reinvented"-style
 * sentence naming the other class, which is why this pair was initially left
 * unconsolidated pending stronger evidence. Checked git history instead:
 * `HoodAiSummaryService`'s own commit message explicitly says it "reuses the
 * self-hosted llama-server/AiSummaryClient already deployed for this session's Maps
 * work" -- built the same day, same session, as a deliberate adaptation of the
 * earlier feature, not an independent convergence. That's sufficient evidence this
 * is the same real, deliberate batch-generation policy, not a coincidence.
 *
 * `STALE_AFTER`: how long a generated AI summary is trusted before the next batch
 * sweep considers it worth regenerating (an itunda-chosen cache-freshness tradeoff,
 * not sourced from any external reference -- self-hosted `llama-server` calls have a
 * real compute cost, so "every post/merchant, every call" would be wasteful).
 * `DEFAULT_BATCH_LIMIT`: how many summaries one scheduler tick generates, bounding
 * real LLM load per tick.
 */
object AiSummaryBatchPolicy {
    val STALE_AFTER: Duration = Duration.ofDays(14)
    const val DEFAULT_BATCH_LIMIT: Int = 20
}
