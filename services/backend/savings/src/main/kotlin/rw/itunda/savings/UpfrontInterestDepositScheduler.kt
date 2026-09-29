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
 *
 * Real fix (found via project_itunda_zero_test_coverage_sweep's scheduler audit,
 * 2026-09-05): `matureDeposit` had no try/catch of its own around its repository
 * save, and this loop had none either -- lower real-world likelihood than a ledger
 * call throwing (no money moves here, only a status flip), but still a real instance
 * of the same "one bad row can't poison the sweep" gap this codebase's schedulers are
 * otherwise expected to close. Per-deposit try/catch closes it for consistency.
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
            try {
                upfrontInterestDepositService.matureDeposit(deposit)
                log.info("Matured upfront-interest deposit {}", deposit.id)
            } catch (e: Exception) {
                log.error("Upfront-interest deposit maturity failed for {}", deposit.id, e)
            }
        }
        return due.size
    }
}
