package rw.itunda.gift

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real scheduled partial-refund for unredeemed gift vouchers -- same "poll for expired
 * rows, act, done" shape as [GiftExpiryScheduler]. The *business* window is real
 * ([rw.itunda.core.domain.GiftVoucher.DEFAULT_VALIDITY], 180 days); the poll interval
 * below is demo-speed on purpose, matching every other scheduler in this codebase.
 */
@Component
class GiftVoucherExpiryScheduler(private val giftVoucherService: GiftVoucherService) {
    private val log = LoggerFactory.getLogger(GiftVoucherExpiryScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        val expired = giftVoucherService.getExpiredActiveVouchers()
        if (expired.isEmpty()) return
        expired.forEach { giftVoucherService.expireVoucher(it) }
        log.info("Partially refunded {} expired unredeemed gift voucher(s)", expired.size)
    }
}
