package rw.itunda.splitbill

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real background automation for KakaoPay's own real 정산하기 reminder nudge -- closes
 * this feature's own previously-named deferred follow-up (see SplitBill.kt's own doc
 * comment). `SplitBillService.sendReminderForParticipant` was real but nothing ever
 * called it on a real recurring cadence; this sweep is what makes it genuinely
 * automatic. Same "business condition is real, poll interval is demo-speed" shape as
 * every other recurring feature's own scheduler in this codebase (`AutoTopUpScheduler`,
 * `AutoSaveScheduler`) -- the real 24-hour reminder cadence lives in the service, not
 * here; a fresh, never-yet-reminded participant is immediately due on the very first
 * poll after their split bill is created, same "null means immediately eligible" trick
 * those other schedulers already established, so live verification never needs to wait
 * out a real 24 hours to prove the sweep works end to end.
 *
 * Real fix (2026-09-13, push-before-commit ordering sweep): the loop used to live
 * entirely inside `SplitBillService.sendDueReminders`, one big non-`@Transactional`
 * method with a per-participant try/catch INSIDE it -- but that try/catch couldn't
 * protect against a self-invocation `@Transactional` gap even if the service method
 * had been annotated (see `project_itunda_bills_autopay_transaction_bug`'s own
 * identical lesson: a per-row transaction boundary only works when the loop lives in a
 * DIFFERENT bean than the one whose method needs isolating). Same real
 * "poll for due rows, act per row, own try/catch" shape as
 * `P2pDelayedTransferReminderScheduler`/`MarketplaceEscrowAutoReleaseReminderScheduler`
 * now applies here too. `processDue` is also exposed for verifying a real reminder
 * window without waiting real wall-clock time.
 */
@Component
class SplitBillReminderScheduler(private val splitBillService: SplitBillService) {
    private val log = LoggerFactory.getLogger(SplitBillReminderScheduler::class.java)

    @Scheduled(fixedDelay = 30000)
    fun run() {
        try {
            processDue()
        } catch (e: Exception) {
            log.error("Split bill reminder sweep failed", e)
        }
    }

    // Whole-call guard on the due-list read itself (a repository failure there) is the
    // caller's (`run`'s) job; this method's own try/catch only guards the per-participant
    // send, same split OrderReturnService/P2pDelayedTransferReminderScheduler already
    // establish elsewhere in this codebase.
    fun processDue(): Int {
        val due = splitBillService.getParticipantsDueForReminder()
        var remindedCount = 0
        for (participant in due) {
            try {
                if (splitBillService.sendReminderForParticipant(participant.id)) {
                    remindedCount++
                }
            } catch (e: Exception) {
                log.warn("Split bill reminder skipped for participant {}: {}", participant.id, e.message)
            }
        }
        if (remindedCount > 0) {
            log.info("Split bill reminder sweep sent {} real reminder(s)", remindedCount)
        }
        return remindedCount
    }
}
