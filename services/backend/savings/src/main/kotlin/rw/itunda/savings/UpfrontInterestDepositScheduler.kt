package rw.itunda.savings

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real 12-month maturity polling -- same shape as `WeeklySavingsScheduler`: the
 * *business* term is real (12 real months), the *poll* interval below is demo-speed.
 * `UpfrontInterestDepositController.processDue` additionally exposes this same logic as
 * a manually-triggerable endpoint, for verifying a real maturity without waiting real
 * wall-clock months.
 */
@Component
class UpfrontInterestDepositScheduler(private val upfrontInterestDepositService: UpfrontInterestDepositService) {
    private val log = LoggerFactory.getLogger(UpfrontInterestDepositScheduler::class.java)

    @Scheduled(fixedDelay = 30000)
    fun run() {
        processDue()
    }

    fun processDue(): Int {
        val due = upfrontInterestDepositService.getDepositsDueForMaturity()
        for (deposit in due) {
            upfrontInterestDepositService.matureDeposit(deposit)
            log.info("Matured upfront-interest deposit {}", deposit.id)
        }
        return due.size
    }
}
