package rw.itunda.rideshare

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real scheduled force-end for a `BikeRentalSession` abandoned past
 * `BikeRentalSession.MAX_RENTAL_DURATION` -- see that constant's own doc comment for the
 * sourced Citi Bike (NYC) 24-hour abandoned-ride account this adapts. Without this
 * scheduler, `BikeRentalService.startRental`/`endRental` had a real gap: a rider whose
 * app crashed or who simply never came back left the bike permanently `available =
 * false` and the owner permanently unpaid for it -- there was no forfeit/timeout
 * counterpart to the rider-triggered `endRental` at all, the same shape
 * `VehicleInspectionNoShowScheduler`'s own doc comment already establishes for its own
 * sibling gap. Same "poll for due rows read-only, resolve each real row inside its own
 * per-item @Transactional method, one bad row never blocks the sweep for every other
 * real due row" shape `MarketplaceEscrowAutoReleaseScheduler`/`GiftVoucherExpiryScheduler`
 * already establish -- deliberately never a single batch-@Transactional loop.
 */
@Component
class BikeRentalAbandonedSessionScheduler(private val bikeRentalService: BikeRentalService) {
    private val log = LoggerFactory.getLogger(BikeRentalAbandonedSessionScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        val due = bikeRentalService.getAbandonedRentals()
        for (session in due) {
            try {
                val resolved = bikeRentalService.forceEndAbandonedRental(session.id)
                if (resolved != null) {
                    log.info("Force-ended abandoned bike rental session {} after exceeding the max rental window", session.id)
                }
            } catch (e: Exception) {
                log.error("Bike rental abandoned-session force-end failed for {}", session.id, e)
            }
        }
    }
}
