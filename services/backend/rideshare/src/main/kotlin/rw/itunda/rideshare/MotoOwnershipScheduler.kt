package rw.itunda.rideshare

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real moto-taxi ownership plan auto-contribution -- see `MotoOwnershipService`'s own
 * doc comment. The *business* cadence is real (a SAVING plan's `lastAutoContributionAt`
 * is null or more than 30 days stale). The *poll* interval below is demo-speed, same
 * convention as `AutoSaveScheduler`'s/`VupLoanOverdueScheduler`'s own doc comments
 * establish: checking every 60 seconds for plans whose real 30-day cadence has already
 * elapsed is cheap and correct; it does not mean plans auto-contribute every 60
 * seconds.
 *
 * Real fix (found via project_itunda_zero_test_coverage_sweep's scheduler audit,
 * 2026-09-05): `autoContribute`'s own `Boolean` return only covers the deliberate
 * insufficient-funds case -- it calls `ledgerService.postLedgerTransaction` with no
 * try/catch of its own, so a genuinely unexpected failure throws uncaught. This loop
 * had no try/catch either, so that exception would silently stop auto-contribution
 * for every OTHER real due plan in the same tick. Per-plan try/catch closes it.
 */
@Component
class MotoOwnershipScheduler(private val motoOwnershipService: MotoOwnershipService) {
    private val log = LoggerFactory.getLogger(MotoOwnershipScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        val due = motoOwnershipService.getPlansDueForAutoContribution()
        for (plan in due) {
            try {
                val succeeded = motoOwnershipService.autoContribute(plan)
                if (succeeded) {
                    log.info("Auto-contributed to moto-taxi ownership plan {} (saved {} of {})", plan.id, plan.savedAmount, plan.downPaymentTarget)
                }
            } catch (e: Exception) {
                log.error("Moto-taxi ownership auto-contribution failed for plan {}", plan.id, e)
            }
        }
    }
}
