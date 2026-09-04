package rw.itunda.loans

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real, minimal pre-due repayment reminder (2026-08-10) -- see VupLoanService.
 * getLoansDueSoonForReminder's own doc comment for the full account: itunda had no
 * reminder before a VUP loan went overdue, only a silent after-the-fact flag (see
 * VupLoanOverdueScheduler). MicroSave/Access to Finance Rwanda's own 2026 research
 * found "only 14% of borrowers repay loans digitally" in Rwanda -- a real, current,
 * documented gap this reminder is a direct, scoped response to. Same demo-speed poll
 * convention VupLoanOverdueScheduler's own doc comment establishes.
 *
 * Real fix (found via project_itunda_zero_test_coverage_sweep's scheduler audit,
 * 2026-09-05): `sendDueReminder` had no try/catch of its own, and this loop had none
 * either -- per-loan try/catch closes it, matching VupLoanOverdueScheduler's own
 * sibling fix.
 */
@Component
class VupLoanReminderScheduler(private val vupLoanService: VupLoanService) {
    private val log = LoggerFactory.getLogger(VupLoanReminderScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        val dueSoon = vupLoanService.getLoansDueSoonForReminder()
        for (loan in dueSoon) {
            try {
                vupLoanService.sendDueReminder(loan)
                log.info("VUP loan {} sent due-soon reminder (due {})", loan.id, loan.dueDate)
            } catch (e: Exception) {
                log.error("VUP loan due-soon reminder failed for {}", loan.id, e)
            }
        }
    }
}
