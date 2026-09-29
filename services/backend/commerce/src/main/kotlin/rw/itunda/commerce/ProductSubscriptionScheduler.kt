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
 *
 * Real gap found 2026-09-05 (concurrency-audit continuation, same fix as
 * MerchantBillingScheduler's own doc comment): `executeOne`'s FINAL
 * `productSubscriptionRepository.save(subscription)` call sits outside its own
 * try/catch (which only wraps the real order-placement call), and this loop had no
 * per-subscription try/catch of its own either -- a genuine failure there used to
 * propagate uncaught and silently stop delivering every OTHER due subscription in the
 * same tick.
 */
@Component
class ProductSubscriptionScheduler(private val productSubscriptionService: ProductSubscriptionService) {
    private val log = LoggerFactory.getLogger(ProductSubscriptionScheduler::class.java)

    @Scheduled(fixedDelay = 30000)
    fun run() {
        val due = productSubscriptionService.getDueForExecution()
        for (subscription in due) {
            try {
                val succeeded = productSubscriptionService.executeOne(subscription)
                log.info("Processed subscription delivery {} (succeeded={})", subscription.id, succeeded)
            } catch (e: Exception) {
                log.error("Subscription delivery {} failed with an unexpected error", subscription.id, e)
            }
        }
    }
}
