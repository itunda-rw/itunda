package rw.itunda.insurance

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real Kakao Pay/Toss Insurance 갱신 안내 (renewal notice) service (2026-08-17) -- see
 * InsuranceService.getPoliciesDueForRenewalReminder/sendRenewalReminder's own doc
 * comments. Same real "demo-speed poll, real business condition" convention
 * SavingsMaturityReminderScheduler's own doc comment already establishes; `processDue`
 * is also exposed as a manually-triggerable endpoint for verifying a real renewal
 * window without waiting real wall-clock days for one to actually arrive.
 *
 * Real gap found 2026-09-06 (concurrency-audit continuation, same "scheduler
 * transaction-poisoning" class already fixed on several other schedulers): this
 * loop had zero per-policy try/catch, so a genuine failure sending ONE renewal
 * reminder used to propagate uncaught and silently skip every OTHER due policy in
 * the same tick.
 */
@Component
class InsurancePolicyRenewalReminderScheduler(private val insuranceService: InsuranceService) {
    private val log = LoggerFactory.getLogger(InsurancePolicyRenewalReminderScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        processDue()
    }

    fun processDue(): Int {
        val due = insuranceService.getPoliciesDueForRenewalReminder()
        var sentCount = 0
        for (policy in due) {
            try {
                insuranceService.sendRenewalReminder(policy.id)
                log.info("Sent renewal reminder for insurance policy {}", policy.id)
                sentCount++
            } catch (e: Exception) {
                log.error("Insurance renewal reminder failed for policy {}", policy.id, e)
            }
        }
        return sentCount
    }
}
