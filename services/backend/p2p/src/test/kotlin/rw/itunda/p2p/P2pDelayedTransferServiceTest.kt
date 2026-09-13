package rw.itunda.p2p

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.P2pDelayedTransfer
import rw.itunda.core.domain.P2pDelayedTransferStatus
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.P2pDelayedTransferRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.Optional

class P2pDelayedTransferServiceTest : BehaviorSpec({

    fun account(id: String, userId: String, balance: String) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal(balance), availableBalance = BigDecimal(balance),
    )

    fun buildService(
        p2pDelayedTransferRepository: P2pDelayedTransferRepository = mockk(),
        accountRepository: AccountRepository = mockk(),
        userRepository: UserRepository = mockk(),
        transactionRepository: TransactionRepository = mockk(relaxed = true),
        ledgerService: LedgerService = mockk(),
        rateLimiter: RateLimiter = mockk(relaxed = true),
        notificationRepository: NotificationRepository = mockk(relaxed = true),
        pushNotificationService: PushNotificationService = mockk(relaxed = true),
        p2pTransferLimitService: P2pTransferLimitService = mockk(relaxed = true),
    ) = P2pDelayedTransferService(
        p2pDelayedTransferRepository, accountRepository, userRepository, transactionRepository,
        ledgerService, rateLimiter, notificationRepository, pushNotificationService, p2pTransferLimitService,
    )

    Given("a real sender holding a delayed transfer to a real recipient by phone number") {
        val p2pDelayedTransferRepository = mockk<P2pDelayedTransferRepository>()
        val accountRepository = mockk<AccountRepository>()
        val userRepository = mockk<UserRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val service = buildService(
            p2pDelayedTransferRepository = p2pDelayedTransferRepository, accountRepository = accountRepository,
            userRepository = userRepository, ledgerService = ledgerService, transactionRepository = transactionRepository,
        )

        val recipientUser = User(id = "recipient_1", phoneNumber = "+250788000099", firstName = "R", lastName = "T", passwordHash = "x")
        every { accountRepository.findByUserIdAndType("sender_1", AccountType.MAIN) } returns account("account_sender", "sender_1", "10000")
        every { userRepository.findByPhoneNumber("+250788000099") } returns recipientUser
        every { accountRepository.findByUserIdAndType("recipient_1", AccountType.MAIN) } returns account("account_recipient", "recipient_1", "0")
        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_hold_1", emptyList())
        val savedSlot = slot<P2pDelayedTransfer>()
        every { p2pDelayedTransferRepository.save(capture(savedSlot)) } answers { firstArg() }
        // Real mockk quirk, same reasoning P2pServiceTest's own equivalent stub already
        // documents: JpaRepository.save's self-bounded generic signature isn't reliably
        // relaxed by plain `relaxed = true`, so it must be explicitly stubbed here.
        every { transactionRepository.save(any()) } answers { firstArg() }

        When("they choose to send safely instead of instantly") {
            val before = Instant.now()
            val transfer = service.sendDelayed("sender_1", "+250788000099", BigDecimal("2000"), "Rent")

            Then("real money is held in p2p_delay_holding, not credited to the recipient yet") {
                legsSlot.captured.size shouldBe 2
                val debitLeg = legsSlot.captured.first { it.direction == LedgerDirection.DEBIT }
                debitLeg.accountId shouldBe "account_sender"
                debitLeg.accountType shouldBe LedgerAccountType.WALLET
                debitLeg.amount shouldBe BigDecimal("2000")
                val creditLeg = legsSlot.captured.first { it.direction == LedgerDirection.CREDIT }
                creditLeg.accountId shouldBe "p2p_delay_holding"
                creditLeg.accountType shouldBe LedgerAccountType.P2P_DELAY_HOLDING
                creditLeg.amount shouldBe BigDecimal("2000")
            }

            Then("the real transfer is PENDING, holds the recipient's real identity, and releases after the real sourced window") {
                transfer.status shouldBe P2pDelayedTransferStatus.PENDING
                transfer.recipientUserId shouldBe "recipient_1"
                transfer.senderUserId shouldBe "sender_1"
                transfer.holdTransactionId shouldBe "ledgertxn_hold_1"
                (transfer.releaseAt.isAfter(before.plus(P2pDelayedTransfer.DELAY_WINDOW).minusSeconds(5))) shouldBe true
                (transfer.releaseAt.isBefore(before.plus(P2pDelayedTransfer.DELAY_WINDOW).plusSeconds(5))) shouldBe true
            }
        }
    }

    // Real Korean "이체한도" (transfer limit) enforcement (Section 186) -- see
    // P2pTransferLimitService's own doc comment for why the delayed path must be
    // gated by the identical real cap sendDirect enforces. Uses the REAL
    // P2pTransferLimitService (not mocked), so this exercises the actual real
    // integration, not just that sendDelayed calls some mock.
    Given("a real sender whose real transfer amount exceeds the real per-transfer cap, choosing the delayed path") {
        val accountRepository = mockk<AccountRepository>()
        val userRepository = mockk<UserRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>()
        every { transactionRepository.findBySenderIdAndTypeAndStatusAndCreatedAtGreaterThanEqual(eq("sender_lim"), any(), any(), any()) } returns emptyList()
        val service = buildService(
            accountRepository = accountRepository, userRepository = userRepository, ledgerService = ledgerService,
            transactionRepository = transactionRepository, p2pTransferLimitService = P2pTransferLimitService(transactionRepository, accountRepository),
        )

        val recipientUser = User(id = "recipient_lim", phoneNumber = "+250788000199", firstName = "R", lastName = "T", passwordHash = "x")
        every { accountRepository.findByUserIdAndType("sender_lim", AccountType.MAIN) } returns account("account_lim", "sender_lim", "10000000")
        every { userRepository.findByPhoneNumber("+250788000199") } returns recipientUser
        every { accountRepository.findByUserIdAndType("recipient_lim", AccountType.MAIN) } returns account("account_recipient_lim", "recipient_lim", "0")

        When("they try to hold 600,000 RWF, over the real 500,000 per-transfer cap") {
            Then("it real-blocks with P2pTransferLimitExceededException before any real money is held") {
                try {
                    service.sendDelayed("sender_lim", "+250788000199", BigDecimal("600000"), "")
                    error("expected P2pTransferLimitExceededException")
                } catch (e: P2pTransferLimitExceededException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a real sender trying to send a delayed transfer to their own account") {
        val accountRepository = mockk<AccountRepository>()
        val userRepository = mockk<UserRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = buildService(accountRepository = accountRepository, userRepository = userRepository, ledgerService = ledgerService)

        val selfUser = User(id = "user_self", phoneNumber = "+250788000077", firstName = "S", lastName = "T", passwordHash = "x")
        every { accountRepository.findByUserIdAndType("user_self", AccountType.MAIN) } returns account("account_self", "user_self", "10000")
        every { userRepository.findByPhoneNumber("+250788000077") } returns selfUser

        When("they try") {
            Then("it throws P2pSelfPaymentException before touching the ledger") {
                try {
                    service.sendDelayed("user_self", "+250788000077", BigDecimal("1000"), "")
                    error("expected P2pSelfPaymentException")
                } catch (e: P2pSelfPaymentException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a real sender whose identifier matches no real itunda account") {
        val accountRepository = mockk<AccountRepository>()
        val userRepository = mockk<UserRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = buildService(accountRepository = accountRepository, userRepository = userRepository, ledgerService = ledgerService)

        every { accountRepository.findByUserIdAndType("sender_3", AccountType.MAIN) } returns account("account_sender3", "sender_3", "10000")
        every { userRepository.findByPhoneNumber("+250700000000") } returns null
        every { accountRepository.findByAccountNumber("+250700000000") } returns null

        When("they try to send") {
            Then("it throws P2pRecipientNotFoundException -- a real, honest 404") {
                try {
                    service.sendDelayed("sender_3", "+250700000000", BigDecimal("1000"), "")
                    error("expected P2pRecipientNotFoundException")
                } catch (e: P2pRecipientNotFoundException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a real sender with insufficient balance") {
        val accountRepository = mockk<AccountRepository>()
        val userRepository = mockk<UserRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = buildService(accountRepository = accountRepository, userRepository = userRepository, ledgerService = ledgerService)

        val recipientUser = User(id = "recipient_9", phoneNumber = "+250788000088", firstName = "R", lastName = "T", passwordHash = "x")
        every { accountRepository.findByUserIdAndType("sender_9", AccountType.MAIN) } returns account("account_poor", "sender_9", "500")
        every { userRepository.findByPhoneNumber("+250788000088") } returns recipientUser
        every { accountRepository.findByUserIdAndType("recipient_9", AccountType.MAIN) } returns account("account_recip9", "recipient_9", "0")

        When("they try to hold more than they have") {
            Then("it throws InsufficientFundsException before touching the ledger") {
                try {
                    service.sendDelayed("sender_9", "+250788000088", BigDecimal("1000"), "")
                    error("expected InsufficientFundsException")
                } catch (e: InsufficientFundsException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a real sender exceeding the real send-delayed rate limit") {
        val rateLimiter = mockk<RateLimiter>()
        val ledgerService = mockk<LedgerService>()
        val service = buildService(rateLimiter = rateLimiter, ledgerService = ledgerService)
        every { rateLimiter.checkLimit("p2p:send-delayed:sender_20", limit = 30, window = Duration.ofHours(1)) } throws RateLimitExceededException("Too many requests")

        When("they try to hold yet another delayed transfer") {
            Then("it real-propagates RateLimitExceededException before ever touching a real account") {
                try {
                    service.sendDelayed("sender_20", "+250788000199", BigDecimal("1000"), "")
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a real sender cancelling their own real still-PENDING delayed transfer") {
        val p2pDelayedTransferRepository = mockk<P2pDelayedTransferRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = buildService(p2pDelayedTransferRepository = p2pDelayedTransferRepository, accountRepository = accountRepository, ledgerService = ledgerService)

        val transfer = P2pDelayedTransfer(
            id = "p2p_delayed_1", senderUserId = "sender_1", senderAccountId = "account_sender",
            recipientUserId = "recipient_1", recipientAccountId = "account_recipient", amount = BigDecimal("2000"),
            description = "Rent", holdTransactionId = "ledgertxn_hold_1", releaseAt = Instant.now().plusSeconds(3600),
        )
        every { p2pDelayedTransferRepository.findByIdAndSenderUserId("p2p_delayed_1", "sender_1") } returns transfer
        every { accountRepository.findByUserIdAndType("sender_1", AccountType.MAIN) } returns account("account_sender", "sender_1", "8000")
        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_cancel_1", emptyList())
        every { p2pDelayedTransferRepository.save(any()) } answers { firstArg() }

        When("they cancel it") {
            val cancelled = service.cancel("sender_1", "p2p_delayed_1")

            Then("the real held amount is refunded straight back to the sender's own account") {
                legsSlot.captured.size shouldBe 2
                val debitLeg = legsSlot.captured.first { it.direction == LedgerDirection.DEBIT }
                debitLeg.accountId shouldBe "p2p_delay_holding"
                val creditLeg = legsSlot.captured.first { it.direction == LedgerDirection.CREDIT }
                creditLeg.accountId shouldBe "account_sender"
                creditLeg.amount shouldBe BigDecimal("2000")
            }

            Then("it's marked CANCELLED, not silently deleted") {
                cancelled.status shouldBe P2pDelayedTransferStatus.CANCELLED
                cancelled.resolutionTransactionId shouldBe "ledgertxn_cancel_1"
            }
        }
    }

    Given("a real user trying to cancel a delayed transfer that belongs to someone else") {
        val p2pDelayedTransferRepository = mockk<P2pDelayedTransferRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = buildService(p2pDelayedTransferRepository = p2pDelayedTransferRepository, ledgerService = ledgerService)

        // Real IDOR guard -- findByIdAndSenderUserId compares the resource's real
        // owning field against the caller, so a real transfer that exists but belongs
        // to a DIFFERENT sender correctly returns null here, never leaking that it exists.
        every { p2pDelayedTransferRepository.findByIdAndSenderUserId("p2p_delayed_2", "attacker_1") } returns null

        When("they try to cancel it by guessing its real id") {
            Then("it throws the same honest P2pDelayedTransferNotFoundException a truly nonexistent id would") {
                try {
                    service.cancel("attacker_1", "p2p_delayed_2")
                    error("expected P2pDelayedTransferNotFoundException")
                } catch (e: P2pDelayedTransferNotFoundException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a real sender trying to cancel a delayed transfer the scheduler already released") {
        val p2pDelayedTransferRepository = mockk<P2pDelayedTransferRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = buildService(p2pDelayedTransferRepository = p2pDelayedTransferRepository, ledgerService = ledgerService)

        val transfer = P2pDelayedTransfer(
            id = "p2p_delayed_3", senderUserId = "sender_1", senderAccountId = "account_sender",
            recipientUserId = "recipient_1", recipientAccountId = "account_recipient", amount = BigDecimal("2000"),
            description = "Rent", status = P2pDelayedTransferStatus.COMPLETED, holdTransactionId = "ledgertxn_hold_3",
            releaseAt = Instant.now().minusSeconds(60),
        )
        every { p2pDelayedTransferRepository.findByIdAndSenderUserId("p2p_delayed_3", "sender_1") } returns transfer

        When("they try to cancel it too late") {
            Then("it throws P2pDelayedTransferNotCancellableException, never double-refunding already-released money") {
                try {
                    service.cancel("sender_1", "p2p_delayed_3")
                    error("expected P2pDelayedTransferNotCancellableException")
                } catch (e: P2pDelayedTransferNotCancellableException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a real delayed transfer whose window has elapsed") {
        val p2pDelayedTransferRepository = mockk<P2pDelayedTransferRepository>()
        val accountRepository = mockk<AccountRepository>()
        val userRepository = mockk<UserRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = buildService(
            p2pDelayedTransferRepository = p2pDelayedTransferRepository, accountRepository = accountRepository,
            userRepository = userRepository, ledgerService = ledgerService,
            notificationRepository = notificationRepository, pushNotificationService = pushNotificationService,
        )

        val transfer = P2pDelayedTransfer(
            id = "p2p_delayed_4", senderUserId = "sender_1", senderAccountId = "account_sender",
            recipientUserId = "recipient_1", recipientAccountId = "account_recipient", amount = BigDecimal("2000"),
            description = "Rent", holdTransactionId = "ledgertxn_hold_4", releaseAt = Instant.now().minusSeconds(1),
        )
        every { p2pDelayedTransferRepository.findById("p2p_delayed_4") } returns Optional.of(transfer)
        every { accountRepository.findById("account_recipient") } returns Optional.of(account("account_recipient", "recipient_1", "0"))
        every { userRepository.findById("sender_1") } returns Optional.of(
            User(id = "sender_1", phoneNumber = "+250788000001", firstName = "Eric", lastName = "Uwase", passwordHash = "x"),
        )
        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_release_4", emptyList())
        every { p2pDelayedTransferRepository.save(any()) } answers { firstArg() }
        // Same real mockk quirk as sendDelayed's own test above -- NotificationRepository
        // .save must be explicitly stubbed, not left to `relaxed = true`.
        every { notificationRepository.save(any()) } answers { firstArg() }

        When("the scheduler releases it") {
            service.release("p2p_delayed_4")

            Then("the real held amount is credited to the real recipient's own account") {
                legsSlot.captured.size shouldBe 2
                val debitLeg = legsSlot.captured.first { it.direction == LedgerDirection.DEBIT }
                debitLeg.accountId shouldBe "p2p_delay_holding"
                val creditLeg = legsSlot.captured.first { it.direction == LedgerDirection.CREDIT }
                creditLeg.accountId shouldBe "account_recipient"
                creditLeg.amount shouldBe BigDecimal("2000")
            }

            Then("the real recipient gets a real money-received notification, same as an instant transfer") {
                verify(exactly = 1) { pushNotificationService.sendToUser("recipient_1", "Money received", "Eric Uwase sent you 2000 RWF.", any(), type = "MONEY_RECEIVED") }
            }

            Then("it's marked COMPLETED") {
                transfer.status shouldBe P2pDelayedTransferStatus.COMPLETED
                transfer.resolutionTransactionId shouldBe "ledgertxn_release_4"
            }
        }
    }

    Given("a real delayed transfer the sender already cancelled just before the scheduler reached it") {
        val p2pDelayedTransferRepository = mockk<P2pDelayedTransferRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = buildService(p2pDelayedTransferRepository = p2pDelayedTransferRepository, ledgerService = ledgerService)

        val transfer = P2pDelayedTransfer(
            id = "p2p_delayed_5", senderUserId = "sender_1", senderAccountId = "account_sender",
            recipientUserId = "recipient_1", recipientAccountId = "account_recipient", amount = BigDecimal("2000"),
            description = "Rent", status = P2pDelayedTransferStatus.CANCELLED, holdTransactionId = "ledgertxn_hold_5",
            releaseAt = Instant.now().minusSeconds(1),
        )
        every { p2pDelayedTransferRepository.findById("p2p_delayed_5") } returns Optional.of(transfer)

        When("the scheduler's own real re-check-before-act guard reaches it anyway") {
            service.release("p2p_delayed_5")

            Then("it's silently skipped, never double-processed -- the same guard MarketplaceService.autoReleaseEscrow already establishes") {
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }
    }

    // Real sibling-asymmetry fix (2026-09-13) -- Gift/GiftVoucher/MerchantCoupon/
    // MarketplaceEscrow all warn the party who can still act before their own
    // hold-then-auto-settle window closes; this feature's ENTIRE purpose is giving the
    // sender a window to cancel, yet nothing ever reminded them it was closing.
    Given("real delayed transfers of every real age, checking which are due for a real pre-release reminder") {
        val p2pDelayedTransferRepository = mockk<P2pDelayedTransferRepository>()
        val service = buildService(p2pDelayedTransferRepository = p2pDelayedTransferRepository)

        val dueSoon = P2pDelayedTransfer(
            id = "p2p_delayed_6", senderUserId = "sender_1", senderAccountId = "account_sender",
            recipientUserId = "recipient_1", recipientAccountId = "account_recipient", amount = BigDecimal("2000"),
            description = "Rent", holdTransactionId = "ledgertxn_hold_6", releaseAt = Instant.now().plusSeconds(60),
        )
        val notYetDue = P2pDelayedTransfer(
            id = "p2p_delayed_7", senderUserId = "sender_1", senderAccountId = "account_sender",
            recipientUserId = "recipient_1", recipientAccountId = "account_recipient", amount = BigDecimal("2000"),
            description = "Rent", holdTransactionId = "ledgertxn_hold_7", releaseAt = Instant.now().plus(Duration.ofHours(2)),
        )
        every { p2pDelayedTransferRepository.findByStatusAndRemindedAtIsNull(P2pDelayedTransferStatus.PENDING) } returns listOf(dueSoon, notYetDue)

        When("getDueForReminder runs") {
            val due = service.getDueForReminder()

            Then("it real-includes only the transfer within the real reminder window, honestly excluding the too-early one") {
                due shouldBe listOf(dueSoon)
            }
        }
    }

    Given("a real still-PENDING delayed transfer within its real pre-release reminder window, never yet reminded") {
        val p2pDelayedTransferRepository = mockk<P2pDelayedTransferRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = buildService(
            p2pDelayedTransferRepository = p2pDelayedTransferRepository,
            notificationRepository = notificationRepository, pushNotificationService = pushNotificationService,
        )

        val transfer = P2pDelayedTransfer(
            id = "p2p_delayed_8", senderUserId = "sender_1", senderAccountId = "account_sender",
            recipientUserId = "recipient_1", recipientAccountId = "account_recipient", amount = BigDecimal("2000"),
            description = "Rent", holdTransactionId = "ledgertxn_hold_8", releaseAt = Instant.now().plusSeconds(60),
        )
        every { p2pDelayedTransferRepository.findById("p2p_delayed_8") } returns Optional.of(transfer)
        every { p2pDelayedTransferRepository.save(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }

        When("sendReminder runs") {
            service.sendReminder("p2p_delayed_8")

            Then("it real-alerts the SENDER (the one who can still cancel) and marks the reminder sent, never double-firing on a re-check") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "sender_1" && it.type == "P2P_DELAYED_TRANSFER_REMINDER" }) }
                transfer.remindedAt shouldNotBe null
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
