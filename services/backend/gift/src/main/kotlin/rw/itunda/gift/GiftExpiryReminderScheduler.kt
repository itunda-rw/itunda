package rw.itunda.gift

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real KakaoTalk-style money-envelope expiry-reminder push -- see
 * GiftService.getGiftsDueForExpiryReminder/sendExpiryReminder's own doc comments for
 * the real sourcing. Same real "demo-speed poll, real business condition" convention
 * GiftVoucherExpiryReminderScheduler's own doc comment already establishes -- a
 * separate `@Component` scheduler calling a per-gift `@Transactional` method, never a
 * batch-transactional loop, with a per-gift try/catch so one bad gift can't stop
 * reminders for every other due gift in the same tick (the exact fix
 * GiftVoucherExpiryReminderScheduler/GiftExpiryScheduler's own doc comments already
 * found necessary for this identical scheduler shape). `processDue` is also exposed
 * for verifying a real reminder window without waiting real wall-clock days.
 */
@Component
class GiftExpiryReminderScheduler(private val giftService: GiftService) {
    private val log = LoggerFactory.getLogger(GiftExpiryReminderScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        processDue()
    }

    fun processDue(): Int {
        val due = giftService.getGiftsDueForExpiryReminder()
        for (gift in due) {
            try {
                giftService.sendExpiryReminder(gift.id)
                log.info("Sent expiry reminder for gift {}", gift.id)
            } catch (e: Exception) {
                log.error("Gift expiry reminder failed for {}", gift.id, e)
            }
        }
        return due.size
    }
}
