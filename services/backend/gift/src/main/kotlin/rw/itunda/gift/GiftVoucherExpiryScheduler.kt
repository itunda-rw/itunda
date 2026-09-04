package rw.itunda.gift

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real scheduled partial-refund for unredeemed gift vouchers -- same "poll for expired
 * rows, act, done" shape as [GiftExpiryScheduler]. The *business* window is real
 * ([rw.itunda.core.domain.GiftVoucher.DEFAULT_VALIDITY], 180 days); the poll interval
 * below is demo-speed on purpose, matching every other scheduler in this codebase.
 *
 * Real fix (found via project_itunda_zero_test_coverage_sweep's scheduler audit,
 * 2026-09-05): `expireVoucher` calls `ledgerService.postLedgerTransaction` (the real
 * partial refund) with no try/catch of its own, and this sweep had none either -- a
 * single bad voucher (a since-deleted purchaser account, a transient ledger error)
 * would throw uncaught and silently stop refunding for every OTHER real expired
 * voucher in the same tick. Per-voucher try/catch closes it.
 */
@Component
class GiftVoucherExpiryScheduler(private val giftVoucherService: GiftVoucherService) {
    private val log = LoggerFactory.getLogger(GiftVoucherExpiryScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        val expired = giftVoucherService.getExpiredActiveVouchers()
        var refunded = 0
        for (voucher in expired) {
            try {
                giftVoucherService.expireVoucher(voucher)
                refunded++
            } catch (e: Exception) {
                log.error("Gift voucher expiry refund failed for {}", voucher.id, e)
            }
        }
        if (refunded > 0) {
            log.info("Partially refunded {} expired unredeemed gift voucher(s)", refunded)
        }
    }
}
