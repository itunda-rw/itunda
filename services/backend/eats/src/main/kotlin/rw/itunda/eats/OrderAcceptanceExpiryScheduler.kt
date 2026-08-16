package rw.itunda.eats

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real scheduled order-acceptance timeout (2026-08-16) -- see
 * EatsOrderService.ORDER_ACCEPTANCE_TIMEOUT/expireUnacceptedOrder's own doc comments
 * for the full account. Same real "poll interval is demo-speed, business window is
 * real" convention DispatchOfferScheduler's own doc comment already establishes --
 * checking every 30 seconds for orders whose real 10-minute acceptance window has
 * elapsed is cheap and correct.
 */
@Component
class OrderAcceptanceExpiryScheduler(private val eatsOrderService: EatsOrderService) {
    private val log = LoggerFactory.getLogger(OrderAcceptanceExpiryScheduler::class.java)

    @Scheduled(fixedDelay = 30000)
    fun run() {
        val expired = eatsOrderService.getExpiredUnacceptedOrders()
        if (expired.isEmpty()) return
        expired.forEach { eatsOrderService.expireUnacceptedOrder(it) }
        log.info("Expired {} unaccepted order(s)", expired.size)
    }
}
