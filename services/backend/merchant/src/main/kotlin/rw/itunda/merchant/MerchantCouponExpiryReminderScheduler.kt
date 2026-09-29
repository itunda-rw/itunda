package rw.itunda.merchant

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real merchant coupon expiry-reminder push -- see
 * MerchantCouponService.getCouponsDueForExpiryReminder/sendExpiryReminder's own doc
 * comments for the real sourcing. Same real "demo-speed poll, real business condition"
 * convention GiftVoucherExpiryReminderScheduler/CertificateRenewalReminderScheduler's own
 * doc comments already establish -- a separate `@Component` scheduler calling a
 * per-coupon `@Transactional` method, never a batch-transactional loop, avoiding by
 * construction the self-invocation/transaction-poisoning pitfall this codebase has
 * already found and fixed multiple times elsewhere. `processDue` is also exposed as a
 * manually-triggerable endpoint for verifying a real reminder window without waiting real
 * wall-clock days for one to actually arrive.
 *
 * Real fix (found via project_itunda_zero_test_coverage_sweep's scheduler audit,
 * 2026-09-05): the same "avoids the transaction-poisoning pitfall" claim above
 * addressed a DIFFERENT concern (self-invocation) than the one that actually
 * applied -- `sendExpiryReminder` had no try/catch of its own, and this loop had none
 * either. Per-coupon try/catch closes it.
 */
@Component
class MerchantCouponExpiryReminderScheduler(private val merchantCouponService: MerchantCouponService) {
    private val log = LoggerFactory.getLogger(MerchantCouponExpiryReminderScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        processDue()
    }

    fun processDue(): Int {
        val due = merchantCouponService.getCouponsDueForExpiryReminder()
        for (coupon in due) {
            try {
                merchantCouponService.sendExpiryReminder(coupon.id)
                log.info("Sent expiry reminder for merchant coupon {}", coupon.id)
            } catch (e: Exception) {
                log.error("Merchant coupon expiry reminder failed for {}", coupon.id, e)
            }
        }
        return due.size
    }
}
