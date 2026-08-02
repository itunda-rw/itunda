package rw.itunda.loans

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real BRD student loan grace-period-end flagging -- see `StudentLoanService`'s own
 * doc comment. The *business* cadence is real (a loan's `graceEndsAt`, itunda's own
 * honest 6-month pick within BRD's real sourced 6-12 month range, has passed). The
 * *poll* interval below is demo-speed, same convention as `VupLoanOverdueScheduler`'s
 * own doc comment establishes: checking every 60 seconds for grace periods that have
 * already elapsed is cheap and correct; it does not mean grace periods end every 60
 * seconds.
 */
@Component
class StudentLoanGracePeriodScheduler(private val studentLoanService: StudentLoanService) {
    private val log = LoggerFactory.getLogger(StudentLoanGracePeriodScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        val due = studentLoanService.getLoansDueForGracePeriodEnd()
        for (loan in due) {
            studentLoanService.markRepaying(loan)
            log.info("Student loan {} grace period ended, now REPAYING (graceEndsAt {}, outstanding {})", loan.id, loan.graceEndsAt, loan.outstandingBalance)
        }
    }
}
