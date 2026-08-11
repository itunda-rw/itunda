package rw.itunda.savings

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real Deposit Protection Fund contribution scheduler -- same demo-speed-poll,
 * real-business-cadence convention as InterestAccrualScheduler: the *business* cadence
 * (a full day since the fund's own lastContributionAt) is real, the poll interval below
 * is demo-speed so a real day doesn't require the process to stay up an actual 24h to
 * observe it working end to end.
 */
@Component
class DepositProtectionScheduler(private val depositProtectionService: DepositProtectionService) {
    private val log = LoggerFactory.getLogger(DepositProtectionScheduler::class.java)

    @Scheduled(fixedDelay = 30000)
    fun run() {
        val fund = depositProtectionService.getOrCreateFund()
        if (depositProtectionService.isContributionDue(fund)) {
            depositProtectionService.accrueContribution(fund)
            log.info("Deposit Protection Fund contribution accrued (reserve now {})", fund.reserveBalance)
        }
    }
}
