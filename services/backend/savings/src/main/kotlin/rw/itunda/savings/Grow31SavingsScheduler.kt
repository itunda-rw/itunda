package rw.itunda.savings

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real daily maturity sweep for 31-day savings plans -- see Grow31SavingsService's own
 * doc comment: unlike WeeklySavingsScheduler, this never moves money itself (deposits
 * are a real user-triggered action, `depositToday`), it only catches a plan whose real
 * 31-day calendar window elapsed without a final `depositToday` call ever crossing
 * `TERM_DAYS` on its own (e.g. the user stopped depositing before day 31 but never
 * explicitly cancelled). Same demo-speed poll interval convention as
 * WeeklySavingsScheduler -- the real business cadence is a full calendar day, the poll
 * itself is just fast enough that a real maturity doesn't require the process to stay
 * up an actual 31 days to observe it working end to end.
 *
 * Real fix (found via project_itunda_zero_test_coverage_sweep's scheduler audit,
 * 2026-09-05): `maturePlan` calls `ledgerService.postLedgerTransaction` (for the
 * real maturity-interest payout) with no try/catch of its own, and this loop had none
 * either -- a single bad plan (a since-deleted account, a transient ledger error)
 * would throw uncaught and silently stop maturity processing for every OTHER real due
 * plan the same day. Per-plan try/catch closes it, matching every other resilient
 * scheduler's shape.
 */
@Component
class Grow31SavingsScheduler(private val grow31SavingsService: Grow31SavingsService) {
    private val log = LoggerFactory.getLogger(Grow31SavingsScheduler::class.java)

    @Scheduled(fixedDelay = 30000)
    fun run() {
        processDue()
    }

    fun processDue(): Int {
        val due = grow31SavingsService.getPlansDueForMaturity()
        for (plan in due) {
            try {
                grow31SavingsService.maturePlan(plan)
                log.info("Matured 31-day savings plan {} via scheduler sweep", plan.id)
            } catch (e: Exception) {
                log.error("31-day savings maturity failed for plan {}", plan.id, e)
            }
        }
        return due.size
    }
}
