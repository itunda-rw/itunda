package rw.itunda.rideshare

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real scheduled force-end for a `ParkingSession` abandoned past
 * `ParkingSession.MAX_SESSION_DURATION` -- see that constant's own doc comment for the
 * sourced Citi Bike (NYC) 24-hour abandoned-ride account this reuses. Without this
 * scheduler, `ParkingService.startSession`/`endSession` had a real gap identical to the
 * one `BikeRentalAbandonedSessionScheduler`'s own doc comment already establishes for
 * bikes: a renter whose app crashed or who simply never came back left the parking spot
 * permanently `available = false` and the owner permanently unpaid for it -- there was
 * no forfeit/timeout counterpart to the renter-triggered `endSession` at all. Same
 * "poll for due rows read-only, resolve each real row inside its own per-item
 * @Transactional method, one bad row never blocks the sweep for every other real due
 * row" shape `BikeRentalAbandonedSessionScheduler`/`MarketplaceEscrowAutoReleaseScheduler`
 * already establish -- deliberately never a single batch-@Transactional loop.
 */
@Component
class ParkingAbandonedSessionScheduler(private val parkingService: ParkingService) {
    private val log = LoggerFactory.getLogger(ParkingAbandonedSessionScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        val due = parkingService.getAbandonedSessions()
        for (session in due) {
            try {
                val resolved = parkingService.forceEndAbandonedSession(session.id)
                if (resolved != null) {
                    log.info("Force-ended abandoned parking session {} after exceeding the max session window", session.id)
                }
            } catch (e: Exception) {
                log.error("Parking abandoned-session force-end failed for {}", session.id, e)
            }
        }
    }
}
