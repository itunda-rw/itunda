package rw.itunda.eats

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real membership expiry-reminder push -- see EatsMembership.reminderSentAt/
 * PlatformMembership.reminderSentAt's own doc comments for the real sourcing (Baemin
 * Club's own real pre-expiry push behavior). Same real "demo-speed poll, real business
 * condition" convention MerchantCouponExpiryReminderScheduler/
 * CertificateRenewalReminderScheduler's own doc comments already establish -- a
 * separate `@Component` scheduler calling a per-membership `@Transactional` method,
 * never a batch-transactional loop, avoiding by construction the self-invocation/
 * transaction-poisoning pitfall this codebase has already found and fixed multiple
 * times elsewhere.
 *
 * Covers both real membership types in one scheduler (rather than two near-identical
 * files) since `EatsMembershipService`/`PlatformMembershipService` are genuine sibling
 * services with the exact same shape -- each still gets its own independent due-list
 * scan and its own independent per-item `@Transactional` send, so a failure/slowdown in
 * one type's loop can't affect the other's.
 *
 * `processDue` is also exposed as a manually-triggerable endpoint
 * (`EatsController.processMembershipExpiryReminders`) for verifying a real reminder
 * window without waiting real wall-clock days for one to actually arrive.
 */
@Component
class MembershipExpiryReminderScheduler(
    private val eatsMembershipService: EatsMembershipService,
    private val platformMembershipService: PlatformMembershipService,
) {
    private val log = LoggerFactory.getLogger(MembershipExpiryReminderScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        processDue()
    }

    fun processDue(): Int {
        val dueEatsMemberships = eatsMembershipService.getMembershipsDueForExpiryReminder()
        for (membership in dueEatsMemberships) {
            eatsMembershipService.sendExpiryReminder(membership.id)
            log.info("Sent expiry reminder for Eats Club membership {}", membership.id)
        }

        val duePlatformMemberships = platformMembershipService.getMembershipsDueForExpiryReminder()
        for (membership in duePlatformMemberships) {
            platformMembershipService.sendExpiryReminder(membership.id)
            log.info("Sent expiry reminder for platform membership {}", membership.id)
        }

        return dueEatsMemberships.size + duePlatformMemberships.size
    }
}
