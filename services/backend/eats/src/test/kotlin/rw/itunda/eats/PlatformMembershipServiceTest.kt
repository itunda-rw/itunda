package rw.itunda.eats

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import rw.itunda.core.domain.PlatformMembership
import rw.itunda.core.domain.AccountType
import rw.itunda.core.domain.Account
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.PlatformMembershipRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional

/**
 * Real optimistic-lock regression test (found live in a 2026-08-02 audit pass) -- same
 * real check-then-act create-or-extend race EatsMembershipServiceTest's own doc comment
 * names, here for the platform-wide Wow-style membership.
 */
class PlatformMembershipServiceTest : BehaviorSpec({

    Given("a real user with an already-active platform membership") {
        val platformMembershipRepository = mockk<PlatformMembershipRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = PlatformMembershipService(platformMembershipRepository, accountRepository, ledgerService, notificationRepository, pushNotificationService)

        val account = Account(
            id = "account_1", userId = "user_1", accountNumber = "ACC-1", accountName = "Test account",
            type = AccountType.MAIN, balance = BigDecimal("10000"), availableBalance = BigDecimal("10000"),
        )
        val existing = PlatformMembership(id = "platform_membership_1", userId = "user_1", activeUntil = Instant.parse("2026-08-10T00:00:00Z"))
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { platformMembershipRepository.findByUserId("user_1") } returns existing
        val savedSlot = mutableListOf<PlatformMembership>()
        every { platformMembershipRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("subscribing again to extend it") {
            service.subscribe("user_1", 30)

            Then("the exact same row object -- the one carrying the real @Version -- is what gets saved") {
                (savedSlot.first() === existing) shouldBe true
            }
        }
    }

    // Real "date field with no reminder" gap -- see PlatformMembership.reminderSentAt's
    // own doc comment. Same coverage shape EatsMembershipServiceTest's own sibling
    // expiry-reminder test already established.
    Given("real platform memberships at various points in their real expiry-reminder window") {
        val platformMembershipRepository = mockk<PlatformMembershipRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = PlatformMembershipService(platformMembershipRepository, accountRepository, ledgerService, notificationRepository, pushNotificationService)

        fun membershipWith(id: String, activeUntil: Instant, reminderSentAt: Instant? = null) =
            PlatformMembership(id = id, userId = "user_1", activeUntil = activeUntil, reminderSentAt = reminderSentAt)

        When("a real active membership's activeUntil is already inside the 3-day reminder window") {
            val soon = membershipWith("membership_soon", Instant.now().plus(1, ChronoUnit.DAYS))
            every { platformMembershipRepository.findByReminderSentAtIsNull() } returns listOf(soon)

            Then("it is a real due candidate") {
                service.getMembershipsDueForExpiryReminder().map { it.id } shouldBe listOf("membership_soon")
            }
        }

        When("a real active membership's activeUntil is genuinely still outside the reminder window") {
            val far = membershipWith("membership_far", Instant.now().plus(30, ChronoUnit.DAYS))
            every { platformMembershipRepository.findByReminderSentAtIsNull() } returns listOf(far)

            Then("it is real-excluded -- not due yet") {
                service.getMembershipsDueForExpiryReminder() shouldBe emptyList()
            }
        }

        When("a real membership has already lapsed") {
            val lapsed = membershipWith("membership_lapsed", Instant.now().minus(1, ChronoUnit.DAYS))
            every { platformMembershipRepository.findByReminderSentAtIsNull() } returns listOf(lapsed)

            Then("it is real-excluded -- there is nothing left to renew before") {
                service.getMembershipsDueForExpiryReminder() shouldBe emptyList()
            }
        }

        When("sending a real expiry reminder for a due membership") {
            val membership = membershipWith("membership_due", Instant.now().plus(2, ChronoUnit.DAYS))
            every { platformMembershipRepository.findById("membership_due") } returns Optional.of(membership)
            every { platformMembershipRepository.save(any()) } answers { firstArg() }
            every { notificationRepository.save(any()) } answers { firstArg() }

            service.sendExpiryReminder("membership_due")

            Then("it real-notifies the member once and real-marks reminderSentAt") {
                verify(exactly = 1) { notificationRepository.save(match { it.type == "PLATFORM_MEMBERSHIP_EXPIRING_SOON" && it.userId == "user_1" }) }
                membership.reminderSentAt shouldNotBe null
            }

            // Real fix (2026-09-13, push-before-commit ordering sweep): reminderSentAt must
            // be saved BEFORE the push fires -- otherwise a rollback after the push leaves
            // the flag unset and the next scheduler pass resends it.
            Then("the reminderSentAt flag is saved before the push is sent") {
                verifyOrder {
                    platformMembershipRepository.save(any())
                    pushNotificationService.sendToUser("user_1", any(), any(), any())
                }
            }
        }

        When("sending a reminder for a membership that was already reminded") {
            val membership = membershipWith("membership_already", Instant.now().plus(1, ChronoUnit.DAYS), reminderSentAt = Instant.now())
            every { platformMembershipRepository.findById("membership_already") } returns Optional.of(membership)

            service.sendExpiryReminder("membership_already")

            Then("it real-skips -- no double notification for the same real membership") {
                verify(exactly = 0) { notificationRepository.save(any()) }
            }
        }

        When("sending a reminder for a membership that has genuinely already lapsed") {
            val membership = membershipWith("membership_lapsed_send", Instant.now().minus(1, ChronoUnit.DAYS))
            every { platformMembershipRepository.findById("membership_lapsed_send") } returns Optional.of(membership)

            service.sendExpiryReminder("membership_lapsed_send")

            Then("it real-skips -- there's no longer a real benefit about to lapse") {
                verify(exactly = 0) { notificationRepository.save(any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
