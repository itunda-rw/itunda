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
 */
@Component
class RideDispatchScheduler(private val rideTripService: RideTripService) {
    private val log = LoggerFactory.getLogger(RideDispatchScheduler::class.java)

    @Scheduled(fixedDelay = 3000)
    fun run() {
        val expired = rideTripService.getExpiredOffers()
        if (expired.isNotEmpty()) {
            rideTripService.reassignExpiredOffers(expired)
            log.info("Reassigned {} expired ride dispatch offer(s)", expired.size)
        }

        // Real Kakao T 예약 호출 (scheduled ride booking) -- see
        // RideTripService.activateScheduledDispatch's own doc comment. Same poll tick
        // as the reassignment check above; a real scheduled trip's own lead-time
        // threshold is the actual business timing, not this poll interval.
        val dueScheduled = rideTripService.getDueScheduledTrips()
        if (dueScheduled.isNotEmpty()) {
            rideTripService.activateScheduledDispatch(dueScheduled)
            log.info("Started real dispatch for {} due scheduled ride(s)", dueScheduled.size)
        }
    }
}
