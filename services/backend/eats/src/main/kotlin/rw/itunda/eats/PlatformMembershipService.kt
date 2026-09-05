package rw.itunda.eats

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.PlatformMembership
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.PlatformMembershipRepository
import rw.itunda.core.pricing.ReminderWindows
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

class PlatformMembershipNoAccountException(message: String) : RuntimeException(message)
class InvalidPlatformMembershipDurationException(message: String) : RuntimeException(message)

/**
 * Real Coupang 와우 (Wow)-style unconditional delivery-fee waiver -- see
 * `PlatformMembership.kt`'s own doc comment for the full sourced account and its
 * honest distinction from `EatsMembershipService` (item 102).
 */
@Service
class PlatformMembershipService(
    private val platformMembershipRepository: PlatformMembershipRepository,
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
) {
    companion object {
        // Real flat-fee tiers, same model EatsMembershipService.MEMBERSHIP_TIERS
        // already established. Priced above Eats Club since this is the strictly
        // broader real guarantee (every restaurant, not just participating ones).
        val MEMBERSHIP_TIERS: Map<Int, BigDecimal> = mapOf(
            30 to BigDecimal("2500"),
            90 to BigDecimal("6500"),
        )

        // Consolidated 2026-09-06 into core/pricing/ReminderWindows -- see its own doc comment.
        val REMINDER_WINDOW: Duration = ReminderWindows.PRE_EXPIRY_REMINDER_WINDOW
    }

    @Transactional
    fun subscribe(userId: String, days: Int): PlatformMembership {
        val price = MEMBERSHIP_TIERS[days]
            ?: throw InvalidPlatformMembershipDurationException("Choose a real membership duration -- ${MEMBERSHIP_TIERS.keys.sorted().joinToString()} days")

        val account = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw PlatformMembershipNoAccountException("No account found for this account")

        ledgerService.postLedgerTransaction(
            account.currency,
            listOf(
                LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, price, "Platform membership for $days days"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, price, "Platform membership fee"),
            ),
        )

        val existing = platformMembershipRepository.findByUserId(userId)
        val now = Instant.now()
        val currentActiveUntil = existing?.activeUntil?.takeIf { it.isAfter(now) } ?: now
        val membership = existing ?: PlatformMembership(id = "platform_membership_${UUID.randomUUID()}", userId = userId, activeUntil = currentActiveUntil)
        membership.activeUntil = currentActiveUntil.plus(Duration.ofDays(days.toLong()))
        membership.updatedAt = now
        // Same "fresh activeUntil earns a fresh reminder" reset EatsMembershipService's
        // own subscribe already applies.
        membership.reminderSentAt = null
        return platformMembershipRepository.save(membership)
    }

    fun getMyMembership(userId: String): PlatformMembership? = platformMembershipRepository.findByUserId(userId)

    fun hasActiveMembership(userId: String): Boolean {
        val membership = platformMembershipRepository.findByUserId(userId) ?: return false
        return membership.activeUntil.isAfter(Instant.now())
    }

    // Real "date field with no reminder" gap -- see PlatformMembership.reminderSentAt's
    // own doc comment. Same shape as EatsMembershipService.getMembershipsDueForExpiryReminder.
    fun getMembershipsDueForExpiryReminder(): List<PlatformMembership> {
        val now = Instant.now()
        val cutoff = now.plus(REMINDER_WINDOW)
        return platformMembershipRepository.findByReminderSentAtIsNull()
            .filter { it.activeUntil.isAfter(now) && !it.activeUntil.isAfter(cutoff) }
    }

    /** Same real per-item re-check-before-send discipline
     * EatsMembershipService.sendExpiryReminder's own doc comment establishes. */
    @Transactional
    fun sendExpiryReminder(membershipId: String) {
        val membership = platformMembershipRepository.findById(membershipId).orElse(null) ?: return
        val now = Instant.now()
        if (membership.reminderSentAt != null || !membership.activeUntil.isAfter(now)) return

        val title = "Your itunda membership is expiring soon"
        val body = "Your unconditional free-delivery membership ends on ${membership.activeUntil}. Renew before then to keep free delivery on every order."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = membership.userId, type = "PLATFORM_MEMBERSHIP_EXPIRING_SOON",
                title = title, body = body, isRead = false, createdAt = now, dataJson = "{\"membershipId\":\"${membership.id}\"}",
            ),
        )
        pushNotificationService.sendToUser(membership.userId, title, body, mapOf("membershipId" to membership.id))
        membership.reminderSentAt = now
        platformMembershipRepository.save(membership)
    }
}
