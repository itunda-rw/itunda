package rw.itunda.eats

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real scheduled reassignment for automatic dispatch (2026-07-20) -- see
 * EatsOrderService.getExpiredOffers/reassignExpiredOffer's own doc comments for the
 * full account. The *business* window is real (EatsOrderService.OFFER_WINDOW, 90
 * seconds). The *poll* interval below is demo-speed, same convention as
 * AutoSaveScheduler/OutboxRelay's own fixedDelay: checking every 10 seconds for offers
 * whose real 90-second window has elapsed is cheap and correct, and keeps a real buyer
 * from waiting much longer than the window itself before reassignment kicks in.
 *
 * Fixed 2026-08-18 (docs/DESIGN_REFERENCES.md Section 180) to actually match the "poll
 * for due rows read-only, resolve each real row inside its own per-item @Transactional
 * method, one bad row never blocks the sweep for every other real due row" shape
 * BikeRentalAbandonedSessionScheduler/BookingNoShowScheduler/RideDispatchScheduler
 * already establish -- this loop used to live inside a single batch-@Transactional
 * service method instead of here, see EatsOrderService.reassignExpiredOffer's own doc
 * comment for the real bug that closed. The once-per-tick candidate-pool computation
 * the old batched method did (2026-07-20 performance sweep) is preserved via
 * `computeDispatchPools`, called once per tick below rather than once per order.
 */
@Component
class DispatchOfferScheduler(private val eatsOrderService: EatsOrderService) {
    private val log = LoggerFactory.getLogger(DispatchOfferScheduler::class.java)

    @Scheduled(fixedDelay = 10000)
    fun run() {
        val expired = eatsOrderService.getExpiredOffers()
        if (expired.isEmpty()) return
        val pools = eatsOrderService.computeDispatchPools()
        var reassigned = 0
        for (order in expired) {
            try {
                val resolved = eatsOrderService.reassignExpiredOffer(order.id, pools)
                if (resolved != null) reassigned++
            } catch (e: Exception) {
                log.error("Eats dispatch reassignment failed for order {}", order.id, e)
            }
        }
        if (reassigned > 0) {
            log.info("Reassigned {} expired dispatch offer(s)", reassigned)
        }
    }
}
