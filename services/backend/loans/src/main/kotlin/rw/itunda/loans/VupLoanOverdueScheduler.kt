package rw.itunda.loans

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real VUP Financial Services overdue flagging -- see `VupLoanService`'s own doc
 * comment. The *business* cadence is real (a loan's `dueDate`, 12 months after
 * disbursement, has passed while principal is still outstanding). The *poll* interval
 * below is demo-speed, same convention as `AutoSaveScheduler`'s own doc comment
 * establishes: checking every 60 seconds for loans whose real due date has already
 * elapsed is cheap and correct; it does not mean loans go overdue every 60 seconds.
 * No penalty interest is applied, since none is sourced for VUP/FS and this backend
 * doesn't invent one. Corrected 2026-08-10: this used to be visibility-only (a server
 * log line) -- `markOverdue` now also sends the borrower a real notification, see its
 * own doc comment. See `VupLoanReminderScheduler` for the proactive, before-the-fact
 * half of this same fix.
 */
@Component
class VupLoanOverdueScheduler(private val vupLoanService: VupLoanService) {
    private val log = LoggerFactory.getLogger(VupLoanOverdueScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        val due = vupLoanService.getLoansDueForOverdueCheck()
        for (loan in due) {
            vupLoanService.markOverdue(loan)
            log.info("VUP loan {} flagged OVERDUE (due {}, outstanding {})", loan.id, loan.dueDate, loan.outstandingPrincipal)
        }
    }
}
