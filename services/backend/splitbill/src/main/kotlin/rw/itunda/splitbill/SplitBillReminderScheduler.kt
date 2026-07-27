package rw.itunda.splitbill

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real background automation for KakaoPay's own real 정산하기 reminder nudge -- closes
 * this feature's own previously-named deferred follow-up (see SplitBill.kt's own doc
 * comment). `SplitBillService.sendDueReminders` was real but nothing ever called it on
 * a real recurring cadence; this sweep is what makes it genuinely automatic. Same
 * "business condition is real, poll interval is demo-speed" shape as every other
 * recurring feature's own scheduler in this codebase (`AutoTopUpScheduler`,
 * `AutoSaveScheduler`) -- the real 24-hour reminder cadence lives in the service, not
 * here; a fresh, never-yet-reminded participant is immediately due on the very first
 * poll after their split bill is created, same "null means immediately eligible" trick
 * those other schedulers already established, so live verification never needs to wait
 * out a real 24 hours to prove the sweep works end to end.
 */
@Component
class SplitBillReminderScheduler(private val splitBillService: SplitBillService) {
    private val log = LoggerFactory.getLogger(SplitBillReminderScheduler::class.java)

    @Scheduled(fixedDelay = 30000)
    fun run() {
        try {
            val remindedCount = splitBillService.sendDueReminders()
            if (remindedCount > 0) {
                log.info("Split bill reminder sweep sent {} real reminder(s)", remindedCount)
            }
        } catch (e: Exception) {
            log.error("Split bill reminder sweep failed", e)
        }
    }
}
