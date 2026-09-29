package rw.itunda.rideshare

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real scheduled reassignment for ride dispatch -- see RideTripService's own doc
 * comment for the full account. The *business* window is real (`RideTrip.OFFER_WINDOW`,
 * 15 seconds, directly Kakao's own sourced cancellation-surge threshold). Polled every
 * 3 seconds -- proportionally tighter than `DispatchOfferScheduler`'s own 10-second poll
 * against Eats' 90-second window, so a real passenger is never left waiting anywhere
 * close to the full real window before reassignment kicks in.
 *
 * Fixed 2026-08-18 (docs/DESIGN_REFERENCES.md Section 180) to actually match the "poll
 * for due rows read-only, resolve each real row inside its own per-item @Transactional
 * method, one bad row never blocks the sweep for every other real due row" shape
 * BikeRentalAbandonedSessionScheduler/BookingNoShowScheduler already establish -- both
 * loops below used to live inside single batch-@Transactional service methods instead
 * of here, see `RideTripService.reassignExpiredOffer`/`activateScheduledDispatchOne`'s
 * own doc comments for the real bug that closed. The once-per-tick candidate-pool
 * computation the old batched methods did is preserved via `computeDispatchPools`,
 * called once per section below rather than once per trip.
 */
@Component
class RideDispatchScheduler(private val rideTripService: RideTripService) {
    private val log = LoggerFactory.getLogger(RideDispatchScheduler::class.java)

    @Scheduled(fixedDelay = 3000)
    fun run() {
        val expired = rideTripService.getExpiredOffers()
        if (expired.isNotEmpty()) {
            val pools = rideTripService.computeDispatchPools()
            var reassigned = 0
            for (trip in expired) {
                try {
                    rideTripService.reassignExpiredOffer(trip.id, pools)
                    reassigned++
                } catch (e: Exception) {
                    log.error("Ride dispatch reassignment failed for trip {}", trip.id, e)
                }
            }
            log.info("Reassigned {} expired ride dispatch offer(s)", reassigned)
        }

        // Real Kakao T 예약 호출 (scheduled ride booking) -- see
        // RideTripService.activateScheduledDispatchOne's own doc comment. Same poll
        // tick as the reassignment check above; a real scheduled trip's own lead-time
        // threshold is the actual business timing, not this poll interval.
        val dueScheduled = rideTripService.getDueScheduledTrips()
        if (dueScheduled.isNotEmpty()) {
            val pools = rideTripService.computeDispatchPools()
            var activated = 0
            for (trip in dueScheduled) {
                try {
                    rideTripService.activateScheduledDispatchOne(trip.id, pools)
                    activated++
                } catch (e: Exception) {
                    log.error("Scheduled ride dispatch activation failed for trip {}", trip.id, e)
                }
            }
            log.info("Started real dispatch for {} due scheduled ride(s)", activated)
        }
    }
}
