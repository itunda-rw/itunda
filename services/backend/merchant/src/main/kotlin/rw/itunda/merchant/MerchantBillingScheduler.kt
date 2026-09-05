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
 *
 * Real gap found 2026-09-05 (concurrency-audit continuation): an earlier scheduler
 * audit confirmed `chargeOne`'s real ledger call has its own internal try/catch, and
 * correctly marked this scheduler safe for THAT specific failure mode -- but
 * `chargeOne`'s FINAL `merchantBillingSubscriptionRepository.save(subscription)` call
 * sits outside that try/catch, and this loop had no per-subscription try/catch of its
 * own either. A genuine failure there (e.g. an ObjectOptimisticLockingFailureException
 * from a real concurrent mutation) used to propagate uncaught and silently stop
 * charging every OTHER due subscription in the same tick -- the exact "scheduler
 * transaction-poisoning" bug class this codebase has already found and fixed on
 * `GiftVoucherExpiryScheduler`/`VendorCashAdvanceCollectionScheduler`/others.
 */
@Component
class MerchantBillingScheduler(private val merchantBillingService: MerchantBillingService) {
    private val log = LoggerFactory.getLogger(MerchantBillingScheduler::class.java)

    @Scheduled(fixedDelay = 30000)
    fun run() {
        val due = merchantBillingService.getDueForExecution()
        for (subscription in due) {
            try {
                val succeeded = merchantBillingService.chargeOne(subscription)
                log.info("Processed subscription charge {} (succeeded={})", subscription.id, succeeded)
            } catch (e: Exception) {
                log.error("Subscription charge {} failed with an unexpected error", subscription.id, e)
            }
        }
    }
}
