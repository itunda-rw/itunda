package rw.itunda.p2p

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.User
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import java.math.BigDecimal
import java.util.Optional

// Real fix (2026-08-26): the real content-shape coverage this restores was previously
// inline in P2pServiceTest.kt's own payRequest/sendDirect Given blocks, moved here
// once notifyMoneyReceived/notifyMoneySent themselves moved to P2pNotificationService
// (see that file's own doc comment). Same real message-copy this codebase's own
// P2pService.payRequest/sendDirect callers already depend on.
class P2pNotificationServiceTest : BehaviorSpec({

    fun account() = Account(
        id = "account_sender", userId = "sender_1", accountNumber = "ACC-sender", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal("8000"), availableBalance = BigDecimal("8000"),
    )

    Given("a real sender and recipient, both real itunda users") {
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = P2pNotificationService(userRepository, notificationRepository, pushNotificationService)

        every { userRepository.findById("sender_1") } returns Optional.of(
            User(id = "sender_1", phoneNumber = "+250788000001", firstName = "Jean", lastName = "Paul", passwordHash = "x"),
        )
        every { userRepository.findById("recipient_1") } returns Optional.of(
            User(id = "recipient_1", phoneNumber = "+250788000002", firstName = "Alice", lastName = "M", passwordHash = "x"),
        )
        val notificationSlot = slot<Notification>()
        every { notificationRepository.save(capture(notificationSlot)) } answers { firstArg() }

        When("notifyMoneyReceived is called for the recipient") {
            service.notifyMoneyReceived("recipient_1", "sender_1", BigDecimal("2000"))

            Then("a real in-app notification is saved naming the real sender, not a generic message") {
                notificationSlot.captured.userId shouldBe "recipient_1"
                notificationSlot.captured.type shouldBe "MONEY_RECEIVED"
                notificationSlot.captured.body shouldBe "Jean Paul sent you 2000 RWF."
            }

            Then("a real push notification is also sent with the same real content") {
                verify(exactly = 1) { pushNotificationService.sendToUser("recipient_1", "Money received", "Jean Paul sent you 2000 RWF.", any(), type = "MONEY_RECEIVED") }
            }
        }

        When("notifyMoneySent is called for the sender") {
            service.notifyMoneySent("sender_1", account(), "recipient_1", BigDecimal("2000"))

            Then("a real in-app notification is saved naming the real recipient and the real debited account") {
                notificationSlot.captured.userId shouldBe "sender_1"
                notificationSlot.captured.type shouldBe "MONEY_SENT"
                notificationSlot.captured.body shouldBe "Test account → Alice M"
            }

            Then("a real push notification is also sent with the same real content") {
                verify(exactly = 1) { pushNotificationService.sendToUser("sender_1", "2000 RWF sent", "Test account → Alice M", any(), type = "MONEY_SENT") }
            }
        }
    }

    Given("a sender or recipient whose real user record can't be found") {
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = P2pNotificationService(userRepository, notificationRepository, pushNotificationService)
        every { userRepository.findById(any()) } returns Optional.empty()
        val notificationSlot = slot<Notification>()
        every { notificationRepository.save(capture(notificationSlot)) } answers { firstArg() }

        When("notifyMoneyReceived is called") {
            service.notifyMoneyReceived("recipient_1", "unknown_sender", BigDecimal("500"))

            Then("it falls back to a generic honest name instead of crashing or leaking a raw id") {
                notificationSlot.captured.body shouldBe "Someone sent you 500 RWF."
            }
        }

        When("notifyMoneySent is called") {
            service.notifyMoneySent("sender_1", account(), "unknown_recipient", BigDecimal("500"))

            Then("it falls back to a generic honest name instead of crashing or leaking a raw id") {
                notificationSlot.captured.body shouldBe "Test account → the recipient"
            }
        }
    }

    Given("a notification save that fails") {
        val userRepository = mockk<UserRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = P2pNotificationService(userRepository, notificationRepository, pushNotificationService)
        every { notificationRepository.save(any()) } throws RuntimeException("db down")

        When("notifyMoneyReceived is called") {
            Then("it's swallowed, not propagated -- a notification failure must never fail money that already moved") {
                service.notifyMoneyReceived("recipient_1", "sender_1", BigDecimal("2000"))
            }
        }
    }
})
