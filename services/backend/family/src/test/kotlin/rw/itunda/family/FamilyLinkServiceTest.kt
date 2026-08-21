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
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.FamilyLinkRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
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
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = FamilyLinkService(familyLinkRepository, userRepository, accountRepository, transactionRepository, notificationRepository, rateLimiter, pushNotificationService)

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

            Then("the real child also gets a real push notification, not just the in-app one") {
                io.mockk.verify(exactly = 1) { pushNotificationService.sendToUser("child_1", "Family link invitation", any(), any()) }
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
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = FamilyLinkService(familyLinkRepository, userRepository, accountRepository, transactionRepository, notificationRepository, rateLimiter, pushNotificationService)

        val pending = FamilyLink(id = "familylink_1", guardianUserId = "guardian_1", childUserId = "child_1")

        When("the child accepts") {
            every { familyLinkRepository.findByIdAndChildUserId("familylink_1", "child_1") } returns pending
            every { userRepository.findById("child_1") } returns Optional.of(user("child_1", "+250788000002", "Alice", "M"))

            val result = service.respondToInvite("child_1", "familylink_1", true)

            Then("it becomes ACTIVE") {
                result.status shouldBe FamilyLinkStatus.ACTIVE
            }

            Then("the real guardian also gets a real push notification, not just the in-app one") {
                io.mockk.verify(exactly = 1) { pushNotificationService.sendToUser("guardian_1", "Family link accepted", any(), any()) }
            }
        }

        When("the child declines") {
            every { familyLinkRepository.findByIdAndChildUserId("familylink_1", "child_1") } returns FamilyLink(id = "familylink_1", guardianUserId = "guardian_1", childUserId = "child_1")

            val result = service.respondToInvite("child_1", "familylink_1", false)

            Then("it becomes DECLINED, never silently ACTIVE") {
                result.status shouldBe FamilyLinkStatus.DECLINED
            }
        }

        // Real bug found live (2026-08-02): respondToInvite read-then-mutated this
        // row's status with no @Version guard -- two concurrent respond calls (e.g. an
        // accept and a decline racing from a flaky client retry) could both read
        // PENDING and both commit, whichever wrote last silently winning instead of the
        // loser getting a real 409. This asserts the mechanism the fix now relies on:
        // the SAME versioned entity that was read and status-checked is the one
        // actually passed to save(), so a concurrent second respond() on a stale
        // version real-409s via the existing global ObjectOptimisticLockingFailureException
        // handler -- same pattern SupportServiceTest.kt's own resolve() fix already
        // established.
        When("the child accepts, with @Version now present on the entity") {
            val versionedPending = FamilyLink(id = "familylink_1", guardianUserId = "guardian_1", childUserId = "child_1", version = 3)
            every { familyLinkRepository.findByIdAndChildUserId("familylink_1", "child_1") } returns versionedPending
            every { userRepository.findById("child_1") } returns Optional.of(user("child_1", "+250788000002", "Alice", "M"))
            val savedSlot = mutableListOf<FamilyLink>()
            every { familyLinkRepository.save(capture(savedSlot)) } answers { firstArg() }

            service.respondToInvite("child_1", "familylink_1", true)

            Then("the same versioned link instance that was read is the one saved") {
                savedSlot.first() shouldBe versionedPending
                savedSlot.first().version shouldBe versionedPending.version
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
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = FamilyLinkService(familyLinkRepository, userRepository, accountRepository, transactionRepository, notificationRepository, rateLimiter, pushNotificationService)

        When("the guardian views the child's real overview") {
            every { familyLinkRepository.findByGuardianUserIdAndChildUserIdAndStatus("guardian_1", "child_1", FamilyLinkStatus.ACTIVE) } returns
                FamilyLink(id = "familylink_1", guardianUserId = "guardian_1", childUserId = "child_1", status = FamilyLinkStatus.ACTIVE)
            every { userRepository.findById("child_1") } returns Optional.of(user("child_1", "+250788000002", "Alice", "M"))
            every { accountRepository.findByUserIdAndType("child_1", AccountType.MAIN) } returns
                Account(id = "account_child", userId = "child_1", accountNumber = "ACC1", accountName = "Alice's account", type = AccountType.MAIN, balance = BigDecimal("15000"), availableBalance = BigDecimal("15000"))
            every { transactionRepository.findBySenderIdOrRecipientIdOrderByCreatedAtDesc("child_1", "child_1") } returns listOf(
                Transaction(
                    id = "txn_1", referenceNumber = "REF1", senderId = "child_1", recipientId = "merchant_1", amount = BigDecimal("2000"),
                    fee = BigDecimal.ZERO, currency = "RWF", type = TransactionType.PAYMENT, status = TransactionStatus.COMPLETED, description = "Snack",
                ),
            )

            val overview = service.getChildOverview("guardian_1", "child_1")

            Then("it returns the real account balance and real transaction history") {
                overview.accountBalance shouldBe BigDecimal("15000")
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

        When("the guardian sets a real daily spend limit") {
            every { familyLinkRepository.findByGuardianUserIdAndChildUserIdAndStatus("guardian_1", "child_1", FamilyLinkStatus.ACTIVE) } returns
                FamilyLink(id = "familylink_1", guardianUserId = "guardian_1", childUserId = "child_1", status = FamilyLinkStatus.ACTIVE)
            val savedSlot = mutableListOf<FamilyLink>()
            every { familyLinkRepository.save(capture(savedSlot)) } answers { firstArg() }

            service.setSpendLimit("guardian_1", "child_1", BigDecimal("5000"))

            Then("it persists the real limit on the link") {
                savedSlot.first().dailySpendLimit shouldBe BigDecimal("5000")
            }
        }

        When("the guardian clears a real spend limit with null") {
            every { familyLinkRepository.findByGuardianUserIdAndChildUserIdAndStatus("guardian_1", "child_1", FamilyLinkStatus.ACTIVE) } returns
                FamilyLink(id = "familylink_1", guardianUserId = "guardian_1", childUserId = "child_1", status = FamilyLinkStatus.ACTIVE, dailySpendLimit = BigDecimal("5000"))
            val savedSlot = mutableListOf<FamilyLink>()
            every { familyLinkRepository.save(capture(savedSlot)) } answers { firstArg() }

            service.setSpendLimit("guardian_1", "child_1", null)

            Then("it honestly removes the restriction, not just sets a very large number") {
                savedSlot.first().dailySpendLimit shouldBe null
            }
        }

        When("setting a real zero or negative spend limit") {
            every { familyLinkRepository.findByGuardianUserIdAndChildUserIdAndStatus("guardian_1", "child_1", FamilyLinkStatus.ACTIVE) } returns
                FamilyLink(id = "familylink_1", guardianUserId = "guardian_1", childUserId = "child_1", status = FamilyLinkStatus.ACTIVE)

            Then("it's rejected before saving") {
                try {
                    service.setSpendLimit("guardian_1", "child_1", BigDecimal.ZERO)
                    throw AssertionError("expected FamilyLinkInvalidSpendLimitException")
                } catch (e: FamilyLinkInvalidSpendLimitException) {
                    // expected
                }
            }
        }

        When("a stranger without an active link tries to set a spend limit") {
            every { familyLinkRepository.findByGuardianUserIdAndChildUserIdAndStatus("stranger_1", "child_1", FamilyLinkStatus.ACTIVE) } returns null

            Then("it's honestly rejected") {
                try {
                    service.setSpendLimit("stranger_1", "child_1", BigDecimal("5000"))
                    throw AssertionError("expected FamilyLinkUnauthorizedException")
                } catch (e: FamilyLinkUnauthorizedException) {
                    // expected
                }
            }
        }
    }

    Given("a real child linked with a real active daily spend limit") {
        val familyLinkRepository = mockk<FamilyLinkRepository>()
        val userRepository = mockk<UserRepository>()
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = FamilyLinkService(familyLinkRepository, userRepository, accountRepository, transactionRepository, notificationRepository, rateLimiter, pushNotificationService)

        val link = FamilyLink(id = "familylink_1", guardianUserId = "guardian_1", childUserId = "child_1", status = FamilyLinkStatus.ACTIVE, dailySpendLimit = BigDecimal("5000"))
        every { familyLinkRepository.findByChildUserIdAndStatusAndDailySpendLimitIsNotNull("child_1", FamilyLinkStatus.ACTIVE) } returns link
        every { accountRepository.findByIdForUpdate("account_child_1") } returns java.util.Optional.empty()

        When("a real transfer would stay within the real limit") {
            every {
                transactionRepository.findBySenderIdAndTypeAndStatusAndCreatedAtGreaterThanEqual("child_1", TransactionType.TRANSFER, TransactionStatus.COMPLETED, any())
            } returns listOf(
                Transaction(id = "txn_1", referenceNumber = "REF1", senderId = "child_1", recipientId = "user_2", amount = BigDecimal("1000"), fee = BigDecimal.ZERO, currency = "RWF", type = TransactionType.TRANSFER, status = TransactionStatus.COMPLETED, description = "x"),
            )

            Then("it real-allows the transfer, no exception") {
                service.enforceSpendLimit("child_1", "account_child_1", BigDecimal("3000"))
            }

            // Real fix (concurrency audit, 2026-08-21): the daily-cumulative SUM() check
            // must lock the child's own account row first, same shape
            // P2pTransferLimitService.enforce already established -- proves the fix
            // actually happens, not just that the pre-existing limit logic still works.
            Then("it real-locks the child's own account row before the sum-check, same discipline P2pTransferLimitService.enforce already establishes") {
                service.enforceSpendLimit("child_1", "account_child_1", BigDecimal("3000"))
                io.mockk.verify(exactly = 1) { accountRepository.findByIdForUpdate("account_child_1") }
            }
        }

        When("a real transfer would exceed the real limit combined with today's real prior sends") {
            every {
                transactionRepository.findBySenderIdAndTypeAndStatusAndCreatedAtGreaterThanEqual("child_1", TransactionType.TRANSFER, TransactionStatus.COMPLETED, any())
            } returns listOf(
                Transaction(id = "txn_1", referenceNumber = "REF1", senderId = "child_1", recipientId = "user_2", amount = BigDecimal("4000"), fee = BigDecimal.ZERO, currency = "RWF", type = TransactionType.TRANSFER, status = TransactionStatus.COMPLETED, description = "x"),
            )

            Then("it real-blocks the transfer") {
                try {
                    service.enforceSpendLimit("child_1", "account_child_1", BigDecimal("1500"))
                    throw AssertionError("expected FamilySpendLimitExceededException")
                } catch (e: FamilySpendLimitExceededException) {
                    // expected
                }
            }
        }
    }

    Given("a real user who is not a linked child with any real spend limit") {
        val familyLinkRepository = mockk<FamilyLinkRepository>()
        val userRepository = mockk<UserRepository>()
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = FamilyLinkService(familyLinkRepository, userRepository, accountRepository, transactionRepository, notificationRepository, rateLimiter, pushNotificationService)

        every { familyLinkRepository.findByChildUserIdAndStatusAndDailySpendLimitIsNotNull("user_5", FamilyLinkStatus.ACTIVE) } returns null

        When("enforceSpendLimit is called for a transfer of any real amount") {
            Then("it's a real no-op, never touching the transaction repository or locking any account -- this must stay cheap for every real P2P send") {
                service.enforceSpendLimit("user_5", "account_user_5", BigDecimal("999999"))
                io.mockk.verify(exactly = 0) { transactionRepository.findBySenderIdAndTypeAndStatusAndCreatedAtGreaterThanEqual(any(), any(), any(), any()) }
                io.mockk.verify(exactly = 0) { accountRepository.findByIdForUpdate(any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
