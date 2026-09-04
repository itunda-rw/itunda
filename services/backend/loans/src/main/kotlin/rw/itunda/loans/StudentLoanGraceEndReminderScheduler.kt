package rw.itunda.loans

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real student-loan-servicer "your grace period is ending soon" push -- see
 * StudentLoanService.getLoansDueSoonForGraceEndReminder/sendGraceEndReminder's own doc
 * comments for the full real sourcing. Itunda's student loan already had a real
 * `graceEndsAt` and a real status flip the instant it elapsed
 * (StudentLoanGracePeriodScheduler's own sibling scheduler), but zero reminder BEFORE it
 * lapsed -- the same gap shape this codebase's Section 152-156 sweep already found and
 * fixed for InsurancePolicy.endDate/Certificate.expiresAt/GiftVoucher.expiresAt/
 * MerchantCoupon.expiresAt/PostpaidCreditLine.cycleDueAt.
 *
 * Same real "demo-speed poll, real business condition" convention every other reminder
 * scheduler in this codebase already establishes (PostpaidCreditPaymentReminderScheduler,
 * MerchantCouponExpiryReminderScheduler, CertificateRenewalReminderScheduler,
 * GiftVoucherExpiryReminderScheduler, InsurancePolicyRenewalReminderScheduler,
 * SavingsMaturityReminderScheduler) -- a separate `@Component` scheduler calling a
 * per-loan `@Transactional` method that re-checks state right before sending, never a
 * batch-transactional loop, avoiding by construction the self-invocation/transaction-
 * poisoning pitfall this codebase has already found and fixed multiple times elsewhere.
 * `processDue` is also exposed as a manually-triggerable endpoint
 * (StudentLoanController.processGraceEndReminders) so a real reminder window can be
 * verified without waiting real wall-clock days for one to arrive.
 *
 * Real fix (found via project_itunda_zero_test_coverage_sweep's scheduler audit,
 * 2026-09-05): the same "avoids the transaction-poisoning pitfall" claim above
 * addressed a DIFFERENT concern (self-invocation) than the one that actually
 * applied -- `sendGraceEndReminder` had no try/catch of its own, and this loop had
 * none either. Per-loan try/catch closes it.
 */
@Component
class StudentLoanGraceEndReminderScheduler(private val studentLoanService: StudentLoanService) {
    private val log = LoggerFactory.getLogger(StudentLoanGraceEndReminderScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        processDue()
    }

    fun processDue(): Int {
        val due = studentLoanService.getLoansDueSoonForGraceEndReminder()
        for (loan in due) {
            try {
                studentLoanService.sendGraceEndReminder(loan.id)
                log.info("Sent grace-period-ending-soon reminder for student loan {} (graceEndsAt {})", loan.id, loan.graceEndsAt)
            } catch (e: Exception) {
                log.error("Student loan grace-end reminder failed for {}", loan.id, e)
            }
        }
        return due.size
    }
}
