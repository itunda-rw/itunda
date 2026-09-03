package rw.itunda.savings

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real daily SafeBox-style interest accrual -- see docs/TOSS_PARITY_MATRIX.md's Savings
 * row. Same convention as AutoSaveScheduler: the *business* cadence is real (a full day
 * since a jar's nextPayoutAt), the *poll* interval below is demo-speed so a real day
 * doesn't require the process to stay up an actual 24h to observe it working end to end.
 */
@Component
class InterestAccrualScheduler(private val interestJarService: InterestJarService) {
    private val log = LoggerFactory.getLogger(InterestAccrualScheduler::class.java)

    @Scheduled(fixedDelay = 30000)
    fun run() {
        val due = interestJarService.getJarsDueForAccrual()
        for (jar in due) {
            interestJarService.accrueInterest(jar)
            log.info("Accrued interest for jar {} (balance {}, earnedThisMonth now {})", jar.userId, jar.balance, jar.earnedThisMonth)
        }
    }
}
