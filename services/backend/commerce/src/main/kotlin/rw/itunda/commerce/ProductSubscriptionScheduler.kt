package rw.itunda.commerce

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real scheduled subscription-delivery execution -- same shape as
 * AutoTransferScheduler/MerchantBillingScheduler: the *business* cadence is real (each
 * subscription's own `intervalDays`, up to Coupang's real 6-month ceiling), the *poll*
 * interval below is demo-speed so a real delivery cycle doesn't require the process to
 * stay up that long to observe it working end to end.
 */
@Component
class ProductSubscriptionScheduler(private val productSubscriptionService: ProductSubscriptionService) {
    private val log = LoggerFactory.getLogger(ProductSubscriptionScheduler::class.java)

    @Scheduled(fixedDelay = 30000)
    fun run() {
        val due = productSubscriptionService.getDueForExecution()
        for (subscription in due) {
            val succeeded = productSubscriptionService.executeOne(subscription)
            log.info("Processed subscription delivery {} (succeeded={})", subscription.id, succeeded)
        }
    }
}
