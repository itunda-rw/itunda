package rw.itunda.p2p

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real scheduled-transfer execution -- same shape as AutoTransferScheduler: the
 * *business* cadence is real (the plan's own locked calendar date), the *poll* interval
 * below is demo-speed so a real future date doesn't require the process to stay up
 * that long to observe it working end to end.
 */
@Component
class ScheduledTransferScheduler(private val scheduledTransferService: ScheduledTransferService) {
    private val log = LoggerFactory.getLogger(ScheduledTransferScheduler::class.java)

    @Scheduled(fixedDelay = 30000)
    fun run() {
        val due = scheduledTransferService.getDueForExecution()
        for (scheduledTransfer in due) {
            val succeeded = scheduledTransferService.executeOne(scheduledTransfer)
            log.info("Processed scheduled transfer {} (succeeded={})", scheduledTransfer.id, succeeded)
        }
    }
}
