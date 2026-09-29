package rw.itunda.gift

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real scheduled auto-refund for unclaimed gifts -- same "poll for expired rows, act,
 * done" shape as `DispatchOfferScheduler`/`WebhookRetryScheduler`. The *business*
 * window is real ([rw.itunda.core.domain.Gift.EXPIRY], 7 days); the poll interval
 * below is demo-speed on purpose, matching every other scheduler in this codebase.
 *
 * Real fix (found via project_itunda_zero_test_coverage_sweep's scheduler audit,
 * 2026-09-05): `expireGift` calls `ledgerService.postLedgerTransaction` (the real
 * refund) with no try/catch of its own, and this sweep had none either -- a single
 * bad gift (a since-deleted sender account, a transient ledger error) would throw
 * uncaught and silently stop refunding for every OTHER real expired gift in the same
 * tick. Per-gift try/catch closes it.
 */
@Component
class GiftExpiryScheduler(private val giftService: GiftService) {
    private val log = LoggerFactory.getLogger(GiftExpiryScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        val expired = giftService.getExpiredPendingGifts()
        var refunded = 0
        for (gift in expired) {
            try {
                giftService.expireGift(gift)
                refunded++
            } catch (e: Exception) {
                log.error("Gift expiry refund failed for {}", gift.id, e)
            }
        }
        if (refunded > 0) {
            log.info("Refunded {} expired unclaimed gift(s)", refunded)
        }
    }
}
