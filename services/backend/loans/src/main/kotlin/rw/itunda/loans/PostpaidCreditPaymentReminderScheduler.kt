package rw.itunda.loans

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real Naver Pay/Kakao Pay 후불결제 payment-due-soon push -- see
 * PostpaidCreditService.getLinesDueSoonForPaymentReminder/sendPaymentReminder's own doc
 * comments for the full real sourcing. Itunda's postpaid credit line already had a real
 * cycleDueAt and a real AFTER-the-fact punishment for missing it
 * (PostpaidCreditAccrualScheduler's own late-fee accrual), but zero reminder BEFORE it
 * lapsed -- the same gap shape this codebase's Section 152-155 sweep already found and
 * fixed for InsurancePolicy.endDate/Certificate.expiresAt/GiftVoucher.expiresAt/
 * MerchantCoupon.expiresAt.
 *
 * Same real "demo-speed poll, real business condition" convention every other reminder
 * scheduler in this codebase already establishes (MerchantCouponExpiryReminderScheduler,
 * CertificateRenewalReminderScheduler, GiftVoucherExpiryReminderScheduler,
 * InsurancePolicyRenewalReminderScheduler, SavingsMaturityReminderScheduler) -- a separate
 * `@Component` scheduler calling a per-line `@Transactional` method that re-checks state
 * right before sending, never a batch-transactional loop, avoiding by construction the
 * self-invocation/transaction-poisoning pitfall this codebase has already found and fixed
 * multiple times elsewhere. `processDue` is also exposed as a manually-triggerable
 * endpoint (LoansController.processPostpaidCreditPaymentReminders) so a real reminder
 * window can be verified without waiting real wall-clock days for one to arrive.
 *
 * Real fix (found via project_itunda_zero_test_coverage_sweep's scheduler audit,
 * 2026-09-05): the same "avoids the transaction-poisoning pitfall" claim above
 * addressed a DIFFERENT concern (self-invocation) than the one that actually
 * applied -- `sendPaymentReminder` had no try/catch of its own, and this loop had
 * none either. Per-line try/catch closes it.
 */
@Component
class PostpaidCreditPaymentReminderScheduler(private val postpaidCreditService: PostpaidCreditService) {
    private val log = LoggerFactory.getLogger(PostpaidCreditPaymentReminderScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        processDue()
    }

    fun processDue(): Int {
        val due = postpaidCreditService.getLinesDueSoonForPaymentReminder()
        for (line in due) {
            try {
                postpaidCreditService.sendPaymentReminder(line.id)
                log.info("Sent payment-due-soon reminder for postpaid credit line {} (due {})", line.id, line.cycleDueAt)
            } catch (e: Exception) {
                log.error("Postpaid credit payment reminder failed for {}", line.id, e)
            }
        }
        return due.size
    }
}
