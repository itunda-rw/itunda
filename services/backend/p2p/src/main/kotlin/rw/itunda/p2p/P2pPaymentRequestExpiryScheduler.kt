package rw.itunda.p2p

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real proactive P2P payment-request expiry (Bank product-completeness pass, cycle 2,
 * 2026-09-09) -- mirrors HarvestAdvanceOverdueScheduler/VupLoanOverdueScheduler exactly,
 * including the per-item try/catch (the 2026-09-05 scheduler-poisoning fix class: one
 * bad row can't poison the sweep). P2pPaymentRequestStatus.EXPIRED and its expiresAt
 * check were already real code inside P2pService.payRequest, but only ever ran lazily --
 * triggered by a payer attempting to pay an already-expired request. A request nobody
 * ever attempts to pay stayed PENDING indefinitely, including in the requester's own
 * "My Requests" list, long after its real 15-minute expiresAt had passed. The poll
 * interval below is demo-speed, same convention as the sibling schedulers' own doc
 * comments establish -- checking every 60 seconds for requests whose real expiry has
 * already elapsed is cheap and correct; it does not mean requests expire every 60 seconds.
 */
@Component
class P2pPaymentRequestExpiryScheduler(private val p2pService: P2pService) {
    private val log = LoggerFactory.getLogger(P2pPaymentRequestExpiryScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        val due = p2pService.getRequestsDueForExpiryCheck()
        for (request in due) {
            try {
                p2pService.markExpired(request)
                log.info("P2P payment request {} flagged EXPIRED (expiresAt {}, amount {})", request.id, request.expiresAt, request.amount)
            } catch (e: Exception) {
                log.error("P2P payment request expiry flagging failed for {}", request.id, e)
            }
        }
    }
}
