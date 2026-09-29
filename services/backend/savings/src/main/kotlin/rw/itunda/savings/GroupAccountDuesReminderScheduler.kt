package rw.itunda.savings

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real KakaoBank 모임통장 automated dues collection -- see docs/DESIGN_REFERENCES.md's
 * Group Account row ("automated dues collection with per-member payment-day rules...
 * playful reminder cards") and GroupAccountService.sendAutomaticDuesRemindersFor's own
 * doc comment. Same demo-speed-poll / real-business-cadence split as AutoSaveScheduler:
 * the *business* rule is real (once per real calendar month, per unpaid member, via the
 * per-cycle dedupe table), the *poll* interval below is just how often that real
 * condition gets checked.
 *
 * Fixed 2026-08-18 (docs/DESIGN_REFERENCES.md Section 180) to actually match the "poll
 * for due rows read-only, resolve each real row inside its own per-item @Transactional
 * method, one bad row never blocks the sweep for every other real due row" shape
 * BikeRentalAbandonedSessionScheduler/BookingNoShowScheduler already establish -- this
 * loop used to live inside a single batch-@Transactional service method instead of
 * here, see GroupAccountService.sendAutomaticDuesRemindersFor's own doc comment for the
 * real bug that closed.
 */
@Component
class GroupAccountDuesReminderScheduler(private val groupAccountService: GroupAccountService) {
    private val log = LoggerFactory.getLogger(GroupAccountDuesReminderScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        val accounts = groupAccountService.getAccountsWithDuesConfigured()
        var remindedCount = 0
        for (account in accounts) {
            try {
                remindedCount += groupAccountService.sendAutomaticDuesRemindersFor(account.id)
            } catch (e: Exception) {
                log.error("Group account dues reminder sweep failed for account {}", account.id, e)
            }
        }
        if (remindedCount > 0) {
            log.info("Sent {} real group account dues reminder(s)", remindedCount)
        }
    }
}
