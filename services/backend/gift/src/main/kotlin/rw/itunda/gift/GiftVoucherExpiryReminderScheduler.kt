package rw.itunda.gift

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real KakaoTalk 기프티콘 사용기한 임박 알림 (expiry-reminder push) -- see
 * GiftVoucherService.getVouchersDueForExpiryReminder/sendExpiryReminder's own doc
 * comments for the real sourcing. Same real "demo-speed poll, real business condition"
 * convention InsurancePolicyRenewalReminderScheduler/CertificateRenewalReminderScheduler's
 * own doc comments already establish -- a separate `@Component` scheduler calling a
 * per-voucher `@Transactional` method, never a batch-transactional loop, avoiding by
 * construction the self-invocation/transaction-poisoning pitfall this codebase has
 * already found and fixed multiple times elsewhere. `processDue` is also exposed as a
 * manually-triggerable endpoint for verifying a real reminder window without waiting
 * real wall-clock days for one to actually arrive.
 *
 * Real fix (found via project_itunda_zero_test_coverage_sweep's scheduler audit,
 * 2026-09-05): this doc comment's own "avoids the transaction-poisoning pitfall"
 * claim addresses a DIFFERENT concern (self-invocation bypassing the Spring proxy)
 * than the one that actually applied here -- `sendExpiryReminder` had no try/catch of
 * its own around its messaging/repository calls, and this loop had none either, so a
 * single bad voucher would still throw uncaught and stop reminders for every OTHER
 * real due voucher in the same tick. Per-voucher try/catch closes it.
 */
@Component
class GiftVoucherExpiryReminderScheduler(private val giftVoucherService: GiftVoucherService) {
    private val log = LoggerFactory.getLogger(GiftVoucherExpiryReminderScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        processDue()
    }

    fun processDue(): Int {
        val due = giftVoucherService.getVouchersDueForExpiryReminder()
        for (voucher in due) {
            try {
                giftVoucherService.sendExpiryReminder(voucher.id)
                log.info("Sent expiry reminder for gift voucher {}", voucher.id)
            } catch (e: Exception) {
                log.error("Gift voucher expiry reminder failed for {}", voucher.id, e)
            }
        }
        return due.size
    }
}
