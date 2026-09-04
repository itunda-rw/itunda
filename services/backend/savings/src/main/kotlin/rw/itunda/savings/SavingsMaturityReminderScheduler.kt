package rw.itunda.savings

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real KB국민은행-style 상품만기알림서비스 (product maturity alert service) (2026-08-17) --
 * see SavingsService.getGoalsDueForMaturityReminder/sendMaturityReminder's own doc
 * comments. `SavingsGoal.targetDate` has been a real, stored field since this goal
 * concept existed, but nothing ever notified a user when it actually arrived -- same
 * "real data sitting unused" shape [[project_itunda_uncalled_endpoint_sweep]] has
 * repeatedly found. Same real "demo-speed poll, real business condition" convention
 * WeeklySavingsScheduler's own doc comment already establishes; `processDue` is also
 * exposed as a manually-triggerable endpoint for verifying a real maturity date without
 * waiting real wall-clock days/weeks for one to actually arrive.
 *
 * Real fix (found via project_itunda_zero_test_coverage_sweep's scheduler audit,
 * 2026-09-05): `sendMaturityReminder` had no try/catch of its own, and this loop had
 * none either. Per-goal try/catch closes it.
 */
@Component
class SavingsMaturityReminderScheduler(private val savingsService: SavingsService) {
    private val log = LoggerFactory.getLogger(SavingsMaturityReminderScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        processDue()
    }

    fun processDue(): Int {
        val due = savingsService.getGoalsDueForMaturityReminder()
        for (goal in due) {
            try {
                savingsService.sendMaturityReminder(goal.id)
                log.info("Sent maturity reminder for savings goal {}", goal.id)
            } catch (e: Exception) {
                log.error("Savings maturity reminder failed for {}", goal.id, e)
            }
        }
        return due.size
    }
}
