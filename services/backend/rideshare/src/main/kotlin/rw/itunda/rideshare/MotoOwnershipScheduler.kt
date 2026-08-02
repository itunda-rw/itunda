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
 */
@Component
class MotoOwnershipScheduler(private val motoOwnershipService: MotoOwnershipService) {
    private val log = LoggerFactory.getLogger(MotoOwnershipScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        val due = motoOwnershipService.getPlansDueForAutoContribution()
        for (plan in due) {
            val succeeded = motoOwnershipService.autoContribute(plan)
            if (succeeded) {
                log.info("Auto-contributed to moto-taxi ownership plan {} (saved {} of {})", plan.id, plan.savedAmount, plan.downPaymentTarget)
            }
        }
    }
}
