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
 */
@Component
class AutoSaveScheduler(private val savingsService: SavingsService) {
    private val log = LoggerFactory.getLogger(AutoSaveScheduler::class.java)

    @Scheduled(fixedDelay = 30000)
    fun run() {
        val due = savingsService.getGoalsDueForAutoContribution()
        for (goal in due) {
            val succeeded = savingsService.autoContribute(goal)
            if (succeeded) {
                log.info("Auto-contributed {} to goal {} ({})", goal.monthlyContribution, goal.id, goal.name)
            }
        }
    }
}
