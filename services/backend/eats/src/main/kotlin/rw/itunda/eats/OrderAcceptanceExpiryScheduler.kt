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
 *
 * Real fix (found via project_itunda_zero_test_coverage_sweep's scheduler audit,
 * 2026-09-05): `expireUnacceptedOrder` calls the private `refundAndCancel` helper,
 * which calls `ledgerService.postLedgerTransaction` with no try/catch anywhere in
 * that chain -- a single bad order (a since-deleted restaurant, a transient ledger
 * error) would throw uncaught and silently stop refunding for every OTHER real
 * expired order in the same tick. Per-order try/catch closes it.
 */
@Component
class OrderAcceptanceExpiryScheduler(private val eatsOrderService: EatsOrderService) {
    private val log = LoggerFactory.getLogger(OrderAcceptanceExpiryScheduler::class.java)

    @Scheduled(fixedDelay = 30000)
    fun run() {
        val expired = eatsOrderService.getExpiredUnacceptedOrders()
        var cancelled = 0
        for (order in expired) {
            try {
                eatsOrderService.expireUnacceptedOrder(order)
                cancelled++
            } catch (e: Exception) {
                log.error("Order-acceptance expiry cancellation failed for {}", order.id, e)
            }
        }
        if (cancelled > 0) {
            log.info("Expired {} unaccepted order(s)", cancelled)
        }
    }
}
