package rw.itunda.p2p

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real pre-release reminder to the sender of a 지연이체 (delayed transfer), before it
 * auto-releases and can no longer be cancelled -- see
 * P2pDelayedTransferService.getDueForReminder/sendReminder's own doc comments. Same
 * "poll for due rows, act per row, done" shape as
 * P2pDelayedTransferReleaseScheduler/MarketplaceEscrowAutoReleaseReminderScheduler,
 * with a per-row try/catch from the start so one bad row can never block the sweep
 * for every other real due reminder in the same poll -- the exact previously-
 * recurring "scheduler transaction-poisoning" bug class this codebase's own Sections
 * 115-181 already found and fixed nine times. `processDue` is also exposed for
 * verifying a real reminder window without waiting real wall-clock time.
 */
@Component
class P2pDelayedTransferReminderScheduler(private val p2pDelayedTransferService: P2pDelayedTransferService) {
    private val log = LoggerFactory.getLogger(P2pDelayedTransferReminderScheduler::class.java)

    @Scheduled(fixedDelay = 30000)
    fun run() {
        processDue()
    }

    fun processDue(): Int {
        val due = p2pDelayedTransferService.getDueForReminder()
        for (transfer in due) {
            try {
                p2pDelayedTransferService.sendReminder(transfer.id)
                log.info("Sent pre-release reminder for delayed P2P transfer {}", transfer.id)
            } catch (e: Exception) {
                log.error("Delayed P2P transfer reminder failed for {}", transfer.id, e)
            }
        }
        return due.size
    }
}
