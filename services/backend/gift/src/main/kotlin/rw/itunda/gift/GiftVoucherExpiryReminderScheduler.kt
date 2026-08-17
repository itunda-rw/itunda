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
            giftVoucherService.sendExpiryReminder(voucher.id)
            log.info("Sent expiry reminder for gift voucher {}", voucher.id)
        }
        return due.size
    }
}
