package rw.itunda.certificate

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real Korean electronic-certificate 갱신 안내 (renewal notice) -- see
 * CertificateService.getCertificatesDueForRenewalReminder/sendRenewalReminder's own doc
 * comments for the real sourcing (gpki.go.kr/crosscert.com's own published 60-day
 * renewal-window practice, the real regulatory category Toss Certificate itself
 * operates under). Same real "demo-speed poll, real business condition" convention
 * SavingsMaturityReminderScheduler/InsurancePolicyRenewalReminderScheduler's own doc
 * comments already establish -- a separate `@Component` scheduler calling a
 * per-certificate `@Transactional` method, never a batch-transactional loop, avoiding
 * by construction the self-invocation/transaction-poisoning pitfall this codebase has
 * already found and fixed multiple times elsewhere. `processDue` is also exposed as a
 * manually-triggerable endpoint for verifying a real renewal window without waiting
 * real wall-clock days for one to actually arrive.
 *
 * Real fix (found via project_itunda_zero_test_coverage_sweep's scheduler audit,
 * 2026-09-05): this doc comment's own "avoids the transaction-poisoning pitfall"
 * claim addresses a DIFFERENT concern (self-invocation bypassing the Spring proxy)
 * than the one that actually applied -- `sendRenewalReminder` had no try/catch of its
 * own, and this loop had none either, so a single bad certificate would still throw
 * uncaught and stop reminders for every OTHER real due certificate in the same tick.
 * Per-certificate try/catch closes it.
 */
@Component
class CertificateRenewalReminderScheduler(private val certificateService: CertificateService) {
    private val log = LoggerFactory.getLogger(CertificateRenewalReminderScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        processDue()
    }

    fun processDue(): Int {
        val due = certificateService.getCertificatesDueForRenewalReminder()
        for (cert in due) {
            try {
                certificateService.sendRenewalReminder(cert.id)
                log.info("Sent renewal reminder for certificate {}", cert.id)
            } catch (e: Exception) {
                log.error("Certificate renewal reminder failed for {}", cert.id, e)
            }
        }
        return due.size
    }
}
