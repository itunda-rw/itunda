package rw.itunda.loans

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real background automation for a postpaid credit line's own real ongoing late-fee
 * accrual -- see PostpaidCreditLine.kt's own doc comment. Same "business condition is
 * real (a real cycle due date has passed), poll interval is demo-speed" shape every
 * other recurring feature's own scheduler in this codebase already establishes
 * (`OverdraftInterestAccrualScheduler`, `AutoSaveScheduler`).
 */
@Component
class PostpaidCreditAccrualScheduler(private val postpaidCreditService: PostpaidCreditService) {
    private val log = LoggerFactory.getLogger(PostpaidCreditAccrualScheduler::class.java)

    @Scheduled(fixedDelay = 30000)
    fun run() {
        val lines = postpaidCreditService.getLinesOverdueForLateFee()
        for (line in lines) {
            try {
                postpaidCreditService.accrueLateFee(line)
            } catch (e: Exception) {
                log.error("Postpaid credit late-fee accrual failed for line {}", line.id, e)
            }
        }
    }
}
