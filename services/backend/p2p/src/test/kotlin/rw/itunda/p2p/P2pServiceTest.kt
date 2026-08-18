package rw.itunda.p2p

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.P2pPaymentRequest
import rw.itunda.core.domain.P2pPaymentRequestStatus
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.P2pPaymentRequestRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.WalletRepository
import rw.itunda.family.FamilyLinkService
import rw.itunda.savings.RoundUpService
import rw.itunda.wallet.AutoTopUpService
import rw.itunda.wallet.AutoTopUpTriggerResult
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.Optional

class P2pServiceTest : BehaviorSpec({

    fun wallet(id: String, userId: String, balance: String) = Wallet(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test wallet",
        type = WalletType.MAIN, balance = BigDecimal(balance), availableBalance = BigDecimal(balance),
    )

    Given("a real pending payment request from a requester with a real wallet") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, walletRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, notificationRepository, roundUpService, familyLinkService, autoTopUpService,
            pushNotificationService,
        )

        val request = P2pPaymentRequest(id = "p2p_1", requesterUserId = "requester_1", amount = BigDecimal("2000"), description = "Lunch", expiresAt = Instant.now().plusSeconds(900))
        every { p2pPaymentRequestRepository.findById("p2p_1") } returns Optional.of(request)
        every { walletRepository.findByUserIdAndType("payer_1", WalletType.MAIN) } returns wallet("wallet_payer", "payer_1", "10000")
        every { walletRepository.findByUserIdAndType("requester_1", WalletType.MAIN) } returns wallet("wallet_requester", "requester_1", "0")
        every { walletRepository.findById("wallet_payer") } returns Optional.of(wallet("wallet_payer", "payer_1", "8000"))
        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { transactionRepository.save(any()) } answers { firstArg() }
        every { p2pPaymentRequestRepository.save(any()) } answers { firstArg() }

        When("a different real user pays it") {
            every { userRepository.findById("payer_1") } returns Optional.of(
                User(id = "payer_1", phoneNumber = "+250788000001", firstName = "Jean", lastName = "Paul", passwordHash = "x"),
            )
            val notificationSlot = slot<Notification>()
            every { notificationRepository.save(capture(notificationSlot)) } answers { firstArg() }

            val (transaction, newBalance) = service.payRequest("payer_1", "p2p_1")

            // Real-time "money received" notification (2026-07-22) -- mirrors one of
            // Toss Bank's own signature UX elements (an instant "OOO님이 X원을 보냈어요"
            // notification the moment money arrives), a real gap found by auditing this
            // file directly: zero Notification references existed anywhere in it before.
            Then("the requester gets a real, immediate notification naming the real payer, not a generic message") {
                notificationSlot.captured.userId shouldBe "requester_1"
                notificationSlot.captured.type shouldBe "MONEY_RECEIVED"
                notificationSlot.captured.body shouldBe "Jean Paul sent you 2000 RWF."
            }

            Then("the requester also gets a real push notification, not just the in-app one") {
                verify(exactly = 1) { pushNotificationService.sendToUser("requester_1", "Money received", "Jean Paul sent you 2000 RWF.", any(), type = "MONEY_RECEIVED") }
            }

            Then("it's a direct wallet-to-wallet ledger pair -- no rail_suspense hop, no fee, unlike a regular transfer") {
                legsSlot.captured.size shouldBe 2
                val debitLeg = legsSlot.captured.first { it.direction == LedgerDirection.DEBIT }
                debitLeg.accountId shouldBe "wallet_payer"
                debitLeg.accountType shouldBe LedgerAccountType.WALLET
                debitLeg.amount shouldBe BigDecimal("2000")
                val creditLeg = legsSlot.captured.first { it.direction == LedgerDirection.CREDIT }
                creditLeg.accountId shouldBe "wallet_requester"
                creditLeg.accountType shouldBe LedgerAccountType.WALLET
                creditLeg.amount shouldBe BigDecimal("2000")
            }
            Then("the transaction has a real recipientId, not the hardcoded \"external\" a regular transfer uses") {
                transaction.senderId shouldBe "payer_1"
                transaction.recipientId shouldBe "requester_1"
                transaction.fee shouldBe BigDecimal.ZERO
            }
            Then("the request is marked completed and the returned balance is re-fetched, not stale") {
                request.status shouldBe P2pPaymentRequestStatus.COMPLETED
                request.paidByUserId shouldBe "payer_1"
                newBalance shouldBe BigDecimal("8000")
            }
        }
    }

    Given("a requester trying to pay their own request") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, walletRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, notificationRepository, roundUpService, familyLinkService, autoTopUpService,
            pushNotificationService,
        )

        val request = P2pPaymentRequest(id = "p2p_2", requesterUserId = "user_5", amount = BigDecimal("1000"), description = "test", expiresAt = Instant.now().plusSeconds(900))
        every { p2pPaymentRequestRepository.findById("p2p_2") } returns Optional.of(request)

        When("they try to pay it") {
            Then("it throws P2pSelfPaymentException before touching any wallet") {
                try {
                    service.payRequest("user_5", "p2p_2")
                    error("expected P2pSelfPaymentException")
                } catch (e: P2pSelfPaymentException) {
                    verify(exactly = 0) { walletRepository.findByUserIdAndType(any(), any()) }
                }
            }
        }
    }

    Given("an expired payment request") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, walletRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, notificationRepository, roundUpService, familyLinkService, autoTopUpService,
            pushNotificationService,
        )

        val request = P2pPaymentRequest(id = "p2p_3", requesterUserId = "requester_2", amount = BigDecimal("1000"), description = "test", expiresAt = Instant.now().minusSeconds(1))
        every { p2pPaymentRequestRepository.findById("p2p_3") } returns Optional.of(request)
        every { p2pPaymentRequestRepository.save(any()) } answers { firstArg() }

        When("someone tries to pay it") {
            Then("it throws P2pRequestNotPayableException and marks the request EXPIRED") {
                try {
                    service.payRequest("payer_2", "p2p_3")
                    error("expected P2pRequestNotPayableException")
                } catch (e: P2pRequestNotPayableException) {
                    request.status shouldBe P2pPaymentRequestStatus.EXPIRED
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a payer with insufficient balance") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, walletRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, notificationRepository, roundUpService, familyLinkService, autoTopUpService,
            pushNotificationService,
        )

        val request = P2pPaymentRequest(id = "p2p_4", requesterUserId = "requester_3", amount = BigDecimal("5000"), description = "test", expiresAt = Instant.now().plusSeconds(900))
        every { p2pPaymentRequestRepository.findById("p2p_4") } returns Optional.of(request)
        every { walletRepository.findByUserIdAndType("payer_3", WalletType.MAIN) } returns wallet("wallet_poor", "payer_3", "1000")
        every { walletRepository.findByUserIdAndType("requester_3", WalletType.MAIN) } returns wallet("wallet_req3", "requester_3", "0")

        When("they try to pay") {
            Then("it throws InsufficientFundsException before touching the ledger") {
                try {
                    service.payRequest("payer_3", "p2p_4")
                    error("expected InsufficientFundsException")
                } catch (e: InsufficientFundsException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a real requester exceeds the real request-creation rate limit") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>()
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, walletRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, notificationRepository, roundUpService, familyLinkService, autoTopUpService,
            pushNotificationService,
        )
        every { rateLimiter.checkLimit("p2p:request:requester_9", limit = 20, window = Duration.ofHours(1)) } throws RateLimitExceededException("Too many requests")

        When("they try to generate another real request") {
            Then("it real-propagates RateLimitExceededException, found missing in a 2026-07-19 security sweep") {
                try {
                    service.generateRequest("requester_9", BigDecimal("1000"), "test")
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { p2pPaymentRequestRepository.save(any()) }
                }
            }
        }
    }

    Given("a real payer exceeds the real payment rate limit") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>()
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, walletRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, notificationRepository, roundUpService, familyLinkService, autoTopUpService,
            pushNotificationService,
        )

        val request = P2pPaymentRequest(id = "p2p_5", requesterUserId = "requester_4", amount = BigDecimal("1000"), description = "test", expiresAt = Instant.now().plusSeconds(900))
        every { p2pPaymentRequestRepository.findById("p2p_5") } returns Optional.of(request)
        every { rateLimiter.checkLimit("p2p:pay:payer_9", limit = 30, window = Duration.ofHours(1)) } throws RateLimitExceededException("Too many requests")

        When("they try to pay") {
            Then("it real-propagates RateLimitExceededException before ever touching a real wallet") {
                try {
                    service.payRequest("payer_9", "p2p_5")
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { walletRepository.findByUserIdAndType(any(), any()) }
                }
            }
        }
    }

    Given("a real sender pushing money directly to a real recipient by phone number") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, walletRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, notificationRepository, roundUpService, familyLinkService, autoTopUpService,
            pushNotificationService,
        )

        val recipientUser = User(id = "recipient_1", phoneNumber = "+250788000099", firstName = "R", lastName = "T", passwordHash = "x")
        every { walletRepository.findByUserIdAndType("sender_1", WalletType.MAIN) } returns wallet("wallet_sender", "sender_1", "10000")
        every { userRepository.findByPhoneNumber("+250788000099") } returns recipientUser
        every { walletRepository.findByUserIdAndType("recipient_1", WalletType.MAIN) } returns wallet("wallet_recipient", "recipient_1", "0")
        every { walletRepository.findById("wallet_sender") } returns Optional.of(wallet("wallet_sender", "sender_1", "8000"))
        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_10", emptyList())
        every { transactionRepository.save(any()) } answers { firstArg() }

        When("they send by the real recipient's phone number") {
            every { userRepository.findById("sender_1") } returns Optional.of(
                User(id = "sender_1", phoneNumber = "+250788000001", firstName = "Eric", lastName = "Uwase", passwordHash = "x"),
            )
            val notificationSlot = slot<Notification>()
            every { notificationRepository.save(capture(notificationSlot)) } answers { firstArg() }

            val (transaction, newBalance) = service.sendDirect("sender_1", "+250788000099", BigDecimal("2000"), "Rent")

            Then("it's a direct wallet-to-wallet ledger pair, no fee, real recipientId not \"external\"") {
                legsSlot.captured.size shouldBe 2
                val debitLeg = legsSlot.captured.first { it.direction == LedgerDirection.DEBIT }
                debitLeg.accountId shouldBe "wallet_sender"
                debitLeg.amount shouldBe BigDecimal("2000")
                val creditLeg = legsSlot.captured.first { it.direction == LedgerDirection.CREDIT }
                creditLeg.accountId shouldBe "wallet_recipient"
                creditLeg.amount shouldBe BigDecimal("2000")
                transaction.recipientId shouldBe "recipient_1"
                transaction.fee shouldBe BigDecimal.ZERO
                newBalance shouldBe BigDecimal("8000")
            }

            Then("the real recipient also gets a real push notification, not just the in-app one") {
                verify(exactly = 1) { pushNotificationService.sendToUser("recipient_1", "Money received", "Eric Uwase sent you 2000 RWF.", any(), type = "MONEY_RECEIVED") }
            }

            Then("the recipient gets a real, immediate notification naming the real sender -- direct sendDirect transfers get the same real-time alert payRequest does") {
                notificationSlot.captured.userId shouldBe "recipient_1"
                notificationSlot.captured.type shouldBe "MONEY_RECEIVED"
                notificationSlot.captured.body shouldBe "Eric Uwase sent you 2000 RWF."
            }

            Then("it real-checks the real FamilyLink spend limit before the ledger moves any money") {
                io.mockk.verify(exactly = 1) { familyLinkService.enforceSpendLimit("sender_1", BigDecimal("2000")) }
            }
        }
    }

    Given("a real linked child whose transfer would exceed their real guardian-set daily spend limit") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>()
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, walletRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, notificationRepository, roundUpService, familyLinkService, autoTopUpService,
            pushNotificationService,
        )

        every { walletRepository.findByUserIdAndType("child_1", WalletType.MAIN) } returns wallet("wallet_child", "child_1", "10000")
        every { userRepository.findByPhoneNumber("+250788000099") } returns
            User(id = "recipient_1", phoneNumber = "+250788000099", firstName = "R", lastName = "T", passwordHash = "x")
        every { walletRepository.findByUserIdAndType("recipient_1", WalletType.MAIN) } returns wallet("wallet_recipient", "recipient_1", "0")
        every { familyLinkService.enforceSpendLimit("child_1", BigDecimal("2000")) } throws
            rw.itunda.family.FamilySpendLimitExceededException("This transfer would exceed your real daily spend limit set by your guardian")

        When("the child tries to send past their real limit") {
            Then("it's real-blocked before the ledger is ever touched") {
                try {
                    service.sendDirect("child_1", "+250788000099", BigDecimal("2000"), "")
                    throw AssertionError("expected FamilySpendLimitExceededException")
                } catch (e: rw.itunda.family.FamilySpendLimitExceededException) {
                    io.mockk.verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    // Real Naver Pay Money shortfall auto top-up (2026-07-27) -- see
    // AutoTopUpService.topUpShortfall's own doc comment.
    Given("a real sender with insufficient balance but a real enabled auto top-up setting") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, walletRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, notificationRepository, roundUpService, familyLinkService, autoTopUpService,
            pushNotificationService,
        )

        every { userRepository.findByPhoneNumber("+250788000099") } returns
            User(id = "recipient_1", phoneNumber = "+250788000099", firstName = "R", lastName = "T", passwordHash = "x")
        every { walletRepository.findByUserIdAndType("recipient_1", WalletType.MAIN) } returns wallet("wallet_recipient", "recipient_1", "0")
        every { transactionRepository.save(any()) } answers { firstArg() }
        every { userRepository.findById("sender_topup") } returns Optional.of(
            User(id = "sender_topup", phoneNumber = "+250788000001", firstName = "Eric", lastName = "Uwase", passwordHash = "x"),
        )

        When("the real shortfall top-up succeeds, covering the gap") {
            every { walletRepository.findByUserIdAndType("sender_topup", WalletType.MAIN) } returns wallet("wallet_sender", "sender_topup", "1000")
            every { autoTopUpService.topUpShortfall("sender_topup", "wallet_sender", BigDecimal("1000")) } answers {
                every { walletRepository.findById("wallet_sender") } returns Optional.of(wallet("wallet_sender", "sender_topup", "3000"))
                AutoTopUpTriggerResult(true, "Topped up 2000 RWF to cover the real shortfall")
            }
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_topup_ok", emptyList())

            val (transaction, newBalance) = service.sendDirect("sender_topup", "+250788000099", BigDecimal("2000"), "Rent")

            Then("the real transfer completes -- the real shortfall was covered, not a hard failure") {
                transaction.recipientId shouldBe "recipient_1"
                newBalance shouldBe BigDecimal("3000")
            }
        }

        When("the real shortfall top-up isn't configured or doesn't trigger") {
            every { walletRepository.findByUserIdAndType("sender_topup", WalletType.MAIN) } returns wallet("wallet_sender", "sender_topup", "1000")
            every { autoTopUpService.topUpShortfall("sender_topup", "wallet_sender", BigDecimal("1000")) } returns
                AutoTopUpTriggerResult(false, "No auto top-up setting configured for this wallet")

            Then("it still real-throws InsufficientFundsException -- the real, honest fallback") {
                try {
                    service.sendDirect("sender_topup", "+250788000099", BigDecimal("2000"), "Rent")
                    throw AssertionError("expected InsufficientFundsException")
                } catch (e: InsufficientFundsException) {
                    io.mockk.verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a real sender pushing money to a recipient identified only by real account number") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, walletRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, notificationRepository, roundUpService, familyLinkService, autoTopUpService,
            pushNotificationService,
        )

        every { walletRepository.findByUserIdAndType("sender_2", WalletType.MAIN) } returns wallet("wallet_sender2", "sender_2", "10000")
        every { userRepository.findByPhoneNumber("2024448333") } returns null
        every { walletRepository.findByAccountNumber("2024448333") } returns Wallet(
            id = "wallet_recipient2", userId = "recipient_2", accountNumber = "2024448333", accountName = "Test wallet",
            type = WalletType.MAIN, balance = BigDecimal("0"), availableBalance = BigDecimal("0"),
        )
        every { walletRepository.findById("wallet_sender2") } returns Optional.of(wallet("wallet_sender2", "sender_2", "9000"))
        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_11", emptyList())
        every { transactionRepository.save(any()) } answers { firstArg() }

        When("no user matches that identifier as a phone number, so it falls back to account-number lookup") {
            val (transaction, _) = service.sendDirect("sender_2", "2024448333", BigDecimal("1000"), "")

            Then("it still resolves the real recipient and defaults the description honestly") {
                transaction.recipientId shouldBe "recipient_2"
                transaction.description shouldBe "Transfer - Transfer"
            }
        }
    }

    Given("a real sender sending to an identifier that matches no real itunda account") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, walletRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, notificationRepository, roundUpService, familyLinkService, autoTopUpService,
            pushNotificationService,
        )

        every { walletRepository.findByUserIdAndType("sender_3", WalletType.MAIN) } returns wallet("wallet_sender3", "sender_3", "10000")
        every { userRepository.findByPhoneNumber("+250700000000") } returns null
        every { walletRepository.findByAccountNumber("+250700000000") } returns null

        When("they try to send") {
            Then("it throws P2pRecipientNotFoundException -- a real, honest 404, never a silent no-op") {
                try {
                    service.sendDirect("sender_3", "+250700000000", BigDecimal("1000"), "")
                    error("expected P2pRecipientNotFoundException")
                } catch (e: P2pRecipientNotFoundException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a real sender trying to send money to their own real account") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, walletRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, notificationRepository, roundUpService, familyLinkService, autoTopUpService,
            pushNotificationService,
        )

        val selfUser = User(id = "user_self", phoneNumber = "+250788000077", firstName = "S", lastName = "T", passwordHash = "x")
        every { walletRepository.findByUserIdAndType("user_self", WalletType.MAIN) } returns wallet("wallet_self", "user_self", "10000")
        every { userRepository.findByPhoneNumber("+250788000077") } returns selfUser

        When("they try to send to their own phone number") {
            Then("it throws P2pSelfPaymentException before touching the ledger") {
                try {
                    service.sendDirect("user_self", "+250788000077", BigDecimal("1000"), "")
                    error("expected P2pSelfPaymentException")
                } catch (e: P2pSelfPaymentException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    // Real Toss/Kakao Bank-style recipient-name confirmation ("받는분 성함 확인") -- see
    // P2pService.resolveRecipient's own doc comment for the full sourced account.
    Given("a real caller previewing a recipient by phone number before sending") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, walletRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, notificationRepository, roundUpService, familyLinkService, autoTopUpService,
            pushNotificationService,
        )

        val recipientUser = User(id = "recipient_20", phoneNumber = "+250788000199", firstName = "Alice", lastName = "Mukamana", passwordHash = "x")
        every { userRepository.findByPhoneNumber("+250788000199") } returns recipientUser
        every { walletRepository.findByUserIdAndType("recipient_20", WalletType.MAIN) } returns wallet("wallet_recip20", "recipient_20", "0")
        every { userRepository.findById("recipient_20") } returns Optional.of(recipientUser)

        When("they preview the resolved recipient") {
            val preview = service.resolveRecipient("caller_20", "+250788000199")

            Then("it returns the real account holder's real name, not just an echo of the typed identifier") {
                preview.recipientUserId shouldBe "recipient_20"
                preview.displayName shouldBe "Alice Mukamana"
            }

            Then("no money moves and no ledger call happens for a plain preview") {
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }
    }

    Given("a real caller previewing an identifier that resolves to no real itunda account") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, walletRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, notificationRepository, roundUpService, familyLinkService, autoTopUpService,
            pushNotificationService,
        )

        every { userRepository.findByPhoneNumber("+250700000001") } returns null
        every { walletRepository.findByAccountNumber("+250700000001") } returns null

        When("they preview it, catching a real typo before any money would move") {
            Then("it throws P2pRecipientNotFoundException -- the same real, honest 404 sendDirect itself would give") {
                try {
                    service.resolveRecipient("caller_21", "+250700000001")
                    error("expected P2pRecipientNotFoundException")
                } catch (e: P2pRecipientNotFoundException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a real caller previewing their own phone number") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, walletRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, notificationRepository, roundUpService, familyLinkService, autoTopUpService,
            pushNotificationService,
        )

        val selfUser = User(id = "user_self_22", phoneNumber = "+250788000222", firstName = "S", lastName = "T", passwordHash = "x")
        every { userRepository.findByPhoneNumber("+250788000222") } returns selfUser
        every { walletRepository.findByUserIdAndType("user_self_22", WalletType.MAIN) } returns wallet("wallet_self22", "user_self_22", "10000")

        When("they preview sending to themselves") {
            Then("it throws P2pSelfPaymentException, same real guard sendDirect enforces") {
                try {
                    service.resolveRecipient("user_self_22", "+250788000222")
                    error("expected P2pSelfPaymentException")
                } catch (e: P2pSelfPaymentException) {
                }
            }
        }
    }

    Given("a real caller exceeding the real recipient-preview rate limit") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>()
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, walletRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, notificationRepository, roundUpService, familyLinkService, autoTopUpService,
            pushNotificationService,
        )
        every { rateLimiter.checkLimit("p2p:resolve:caller_23", limit = 40, window = Duration.ofHours(1)) } throws RateLimitExceededException("Too many requests")

        When("they try to preview yet another recipient") {
            Then("it real-propagates RateLimitExceededException before ever touching a real wallet -- closes a real phone-number-enumeration risk") {
                try {
                    service.resolveRecipient("caller_23", "+250788000333")
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { userRepository.findByPhoneNumber(any()) }
                }
            }
        }
    }

    Given("a real sender with insufficient balance sending directly") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, walletRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, notificationRepository, roundUpService, familyLinkService, autoTopUpService,
            pushNotificationService,
        )

        val recipientUser = User(id = "recipient_9", phoneNumber = "+250788000088", firstName = "R", lastName = "T", passwordHash = "x")
        every { walletRepository.findByUserIdAndType("sender_9", WalletType.MAIN) } returns wallet("wallet_poor2", "sender_9", "500")
        every { userRepository.findByPhoneNumber("+250788000088") } returns recipientUser
        every { walletRepository.findByUserIdAndType("recipient_9", WalletType.MAIN) } returns wallet("wallet_recip9", "recipient_9", "0")

        When("they try to send more than they have") {
            Then("it throws InsufficientFundsException before touching the ledger") {
                try {
                    service.sendDirect("sender_9", "+250788000088", BigDecimal("1000"), "")
                    error("expected InsufficientFundsException")
                } catch (e: InsufficientFundsException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
