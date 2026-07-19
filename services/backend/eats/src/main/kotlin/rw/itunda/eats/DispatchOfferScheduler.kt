package rw.itunda.eats

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real scheduled reassignment for automatic dispatch (2026-07-20) -- see
 * EatsOrderService.getExpiredOffers/reassignExpiredOffer's own doc comments for the full
 * account. The *business* window is real (EatsOrderService.OFFER_WINDOW, 90 seconds).
 * The *poll* interval below is demo-speed, same convention as AutoSaveScheduler/
 * OutboxRelay's own fixedDelay: checking every 10 seconds for offers whose real
 * 90-second window has elapsed is cheap and correct, and keeps a real buyer from
 * waiting much longer than the window itself before reassignment kicks in.
 */
@Component
class DispatchOfferScheduler(private val eatsOrderService: EatsOrderService) {
    private val log = LoggerFactory.getLogger(DispatchOfferScheduler::class.java)

    @Scheduled(fixedDelay = 10000)
    fun run() {
        val expired = eatsOrderService.getExpiredOffers()
        for (order in expired) {
            eatsOrderService.reassignExpiredOffer(order)
            log.info("Reassigned expired dispatch offer for order {}", order.id)
        }
    }
}
