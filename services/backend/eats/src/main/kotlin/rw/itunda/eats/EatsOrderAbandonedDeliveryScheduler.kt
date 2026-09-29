package rw.itunda.eats

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real scheduled force-cancel for an Eats delivery abandoned mid-flight -- see
 * EatsOrderService.getAbandonedDeliveries/forceCancelAbandonedDelivery's own doc
 * comments for the full account of the gap this closes. Same "poll for due rows
 * read-only, resolve each real row inside its own per-item @Transactional method, one
 * bad row never blocks the sweep for every other real due row" shape
 * BikeRentalAbandonedSessionScheduler/ParkingAbandonedSessionScheduler/
 * MarketplaceEscrowAutoReleaseScheduler already establish -- deliberately never a
 * single batch-@Transactional loop (see docs/DESIGN_REFERENCES.md's own real
 * transaction-poisoning bug-class writeup for why a batch-transactional scheduler loop
 * over independent rows is a genuine, previously-hit footgun in this codebase).
 */
@Component
class EatsOrderAbandonedDeliveryScheduler(private val eatsOrderService: EatsOrderService) {
    private val log = LoggerFactory.getLogger(EatsOrderAbandonedDeliveryScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        val due = eatsOrderService.getAbandonedDeliveries()
        for (order in due) {
            try {
                val resolved = eatsOrderService.forceCancelAbandonedDelivery(order.id)
                if (resolved != null && resolved.status.name == "CANCELLED") {
                    log.info("Force-cancelled abandoned Eats delivery {} after exceeding the max delivery window", order.id)
                }
            } catch (e: Exception) {
                log.error("Eats abandoned-delivery force-cancel failed for {}", order.id, e)
            }
        }
    }
}
