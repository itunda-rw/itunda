package rw.itunda.eats

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.EatsMembership
import rw.itunda.core.domain.WalletType
import rw.itunda.core.domain.Wallet
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.EatsMembershipRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional

/**
 * Real optimistic-lock regression test (found live in a 2026-08-02 audit pass):
 * `subscribe` is a real check-then-act create-or-extend shape once a membership row
 * already exists (read the current `activeUntil`, extend it, save) -- the real DB
 * unique constraint on `userId` only protects the very first subscribe's INSERT race,
 * not two concurrent EXTENSIONS of an already-existing membership, which would both
 * charge the real wallet but only actually extend `activeUntil` once. This proves the
 * fix's real mechanism: extending saves the SAME pre-existing `EatsMembership` row,
 * which is what makes its own @Version field actually guard a concurrent second
 * extend on that exact row.
 */
class EatsMembershipServiceTest : BehaviorSpec({

    Given("a real user with an already-active Eats Club membership") {
        val eatsMembershipRepository = mockk<EatsMembershipRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = EatsMembershipService(eatsMembershipRepository, walletRepository, ledgerService, notificationRepository, pushNotificationService)

        val wallet = Wallet(
            id = "wallet_1", userId = "user_1", accountNumber = "ACC-1", accountName = "Test wallet",
            type = WalletType.MAIN, balance = BigDecimal("10000"), availableBalance = BigDecimal("10000"),
        )
        val existing = EatsMembership(id = "eats_membership_1", userId = "user_1", activeUntil = Instant.parse("2026-08-10T00:00:00Z"))
        every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns wallet
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { eatsMembershipRepository.findByUserId("user_1") } returns existing
        val savedSlot = mutableListOf<EatsMembership>()
        every { eatsMembershipRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("subscribing again to extend it") {
            service.subscribe("user_1", 30)

            Then("the exact same row object -- the one carrying the real @Version -- is what gets saved") {
                (savedSlot.first() === existing) shouldBe true
            }
        }
    }

    // Real "date field with no reminder" gap -- see EatsMembership.reminderSentAt's own
    // doc comment. Same coverage shape MerchantCouponServiceTest already established
    // for its own sibling expiry-reminder gap.
    Given("real Eats Club memberships at various points in their real expiry-reminder window") {
        val eatsMembershipRepository = mockk<EatsMembershipRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = EatsMembershipService(eatsMembershipRepository, walletRepository, ledgerService, notificationRepository, pushNotificationService)

        fun membershipWith(id: String, activeUntil: Instant, reminderSentAt: Instant? = null) =
            EatsMembership(id = id, userId = "user_1", activeUntil = activeUntil, reminderSentAt = reminderSentAt)

        When("a real active membership's activeUntil is already inside the 3-day reminder window") {
            val soon = membershipWith("membership_soon", Instant.now().plus(1, ChronoUnit.DAYS))
            every { eatsMembershipRepository.findByReminderSentAtIsNull() } returns listOf(soon)

            Then("it is a real due candidate") {
                service.getMembershipsDueForExpiryReminder().map { it.id } shouldBe listOf("membership_soon")
            }
        }

        When("a real active membership's activeUntil is genuinely still outside the reminder window") {
            val far = membershipWith("membership_far", Instant.now().plus(30, ChronoUnit.DAYS))
            every { eatsMembershipRepository.findByReminderSentAtIsNull() } returns listOf(far)

            Then("it is real-excluded -- not due yet") {
                service.getMembershipsDueForExpiryReminder() shouldBe emptyList()
            }
        }

        When("a real membership has already lapsed") {
            val lapsed = membershipWith("membership_lapsed", Instant.now().minus(1, ChronoUnit.DAYS))
            every { eatsMembershipRepository.findByReminderSentAtIsNull() } returns listOf(lapsed)

            Then("it is real-excluded -- there is nothing left to renew before") {
                service.getMembershipsDueForExpiryReminder() shouldBe emptyList()
            }
        }

        When("sending a real expiry reminder for a due membership") {
            val membership = membershipWith("membership_due", Instant.now().plus(2, ChronoUnit.DAYS))
            every { eatsMembershipRepository.findById("membership_due") } returns Optional.of(membership)
            every { eatsMembershipRepository.save(any()) } answers { firstArg() }
            every { notificationRepository.save(any()) } answers { firstArg() }

            service.sendExpiryReminder("membership_due")

            Then("it real-notifies the member once and real-marks reminderSentAt") {
                verify(exactly = 1) { notificationRepository.save(match { it.type == "EATS_MEMBERSHIP_EXPIRING_SOON" && it.userId == "user_1" }) }
                membership.reminderSentAt shouldNotBe null
            }
        }

        When("sending a reminder for a membership that was already reminded") {
            val membership = membershipWith("membership_already", Instant.now().plus(1, ChronoUnit.DAYS), reminderSentAt = Instant.now())
            every { eatsMembershipRepository.findById("membership_already") } returns Optional.of(membership)

            service.sendExpiryReminder("membership_already")

            Then("it real-skips -- no double notification for the same real membership") {
                verify(exactly = 0) { notificationRepository.save(any()) }
            }
        }

        When("sending a reminder for a membership that has genuinely already lapsed") {
            val membership = membershipWith("membership_lapsed_send", Instant.now().minus(1, ChronoUnit.DAYS))
            every { eatsMembershipRepository.findById("membership_lapsed_send") } returns Optional.of(membership)

            service.sendExpiryReminder("membership_lapsed_send")

            Then("it real-skips -- there's no longer a real benefit about to lapse") {
                verify(exactly = 0) { notificationRepository.save(any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
