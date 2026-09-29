package rw.itunda.marketplace

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real no-show detection for `VehicleInspectionBooking` -- see
 * `VehicleInspectionStatus.NO_SHOW`'s own doc comment for the full real bug account:
 * `VehicleInspectionService.cancelInspection` has no time-based check at all, so
 * without this scheduler a buyer could let a mechanic travel to/perform the real
 * inspection and then cancel arbitrarily late for a full refund, leaving the mechanic
 * unpaid for real committed time. `MerchantBooking`/`BookingDeposit` already close this
 * exact gap for its own sibling 100%-prepay-to-book feature via
 * `BookingNoShowScheduler`; this ports the identical real forfeit-to-provider poll to
 * this structurally identical escrow. Same "poll for due rows read-only, resolve each
 * real row inside its own per-item @Transactional method, one bad row never blocks the
 * sweep for every other real due row" shape
 * `MarketplaceEscrowAutoReleaseScheduler`/`GiftVoucherExpiryScheduler` already
 * establish -- deliberately never a single batch-@Transactional loop.
 */
@Component
class VehicleInspectionNoShowScheduler(private val vehicleInspectionService: VehicleInspectionService) {
    private val log = LoggerFactory.getLogger(VehicleInspectionNoShowScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        val due = vehicleInspectionService.findDueNoShows()
        for (booking in due) {
            try {
                val resolved = vehicleInspectionService.processNoShow(booking.id)
                if (resolved != null) {
                    log.info("Marked vehicle inspection booking {} as a no-show, fee forfeited to mechanic", booking.id)
                }
            } catch (e: Exception) {
                log.error("Vehicle inspection no-show processing failed for {}", booking.id, e)
            }
        }
    }
}
