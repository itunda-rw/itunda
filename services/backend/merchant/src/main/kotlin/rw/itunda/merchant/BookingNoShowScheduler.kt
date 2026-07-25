package rw.itunda.merchant

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

// Real no-show detection poll -- see MerchantBookingService.processNoShows's own doc
// comment. Same demo-speed-poll convention as GiftExpiryScheduler/AutoSaveScheduler
// (a real business-hours-scale check, polled fast enough to verify without waiting real
// wall-clock hours).
@Component
class BookingNoShowScheduler(private val merchantBookingService: MerchantBookingService) {
    private val log = LoggerFactory.getLogger(BookingNoShowScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        val processed = merchantBookingService.processNoShows()
        if (processed.isNotEmpty()) {
            log.info("Marked {} booking(s) as no-show", processed.size)
        }
    }
}
