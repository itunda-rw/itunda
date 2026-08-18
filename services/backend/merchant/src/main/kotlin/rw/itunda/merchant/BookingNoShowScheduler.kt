package rw.itunda.merchant

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

// Real no-show detection poll -- see MerchantBookingService.processNoShow's own doc
// comment. Same demo-speed-poll convention as GiftExpiryScheduler/AutoSaveScheduler
// (a real business-hours-scale check, polled fast enough to verify without waiting real
// wall-clock hours). Same "poll for due rows read-only, resolve each real row inside
// its own per-item @Transactional method, one bad row never blocks the sweep for every
// other real due row" shape BikeRentalAbandonedSessionScheduler/
// ParkingAbandonedSessionScheduler/VehicleInspectionNoShowScheduler/
// EatsOrderAbandonedDeliveryScheduler already establish -- fixed 2026-08-18 to actually
// match that shape itself (see MerchantBookingService.processNoShow's own doc comment
// for the real batch-transaction-poisoning bug this closes).
@Component
class BookingNoShowScheduler(private val merchantBookingService: MerchantBookingService) {
    private val log = LoggerFactory.getLogger(BookingNoShowScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        val due = merchantBookingService.getDueNoShows()
        for (booking in due) {
            try {
                val resolved = merchantBookingService.processNoShow(booking.id)
                if (resolved != null) {
                    log.info("Marked booking {} as no-show", booking.id)
                }
            } catch (e: Exception) {
                log.error("Booking no-show processing failed for {}", booking.id, e)
            }
        }
    }
}
