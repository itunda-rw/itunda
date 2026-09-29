package rw.itunda.savings

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real daily SafeBox-style interest accrual -- see docs/TOSS_PARITY_MATRIX.md's Savings
 * row. Same convention as AutoSaveScheduler: the *business* cadence is real (a full day
 * since a jar's nextPayoutAt), the *poll* interval below is demo-speed so a real day
 * doesn't require the process to stay up an actual 24h to observe it working end to end.
 *
 * Real fix (found via project_itunda_zero_test_coverage_sweep's scheduler audit,
 * 2026-09-05): `accrueInterest` calls `ledgerService.postLedgerTransaction` with no
 * try/catch of its own, and this loop had none either -- a single bad jar (a since-
 * deleted account, a transient ledger error) would throw uncaught and silently stop
 * accrual for every OTHER real due jar the same day, the exact "scheduler
 * transaction-poisoning" bug class this codebase has already found and fixed multiple
 * times elsewhere (see e.g. P2pDelayedTransferReleaseScheduler's own doc comment).
 * Per-jar try/catch closes it, matching every other resilient scheduler's shape.
 */
@Component
class InterestAccrualScheduler(private val interestJarService: InterestJarService) {
    private val log = LoggerFactory.getLogger(InterestAccrualScheduler::class.java)

    @Scheduled(fixedDelay = 30000)
    fun run() {
        val due = interestJarService.getJarsDueForAccrual()
        for (jar in due) {
            try {
                interestJarService.accrueInterest(jar)
                log.info("Accrued interest for jar {} (balance {}, earnedThisMonth now {})", jar.userId, jar.balance, jar.earnedThisMonth)
            } catch (e: Exception) {
                log.error("Interest accrual failed for jar {}", jar.userId, e)
            }
        }
    }
}
