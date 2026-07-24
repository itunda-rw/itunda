package rw.itunda.p2p

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real scheduled auto-transfer execution -- same shape as
 * WeeklySavingsScheduler/AutoSaveScheduler: the *business* cadence is real (weekly/
 * monthly, locked to each plan's own day), the *poll* interval below is demo-speed so a
 * real week/month doesn't require the process to stay up that long to observe it
 * working end to end.
 */
@Component
class AutoTransferScheduler(private val autoTransferService: AutoTransferService) {
    private val log = LoggerFactory.getLogger(AutoTransferScheduler::class.java)

    @Scheduled(fixedDelay = 30000)
    fun run() {
        val due = autoTransferService.getDueForExecution()
        for (autoTransfer in due) {
            val succeeded = autoTransferService.executeOne(autoTransfer)
            log.info("Processed auto-transfer {} (succeeded={})", autoTransfer.id, succeeded)
        }
    }
}
