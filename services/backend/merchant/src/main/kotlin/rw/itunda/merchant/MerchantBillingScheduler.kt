package rw.itunda.merchant

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real scheduled recurring-billing execution -- same shape as
 * `rw.itunda.p2p.AutoTransferScheduler`: the *business* cadence is real (each plan's
 * own `intervalDays`), the *poll* interval below is demo-speed so a real subscription
 * cycle doesn't require the process to stay up that long to observe it working end to
 * end.
 */
@Component
class MerchantBillingScheduler(private val merchantBillingService: MerchantBillingService) {
    private val log = LoggerFactory.getLogger(MerchantBillingScheduler::class.java)

    @Scheduled(fixedDelay = 30000)
    fun run() {
        val due = merchantBillingService.getDueForExecution()
        for (subscription in due) {
            val succeeded = merchantBillingService.chargeOne(subscription)
            log.info("Processed subscription charge {} (succeeded={})", subscription.id, succeeded)
        }
    }
}
