package rw.itunda.savings

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real KakaoBank 모임통장 automated dues collection -- see docs/DESIGN_REFERENCES.md's
 * Group Account row ("automated dues collection with per-member payment-day rules...
 * playful reminder cards") and GroupAccountService.sendAutomaticDuesReminders's own doc
 * comment. Same demo-speed-poll / real-business-cadence split as AutoSaveScheduler: the
 * *business* rule is real (once per real calendar month, per unpaid member, via the
 * per-cycle dedupe table), the *poll* interval below is just how often that real
 * condition gets checked.
 */
@Component
class GroupAccountDuesReminderScheduler(private val groupAccountService: GroupAccountService) {
    private val log = LoggerFactory.getLogger(GroupAccountDuesReminderScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        val remindedCount = groupAccountService.sendAutomaticDuesReminders()
        if (remindedCount > 0) {
            log.info("Sent {} real group account dues reminder(s)", remindedCount)
        }
    }
}
