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
            certificateService.sendRenewalReminder(cert.id)
            log.info("Sent renewal reminder for certificate {}", cert.id)
        }
        return due.size
    }
}
