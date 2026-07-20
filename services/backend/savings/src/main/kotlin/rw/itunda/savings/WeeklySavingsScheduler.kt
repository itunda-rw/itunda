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
            val succeeded = weeklySavingsService.processDueInstallment(plan)
            log.info("Processed weekly installment for plan {} (succeeded={})", plan.id, succeeded)
        }
        return due.size
    }
}
