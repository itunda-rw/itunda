package rw.itunda.savings

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real weekly auto-debit for 26-week savings plans -- same shape as
 * `AutoSaveScheduler`/`InterestAccrualScheduler`: the *business* cadence is real (a
 * full 7 days since a plan's last processed installment, hard-locked to the plan's
 * opening weekday), the *poll* interval below is demo-speed so a real week doesn't
 * require the process to stay up an actual 7 days to observe it working end to end.
 * `WeeklySavingsController.processDue` additionally exposes this same logic as a
 * manually-triggerable endpoint, for verifying a real 26-week maturity without waiting
 * real wall-clock weeks.
 *
 * Real fix (found via project_itunda_zero_test_coverage_sweep's scheduler audit,
 * 2026-09-05): `processDueInstallment`'s own `Boolean` return only covers the
 * deliberate insufficient-funds case -- it calls `ledgerService.postLedgerTransaction`
 * (and `accountRepository.findById(...).orElseThrow`) with no try/catch of its own, so
 * a genuinely unexpected failure throws uncaught. This loop had no try/catch either,
 * so that exception would silently stop installment processing for every OTHER real
 * due plan in the same tick. Per-plan try/catch closes it.
 */
@Component
class WeeklySavingsScheduler(private val weeklySavingsService: WeeklySavingsService) {
    private val log = LoggerFactory.getLogger(WeeklySavingsScheduler::class.java)

    @Scheduled(fixedDelay = 30000)
    fun run() {
        processDue()
    }

    fun processDue(): Int {
        val due = weeklySavingsService.getPlansDueForProcessing()
        for (plan in due) {
            try {
                val succeeded = weeklySavingsService.processDueInstallment(plan)
                log.info("Processed weekly installment for plan {} (succeeded={})", plan.id, succeeded)
            } catch (e: Exception) {
                log.error("Weekly installment processing failed for plan {}", plan.id, e)
            }
        }
        return due.size
    }
}
