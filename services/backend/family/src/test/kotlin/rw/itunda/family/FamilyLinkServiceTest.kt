package rw.itunda.family

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.FamilyLink
import rw.itunda.core.domain.FamilyLinkStatus
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.repository.FamilyLinkRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.util.Optional

/**
 * Real Toss 유스 (Toss Youth)-style guardian-child account link -- see
 * FamilyLinkService's own doc comment for the full sourced account.
 */
class FamilyLinkServiceTest : BehaviorSpec({

    fun user(id: String, phone: String, first: String, last: String) = User(id = id, phoneNumber = phone, firstName = first, lastName = last, passwordHash = "x")

    Given("a real guardian and a real child account, not yet linked") {
        val familyLinkRepository = mockk<FamilyLinkRepository>(relaxed = true)
        every { familyLinkRepository.save(any()) } answers { firstArg() }
        val userRepository = mockk<UserRepository>()
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = FamilyLinkService(familyLinkRepository, userRepository, walletRepository, transactionRepository, notificationRepository, rateLimiter)

        val guardian = user("guardian_1", "+250788000001", "Jean", "Baptiste")
        val child = user("child_1", "+250788000002", "Alice", "M")

        When("the guardian invites the child by phone number") {
            every { userRepository.findByPhoneNumber("+250788000002") } returns child
            every { familyLinkRepository.findByGuardianUserIdAndChildUserIdAndStatusIn("guardian_1", "child_1", listOf(FamilyLinkStatus.PENDING, FamilyLinkStatus.ACTIVE)) } returns emptyList()
            every { userRepository.findById("guardian_1") } returns Optional.of(guardian)

            val link = service.inviteChild("guardian_1", "+250788000002")

            Then("it creates a real PENDING link") {
                link.status shouldBe FamilyLinkStatus.PENDING
                link.guardianUserId shouldBe "guardian_1"
                link.childUserId shouldBe "child_1"
            }
        }

        When("inviting your own account") {
            every { userRepository.findByPhoneNumber("+250788000001") } returns guardian

            Then("it's rejected") {
                try {
                    service.inviteChild("guardian_1", "+250788000001")
                    throw AssertionError("expected FamilyLinkSelfException")
                } catch (e: FamilyLinkSelfException) {
                    // expected
                }
            }
        }

        When("a phone number with no real itunda account") {
            every { userRepository.findByPhoneNumber("+250788999999") } returns null

            Then("it's rejected") {
                try {
                    service.inviteChild("guardian_1", "+250788999999")
                    throw AssertionError("expected FamilyLinkChildNotFoundException")
                } catch (e: FamilyLinkChildNotFoundException) {
                    // expected
                }
            }
        }

        When("a link already exists or is pending") {
            every { userRepository.findByPhoneNumber("+250788000002") } returns child
            every { familyLinkRepository.findByGuardianUserIdAndChildUserIdAndStatusIn("guardian_1", "child_1", listOf(FamilyLinkStatus.PENDING, FamilyLinkStatus.ACTIVE)) } returns
                listOf(FamilyLink(id = "familylink_x", guardianUserId = "guardian_1", childUserId = "child_1"))

            Then("a duplicate invite is rejected") {
                try {
                    service.inviteChild("guardian_1", "+250788000002")
                    throw AssertionError("expected FamilyLinkAlreadyExistsException")
                } catch (e: FamilyLinkAlreadyExistsException) {
                    // expected
                }
            }
        }
    }

    Given("a real pending invitation") {
        val familyLinkRepository = mockk<FamilyLinkRepository>(relaxed = true)
        every { familyLinkRepository.save(any()) } answers { firstArg() }
        val userRepository = mockk<UserRepository>()
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = FamilyLinkService(familyLinkRepository, userRepository, walletRepository, transactionRepository, notificationRepository, rateLimiter)

        val pending = FamilyLink(id = "familylink_1", guardianUserId = "guardian_1", childUserId = "child_1")

        When("the child accepts") {
            every { familyLinkRepository.findByIdAndChildUserId("familylink_1", "child_1") } returns pending
            every { userRepository.findById("child_1") } returns Optional.of(user("child_1", "+250788000002", "Alice", "M"))

            val result = service.respondToInvite("child_1", "familylink_1", true)

            Then("it becomes ACTIVE") {
                result.status shouldBe FamilyLinkStatus.ACTIVE
            }
        }

        When("the child declines") {
            every { familyLinkRepository.findByIdAndChildUserId("familylink_1", "child_1") } returns FamilyLink(id = "familylink_1", guardianUserId = "guardian_1", childUserId = "child_1")

            val result = service.respondToInvite("child_1", "familylink_1", false)

            Then("it becomes DECLINED, never silently ACTIVE") {
                result.status shouldBe FamilyLinkStatus.DECLINED
            }
        }

        When("responding to an already-responded invitation") {
            val alreadyActive = FamilyLink(id = "familylink_1", guardianUserId = "guardian_1", childUserId = "child_1", status = FamilyLinkStatus.ACTIVE)
            every { familyLinkRepository.findByIdAndChildUserId("familylink_1", "child_1") } returns alreadyActive

            Then("it's rejected") {
                try {
                    service.respondToInvite("child_1", "familylink_1", true)
                    throw AssertionError("expected FamilyLinkNotPendingException")
                } catch (e: FamilyLinkNotPendingException) {
                    // expected
                }
            }
        }
    }

    Given("a real active family link") {
        val familyLinkRepository = mockk<FamilyLinkRepository>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = FamilyLinkService(familyLinkRepository, userRepository, walletRepository, transactionRepository, notificationRepository, rateLimiter)

        When("the guardian views the child's real overview") {
            every { familyLinkRepository.findByGuardianUserIdAndChildUserIdAndStatus("guardian_1", "child_1", FamilyLinkStatus.ACTIVE) } returns
                FamilyLink(id = "familylink_1", guardianUserId = "guardian_1", childUserId = "child_1", status = FamilyLinkStatus.ACTIVE)
            every { userRepository.findById("child_1") } returns Optional.of(user("child_1", "+250788000002", "Alice", "M"))
            every { walletRepository.findByUserIdAndType("child_1", WalletType.MAIN) } returns
                Wallet(id = "wallet_child", userId = "child_1", accountNumber = "ACC1", accountName = "Alice's wallet", type = WalletType.MAIN, balance = BigDecimal("15000"), availableBalance = BigDecimal("15000"))
            every { transactionRepository.findBySenderIdOrRecipientIdOrderByCreatedAtDesc("child_1", "child_1") } returns listOf(
                Transaction(
                    id = "txn_1", referenceNumber = "REF1", senderId = "child_1", recipientId = "merchant_1", amount = BigDecimal("2000"),
                    fee = BigDecimal.ZERO, currency = "RWF", type = TransactionType.PAYMENT, status = TransactionStatus.COMPLETED, description = "Snack",
                ),
            )

            val overview = service.getChildOverview("guardian_1", "child_1")

            Then("it returns the real wallet balance and real transaction history") {
                overview.walletBalance shouldBe BigDecimal("15000")
                overview.recentTransactions.size shouldBe 1
                overview.childName shouldBe "Alice M"
            }
        }

        When("a guardian without an active link tries to view an unrelated account's overview") {
            every { familyLinkRepository.findByGuardianUserIdAndChildUserIdAndStatus("stranger_1", "child_1", FamilyLinkStatus.ACTIVE) } returns null

            Then("it's honestly rejected, not leaked") {
                try {
                    service.getChildOverview("stranger_1", "child_1")
                    throw AssertionError("expected FamilyLinkUnauthorizedException")
                } catch (e: FamilyLinkUnauthorizedException) {
                    // expected
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
