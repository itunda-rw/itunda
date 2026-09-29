package rw.itunda.marketplace

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real pre-auto-release reminder to the buyer -- see
 * MarketplaceService.getEscrowsDueForAutoReleaseReminder/sendAutoReleaseReminder's own
 * doc comments. Same "poll for due rows, act, done" shape as
 * MarketplaceEscrowAutoReleaseScheduler/GiftVoucherExpiryReminderScheduler, with a
 * per-escrow try/catch from the start so one bad row can't block the sweep for every
 * other real due escrow in the same tick (the exact fix those two schedulers' own doc
 * comments already found necessary for this identical scheduler shape). `processDue`
 * is also exposed for verifying a real reminder window without waiting real wall-clock
 * days.
 */
@Component
class MarketplaceEscrowAutoReleaseReminderScheduler(private val marketplaceService: MarketplaceService) {
    private val log = LoggerFactory.getLogger(MarketplaceEscrowAutoReleaseReminderScheduler::class.java)

    @Scheduled(fixedDelay = 30000)
    fun run() {
        processDue()
    }

    fun processDue(): Int {
        val due = marketplaceService.getEscrowsDueForAutoReleaseReminder()
        for (escrow in due) {
            try {
                marketplaceService.sendAutoReleaseReminder(escrow.id)
                log.info("Sent auto-release reminder for marketplace escrow {}", escrow.id)
            } catch (e: Exception) {
                log.error("Marketplace escrow auto-release reminder failed for {}", escrow.id, e)
            }
        }
        return due.size
    }
}
