package rw.itunda.eats

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real scheduled reassignment for automatic dispatch (2026-07-20) -- see
 * EatsOrderService.getExpiredOffers/reassignExpiredOffers's own doc comments for the
 * full account. The *business* window is real (EatsOrderService.OFFER_WINDOW, 90
 * seconds). The *poll* interval below is demo-speed, same convention as
 * AutoSaveScheduler/OutboxRelay's own fixedDelay: checking every 10 seconds for offers
 * whose real 90-second window has elapsed is cheap and correct, and keeps a real buyer
 * from waiting much longer than the window itself before reassignment kicks in.
 *
 * Batches the whole tick into one real `reassignExpiredOffers` call (2026-07-20
 * performance sweep) rather than one call per order -- see that method's own doc
 * comment for why looping individual calls here was real, avoidable repeated work.
 */
@Component
class DispatchOfferScheduler(private val eatsOrderService: EatsOrderService) {
    private val log = LoggerFactory.getLogger(DispatchOfferScheduler::class.java)

    @Scheduled(fixedDelay = 10000)
    fun run() {
        val expired = eatsOrderService.getExpiredOffers()
        if (expired.isEmpty()) return
        eatsOrderService.reassignExpiredOffers(expired)
        log.info("Reassigned {} expired dispatch offer(s)", expired.size)
    }
}
