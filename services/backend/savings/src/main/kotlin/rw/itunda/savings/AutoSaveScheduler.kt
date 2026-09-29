package rw.itunda.savings

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real recurring auto-save -- see docs/TOSS_PARITY_MATRIX.md's Savings row ("recurring
 * auto-save scheduling still target"). The *business* cadence is real (30 days since a
 * goal's last auto-contribution, or never contributed at all), same as
 * SavingsService.AUTO_CONTRIBUTION_INTERVAL_DAYS. The *poll* interval below is demo-speed,
 * same convention as OutboxRelay's 2-second fixedDelay -- checking every 30 seconds for
 * goals whose real 30-day window has elapsed is cheap and correct; it does not mean
 * contributions happen every 30 seconds.
 *
 * Real fix (found via project_itunda_zero_test_coverage_sweep's scheduler audit,
 * 2026-09-05): `autoContribute`'s own `Boolean` return only covers the deliberate
 * insufficient-funds case -- it re-fetches the goal via `findByIdForUpdate(...)
 * .orElseThrow { GoalNotFoundException(...) }` and calls
 * `ledgerService.postLedgerTransaction` with no try/catch of its own, so a genuinely
 * unexpected failure (a since-deleted goal, a transient ledger error) throws uncaught.
 * This loop had no try/catch either, so that exception would silently stop
 * auto-contribution for every OTHER real due goal in the same tick -- the exact
 * "scheduler transaction-poisoning" bug class this codebase has already found and
 * fixed multiple times elsewhere. Per-goal try/catch closes it; the existing Boolean
 * return still covers the expected insufficient-funds case exactly as before.
 */
@Component
class AutoSaveScheduler(private val savingsService: SavingsService) {
    private val log = LoggerFactory.getLogger(AutoSaveScheduler::class.java)

    @Scheduled(fixedDelay = 30000)
    fun run() {
        val due = savingsService.getGoalsDueForAutoContribution()
        for (goal in due) {
            try {
                val succeeded = savingsService.autoContribute(goal)
                if (succeeded) {
                    log.info("Auto-contributed {} to goal {} ({})", goal.monthlyContribution, goal.id, goal.name)
                }
            } catch (e: Exception) {
                log.error("Auto-contribution failed for goal {}", goal.id, e)
            }
        }
    }
}
