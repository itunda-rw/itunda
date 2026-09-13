package rw.itunda.merchant

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real pre-expiry reminder for dormant merchant loyalty points -- see
 * MerchantLoyaltyPointsService.getAccountsDueForExpiryReminder/sendExpiryReminder's own
 * doc comments. Same "poll for due rows, act per row, done" shape as
 * MerchantLoyaltyPointsExpiryScheduler/MerchantCouponExpiryReminderScheduler, with a
 * per-account try/catch from the start so one bad row can never block the sweep for
 * every other real due reminder in the same poll -- the exact previously-recurring
 * "scheduler transaction-poisoning" bug class this codebase's own Sections 115-181
 * already found and fixed nine times. `processDue` is also exposed for verifying a
 * real reminder window without waiting real wall-clock days.
 */
@Component
class MerchantLoyaltyPointsExpiryReminderScheduler(private val merchantLoyaltyPointsService: MerchantLoyaltyPointsService) {
    private val log = LoggerFactory.getLogger(MerchantLoyaltyPointsExpiryReminderScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        processDue()
    }

    fun processDue(): Int {
        val due = merchantLoyaltyPointsService.getAccountsDueForExpiryReminder()
        for (account in due) {
            try {
                merchantLoyaltyPointsService.sendExpiryReminder(account.id)
                log.info("Sent expiry reminder for merchant loyalty account {}", account.id)
            } catch (e: Exception) {
                log.error("Merchant loyalty points expiry reminder failed for {}", account.id, e)
            }
        }
        return due.size
    }
}
