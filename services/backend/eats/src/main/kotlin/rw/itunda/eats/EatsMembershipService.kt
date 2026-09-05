package rw.itunda.eats

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.EatsMembership
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.EatsMembershipRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.pricing.ReminderWindows
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

class EatsMembershipNoAccountException(message: String) : RuntimeException(message)
class InvalidMembershipDurationException(message: String) : RuntimeException(message)

/**
 * Real Baemin Club (배민클럽)-style free-delivery membership -- see `EatsMembership.kt`'s
 * own doc comment for the full sourced account, including the honest "exact pricing
 * unconfirmed, itunda's own scoping choice" reasoning for `MEMBERSHIP_TIERS`.
 */
@Service
class EatsMembershipService(
    private val eatsMembershipRepository: EatsMembershipRepository,
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
) {
    companion object {
        // Real flat-fee tiers, same "pay once, extend, stack" model
        // Listing.BOOST_TIERS/MerchantAd.LOCAL_AD_TIERS already established -- itunda's
        // own honest scoping choice, not a currency-converted reuse of an unconfirmed
        // real Baemin Club price.
        val MEMBERSHIP_TIERS: Map<Int, BigDecimal> = mapOf(
            30 to BigDecimal("1500"),
            90 to BigDecimal("4000"),
        )

        // Consolidated 2026-09-06 into core/pricing/ReminderWindows -- see its own doc comment.
        val REMINDER_WINDOW: Duration = ReminderWindows.PRE_EXPIRY_REMINDER_WINDOW
    }

    @Transactional
    fun subscribe(userId: String, days: Int): EatsMembership {
        val price = MEMBERSHIP_TIERS[days]
            ?: throw InvalidMembershipDurationException("Choose a real membership duration -- ${MEMBERSHIP_TIERS.keys.sorted().joinToString()} days")

        val account = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw EatsMembershipNoAccountException("No account found for this account")

        ledgerService.postLedgerTransaction(
            account.currency,
            listOf(
                LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, price, "Eats membership for $days days"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, price, "Eats membership fee"),
            ),
        )

        val existing = eatsMembershipRepository.findByUserId(userId)
        val now = Instant.now()
        val currentActiveUntil = existing?.activeUntil?.takeIf { it.isAfter(now) } ?: now
        val membership = existing ?: EatsMembership(id = "eats_membership_${UUID.randomUUID()}", userId = userId, activeUntil = currentActiveUntil)
        membership.activeUntil = currentActiveUntil.plus(Duration.ofDays(days.toLong()))
        membership.updatedAt = now
        // A fresh `activeUntil` deserves its own fresh reminder, not silence because an
        // earlier extension already fired one for a now-superseded expiry date.
        membership.reminderSentAt = null
        return eatsMembershipRepository.save(membership)
    }

    fun getMyMembership(userId: String): EatsMembership? = eatsMembershipRepository.findByUserId(userId)

    fun hasActiveMembership(userId: String): Boolean {
        val membership = eatsMembershipRepository.findByUserId(userId) ?: return false
        return membership.activeUntil.isAfter(Instant.now())
    }

    // Real "date field with no reminder" gap -- see EatsMembership.reminderSentAt's own
    // doc comment for the real sourcing. Same shape as
    // MerchantCouponService.getCouponsDueForExpiryReminder: only a membership that's
    // still genuinely active but expiring inside the real window, and hasn't already
    // been reminded for its current `activeUntil`.
    fun getMembershipsDueForExpiryReminder(): List<EatsMembership> {
        val now = Instant.now()
        val cutoff = now.plus(REMINDER_WINDOW)
        return eatsMembershipRepository.findByReminderSentAtIsNull()
            .filter { it.activeUntil.isAfter(now) && !it.activeUntil.isAfter(cutoff) }
    }

    /** One real expiry-reminder notification per membership, called by the scheduler --
     * re-checks `reminderSentAt`/`activeUntil` right before sending so a genuine race
     * (e.g. the user re-subscribing between the scheduler's scan and this call) can't
     * double-fire or fire on an already-lapsed row, same resilience discipline
     * MerchantCouponService.sendExpiryReminder's own doc comment already establishes. */
    @Transactional
    fun sendExpiryReminder(membershipId: String) {
        val membership = eatsMembershipRepository.findById(membershipId).orElse(null) ?: return
        val now = Instant.now()
        if (membership.reminderSentAt != null || !membership.activeUntil.isAfter(now)) return

        val title = "Your Eats Club membership is expiring soon"
        val body = "Your free-delivery membership ends on ${membership.activeUntil}. Renew before then to keep free delivery at participating restaurants."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = membership.userId, type = "EATS_MEMBERSHIP_EXPIRING_SOON",
                title = title, body = body, isRead = false, createdAt = now, dataJson = "{\"membershipId\":\"${membership.id}\"}",
            ),
        )
        pushNotificationService.sendToUser(membership.userId, title, body, mapOf("membershipId" to membership.id))
        membership.reminderSentAt = now
        eatsMembershipRepository.save(membership)
    }
}
