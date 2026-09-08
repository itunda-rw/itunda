package rw.itunda.loans

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real Harvest Advance overdue flagging (Bank product-completeness pass, cycle 2,
 * 2026-09-08) -- mirrors VupLoanOverdueScheduler exactly, including its own
 * per-item try/catch (the 2026-09-05 scheduler-poisoning fix class: one bad row
 * can't poison the sweep). HarvestAdvance.repaymentDueDate has been stored on
 * every row since this feature shipped; nothing ever checked it against
 * `Instant.now()` until CooperativeService.getAdvancesDueForOverdueCheck. The
 * poll interval below is demo-speed, same convention as VupLoanOverdueScheduler's
 * own doc comment establishes -- checking every 60 seconds for advances whose
 * real due date has already elapsed is cheap and correct; it does not mean
 * advances go overdue every 60 seconds.
 */
@Component
class HarvestAdvanceOverdueScheduler(private val cooperativeService: CooperativeService) {
    private val log = LoggerFactory.getLogger(HarvestAdvanceOverdueScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        val due = cooperativeService.getAdvancesDueForOverdueCheck()
        for (advance in due) {
            try {
                cooperativeService.markOverdue(advance)
                log.info("Harvest advance {} flagged OVERDUE (due {}, principal {})", advance.id, advance.repaymentDueDate, advance.principalAmount)
            } catch (e: Exception) {
                log.error("Harvest advance overdue flagging failed for {}", advance.id, e)
            }
        }
    }
}
