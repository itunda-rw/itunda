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
import rw.itunda.core.domain.FraudFlag
import rw.itunda.core.domain.FraudRule
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.P2pPaymentRequest
import rw.itunda.core.domain.P2pPaymentRequestStatus
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.P2pPaymentRequestRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.family.FamilyLinkService
import rw.itunda.savings.RoundUpService
import rw.itunda.account.AutoTopUpService
import rw.itunda.account.AutoTopUpTriggerResult
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.Optional

class P2pServiceTest : BehaviorSpec({

    fun account(id: String, userId: String, balance: String) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal(balance), availableBalance = BigDecimal(balance),
    )

    Given("a real pending payment request from a requester with a real account") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val p2pNotificationService = mockk<P2pNotificationService>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val p2pTransferLimitService = mockk<P2pTransferLimitService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, accountRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, roundUpService, familyLinkService, autoTopUpService,
            p2pTransferLimitService, p2pNotificationService,
        )

        val request = P2pPaymentRequest(id = "p2p_1", requesterUserId = "requester_1", amount = BigDecimal("2000"), description = "Lunch", expiresAt = Instant.now().plusSeconds(900))
        every { p2pPaymentRequestRepository.findById("p2p_1") } returns Optional.of(request)
        every { accountRepository.findByUserIdAndType("payer_1", AccountType.MAIN) } returns account("account_payer", "payer_1", "10000")
        every { accountRepository.findByUserIdAndType("requester_1", AccountType.MAIN) } returns account("account_requester", "requester_1", "0")
        every { accountRepository.findById("account_payer") } returns Optional.of(account("account_payer", "payer_1", "8000"))
        // Real Toss-parity fix (2026-08-23): payRequest now also notifies the payer
        // themselves (notifyMoneySent), which looks the requester up by id to name them
        // in the payer's own confirmation copy.
        every { userRepository.findById("requester_1") } returns Optional.of(
            User(id = "requester_1", phoneNumber = "+250788000099", firstName = "Alice", lastName = "M", passwordHash = "x"),
        )
        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { transactionRepository.save(any()) } answers { firstArg() }
        every { p2pPaymentRequestRepository.save(any()) } answers { firstArg() }

        When("a different real user pays it") {
            every { userRepository.findById("payer_1") } returns Optional.of(
                User(id = "payer_1", phoneNumber = "+250788000001", firstName = "Jean", lastName = "Paul", passwordHash = "x"),
            )
            val (transaction, newBalance) = service.payRequest("payer_1", "p2p_1")

            // Real-time "money received"/"money sent" notifications (2026-07-22,
            // 2026-08-23) -- the actual message-formatting/push-sending logic now lives
            // in P2pNotificationService (see P2pNotificationServiceTest.kt for real
            // content-shape coverage); this just verifies payRequest hands off to it
            // with the right real arguments.
            Then("the requester gets a real, immediate notification naming the real payer") {
                verify(exactly = 1) { p2pNotificationService.notifyMoneyReceived("requester_1", "payer_1", BigDecimal("2000")) }
            }

            // Real Toss-parity fix (2026-08-23, real user-supplied Toss screenshot): the
            // PAYER now gets their own confirmation push too -- see
            // P2pService.notifyMoneySent's own doc comment.
            Then("the payer also gets their own real confirmation of their outgoing payment") {
                verify(exactly = 1) { p2pNotificationService.notifyMoneySent("payer_1", any(), "requester_1", BigDecimal("2000")) }
            }

            Then("it's a direct account-to-account ledger pair -- no rail_suspense hop, no fee, unlike a regular transfer") {
                legsSlot.captured.size shouldBe 2
                val debitLeg = legsSlot.captured.first { it.direction == LedgerDirection.DEBIT }
                debitLeg.accountId shouldBe "account_payer"
                debitLeg.accountType shouldBe LedgerAccountType.WALLET
                debitLeg.amount shouldBe BigDecimal("2000")
                val creditLeg = legsSlot.captured.first { it.direction == LedgerDirection.CREDIT }
                creditLeg.accountId shouldBe "account_requester"
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

            // Real repo-wide FraudRuleEngine-verify re-sweep (2026-09-09): unlike
            // sendDirect (which returns fraudRuleEngine.evaluate's result directly, so a
            // deleted call would already fail an existing return-value assertion),
            // payRequest's own evaluate() call at line ~172 is a fire-and-forget side
            // effect -- its return value is never used, so nothing in this file
            // previously verified it fires at all. A real regression silently deleting
            // it would have gone undetected.
            Then("the real fraud engine is evaluated with the real payer/requester/amount/transaction, not silently skipped") {
                verify(exactly = 1) { fraudRuleEngine.evaluate("payer_1", "requester_1", BigDecimal("2000"), transaction.id) }
            }

            // Real gap found live (2026-09-14, sibling-asymmetry sweep): this method
            // already got the p2pTransferLimitService fix for the identical "QR-pay
            // loophole" shape, but never got sendDirect's own family-spend-limit call.
            Then("the real family daily spend limit is enforced, not silently skipped") {
                verify(exactly = 1) { familyLinkService.enforceSpendLimit("payer_1", "account_payer", BigDecimal("2000")) }
            }
        }
    }

    Given("a requester trying to pay their own request") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val p2pNotificationService = mockk<P2pNotificationService>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val p2pTransferLimitService = mockk<P2pTransferLimitService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, accountRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, roundUpService, familyLinkService, autoTopUpService,
            p2pTransferLimitService, p2pNotificationService,
        )

        val request = P2pPaymentRequest(id = "p2p_2", requesterUserId = "user_5", amount = BigDecimal("1000"), description = "test", expiresAt = Instant.now().plusSeconds(900))
        every { p2pPaymentRequestRepository.findById("p2p_2") } returns Optional.of(request)

        When("they try to pay it") {
            Then("it throws P2pSelfPaymentException before touching any account") {
                try {
                    service.payRequest("user_5", "p2p_2")
                    error("expected P2pSelfPaymentException")
                } catch (e: P2pSelfPaymentException) {
                    verify(exactly = 0) { accountRepository.findByUserIdAndType(any(), any()) }
                }
            }
        }
    }

    Given("an expired payment request") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val p2pNotificationService = mockk<P2pNotificationService>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val p2pTransferLimitService = mockk<P2pTransferLimitService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, accountRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, roundUpService, familyLinkService, autoTopUpService,
            p2pTransferLimitService, p2pNotificationService,
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
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val p2pNotificationService = mockk<P2pNotificationService>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val p2pTransferLimitService = mockk<P2pTransferLimitService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, accountRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, roundUpService, familyLinkService, autoTopUpService,
            p2pTransferLimitService, p2pNotificationService,
        )

        val request = P2pPaymentRequest(id = "p2p_4", requesterUserId = "requester_3", amount = BigDecimal("5000"), description = "test", expiresAt = Instant.now().plusSeconds(900))
        every { p2pPaymentRequestRepository.findById("p2p_4") } returns Optional.of(request)
        every { accountRepository.findByUserIdAndType("payer_3", AccountType.MAIN) } returns account("account_poor", "payer_3", "1000")
        every { accountRepository.findByUserIdAndType("requester_3", AccountType.MAIN) } returns account("account_req3", "requester_3", "0")

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
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>()
        val userRepository = mockk<UserRepository>()
        val p2pNotificationService = mockk<P2pNotificationService>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val p2pTransferLimitService = mockk<P2pTransferLimitService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, accountRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, roundUpService, familyLinkService, autoTopUpService,
            p2pTransferLimitService, p2pNotificationService,
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
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>()
        val userRepository = mockk<UserRepository>()
        val p2pNotificationService = mockk<P2pNotificationService>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val p2pTransferLimitService = mockk<P2pTransferLimitService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, accountRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, roundUpService, familyLinkService, autoTopUpService,
            p2pTransferLimitService, p2pNotificationService,
        )

        val request = P2pPaymentRequest(id = "p2p_5", requesterUserId = "requester_4", amount = BigDecimal("1000"), description = "test", expiresAt = Instant.now().plusSeconds(900))
        every { p2pPaymentRequestRepository.findById("p2p_5") } returns Optional.of(request)
        every { rateLimiter.checkLimit("p2p:pay:payer_9", limit = 30, window = Duration.ofHours(1)) } throws RateLimitExceededException("Too many requests")

        When("they try to pay") {
            Then("it real-propagates RateLimitExceededException before ever touching a real account") {
                try {
                    service.payRequest("payer_9", "p2p_5")
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { accountRepository.findByUserIdAndType(any(), any()) }
                }
            }
        }
    }

    Given("a real sender pushing money directly to a real recipient by phone number") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val p2pNotificationService = mockk<P2pNotificationService>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val p2pTransferLimitService = mockk<P2pTransferLimitService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, accountRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, roundUpService, familyLinkService, autoTopUpService,
            p2pTransferLimitService, p2pNotificationService,
        )

        val recipientUser = User(id = "recipient_1", phoneNumber = "+250788000099", firstName = "R", lastName = "T", passwordHash = "x")
        every { accountRepository.findByUserIdAndType("sender_1", AccountType.MAIN) } returns account("account_sender", "sender_1", "10000")
        every { userRepository.findByPhoneNumber("+250788000099") } returns recipientUser
        every { accountRepository.findByUserIdAndType("recipient_1", AccountType.MAIN) } returns account("account_recipient", "recipient_1", "0")
        every { accountRepository.findById("account_sender") } returns Optional.of(account("account_sender", "sender_1", "8000"))
        // Real Toss-parity fix (2026-08-23): sendDirect now also notifies the sender
        // themselves (notifyMoneySent), which looks the recipient up by id to name them
        // in the sender's own confirmation copy.
        every { userRepository.findById("recipient_1") } returns Optional.of(recipientUser)
        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_10", emptyList())
        every { transactionRepository.save(any()) } answers { firstArg() }

        When("they send by the real recipient's phone number") {
            every { userRepository.findById("sender_1") } returns Optional.of(
                User(id = "sender_1", phoneNumber = "+250788000001", firstName = "Eric", lastName = "Uwase", passwordHash = "x"),
            )
            val (transaction, newBalance) = service.sendDirect("sender_1", "+250788000099", BigDecimal("2000"), "Rent")

            Then("it's a direct account-to-account ledger pair, no fee, real recipientId not \"external\"") {
                legsSlot.captured.size shouldBe 2
                val debitLeg = legsSlot.captured.first { it.direction == LedgerDirection.DEBIT }
                debitLeg.accountId shouldBe "account_sender"
                debitLeg.amount shouldBe BigDecimal("2000")
                val creditLeg = legsSlot.captured.first { it.direction == LedgerDirection.CREDIT }
                creditLeg.accountId shouldBe "account_recipient"
                creditLeg.amount shouldBe BigDecimal("2000")
                transaction.recipientId shouldBe "recipient_1"
                transaction.fee shouldBe BigDecimal.ZERO
                newBalance shouldBe BigDecimal("8000")
            }

            // Real-time "money received"/"money sent" notifications -- the actual
            // message-formatting/push-sending logic now lives in P2pNotificationService
            // (see P2pNotificationServiceTest.kt for real content-shape coverage); this
            // just verifies sendDirect hands off to it with the right real arguments,
            // direct transfers getting the same real-time alert payRequest does.
            Then("the recipient gets a real, immediate notification naming the real sender") {
                verify(exactly = 1) { p2pNotificationService.notifyMoneyReceived("recipient_1", "sender_1", BigDecimal("2000")) }
            }

            // Real Toss-parity fix (2026-08-23, real user-supplied Toss screenshot): the
            // SENDER now gets their own confirmation push too, not just the recipient --
            // see P2pService.notifyMoneySent's own doc comment for the corrected,
            // previously-unsourced "recipient-only" assumption this closes.
            Then("the real sender also gets their own real confirmation of their outgoing transfer") {
                verify(exactly = 1) { p2pNotificationService.notifyMoneySent("sender_1", any(), "recipient_1", BigDecimal("2000")) }
            }

            Then("it real-checks the real FamilyLink spend limit before the ledger moves any money") {
                io.mockk.verify(exactly = 1) { familyLinkService.enforceSpendLimit("sender_1", "account_sender", BigDecimal("2000")) }
            }

            Then("it real-checks the real Section 186 transfer-limit gate before the ledger moves any money") {
                io.mockk.verify(exactly = 1) { p2pTransferLimitService.enforce("sender_1", "account_sender", BigDecimal("2000")) }
            }
        }
    }

    // Real Korean "이체한도" (transfer limit) enforcement (Section 186) -- see
    // P2pTransferLimitService's own doc comment for the full sourced account. Uses the
    // REAL P2pTransferLimitService (not mocked) against a mocked TransactionRepository,
    // so this exercises the actual per-transfer/daily-cumulative comparison logic, not
    // just that sendDirect calls some mock.
    Given("a real sender pushing a single transfer past the real per-transfer limit") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val p2pNotificationService = mockk<P2pNotificationService>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val p2pTransferLimitService = P2pTransferLimitService(transactionRepository, accountRepository)
        val service = P2pService(
            p2pPaymentRequestRepository, accountRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, roundUpService, familyLinkService, autoTopUpService,
            p2pTransferLimitService, p2pNotificationService,
        )

        every { accountRepository.findByUserIdAndType("sender_lim1", AccountType.MAIN) } returns account("account_lim1", "sender_lim1", "10000000")
        every { accountRepository.findByIdForUpdate("account_lim1") } returns Optional.empty()
        every { userRepository.findByPhoneNumber("+250788000199") } returns
            User(id = "recipient_lim1", phoneNumber = "+250788000199", firstName = "R", lastName = "T", passwordHash = "x")
        every { accountRepository.findByUserIdAndType("recipient_lim1", AccountType.MAIN) } returns account("account_recipient_lim1", "recipient_lim1", "0")
        every { transactionRepository.findBySenderIdAndTypeAndStatusAndCreatedAtGreaterThanEqual(eq("sender_lim1"), any(), any(), any()) } returns emptyList()

        When("they try to send more than the real 500,000 RWF per-transfer cap in one call") {
            Then("it real-blocks with P2pTransferLimitExceededException before the ledger is ever touched") {
                try {
                    service.sendDirect("sender_lim1", "+250788000199", BigDecimal("600000"), "")
                    throw AssertionError("expected P2pTransferLimitExceededException")
                } catch (e: P2pTransferLimitExceededException) {
                    io.mockk.verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a real sender whose earlier real transfers today already used up most of the real daily cap") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val p2pNotificationService = mockk<P2pNotificationService>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val p2pTransferLimitService = P2pTransferLimitService(transactionRepository, accountRepository)
        val service = P2pService(
            p2pPaymentRequestRepository, accountRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, roundUpService, familyLinkService, autoTopUpService,
            p2pTransferLimitService, p2pNotificationService,
        )

        every { accountRepository.findByUserIdAndType("sender_lim2", AccountType.MAIN) } returns account("account_lim2", "sender_lim2", "10000000")
        every { accountRepository.findByIdForUpdate("account_lim2") } returns Optional.empty()
        every { userRepository.findByPhoneNumber("+250788000299") } returns
            User(id = "recipient_lim2", phoneNumber = "+250788000299", firstName = "R", lastName = "T", passwordHash = "x")
        every { accountRepository.findByUserIdAndType("recipient_lim2", AccountType.MAIN) } returns account("account_recipient_lim2", "recipient_lim2", "0")
        // Real sender already sent 2,400,000 RWF today (under the real 500,000
        // per-transfer cap on each individual prior send, but close to the real
        // 2,500,000 daily cumulative cap).
        val priorTransfer = rw.itunda.core.domain.Transaction(
            id = "txn_prior", referenceNumber = "P2PTXNPRIOR", senderId = "sender_lim2", recipientId = "someone_else",
            fromAccountId = "account_lim2", toAccountId = "account_other",
            amount = BigDecimal("2400000"), fee = BigDecimal.ZERO, currency = "RWF", type = rw.itunda.core.domain.TransactionType.TRANSFER,
            status = rw.itunda.core.domain.TransactionStatus.COMPLETED, description = "Earlier today", completedAt = Instant.now(),
        )
        every { transactionRepository.findBySenderIdAndTypeAndStatusAndCreatedAtGreaterThanEqual(eq("sender_lim2"), any(), any(), any()) } returns listOf(priorTransfer)

        When("they try to send another 200,000 RWF, which would push today's real total past the 2,500,000 daily cap") {
            Then("it real-blocks with P2pTransferLimitExceededException before the ledger is ever touched, even though this single transfer is well under the per-transfer cap") {
                try {
                    service.sendDirect("sender_lim2", "+250788000299", BigDecimal("200000"), "")
                    throw AssertionError("expected P2pTransferLimitExceededException")
                } catch (e: P2pTransferLimitExceededException) {
                    io.mockk.verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("they send exactly the remaining 100,000 RWF headroom instead") {
            Then("it real-succeeds -- the real daily cap is inclusive, not exclusive, of the exact remaining amount") {
                every { accountRepository.findById("account_lim2") } returns Optional.of(account("account_lim2", "sender_lim2", "9900000"))
                val legsSlot = slot<List<LedgerLeg>>()
                every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_lim2", emptyList())
                every { transactionRepository.save(any()) } answers { firstArg() }
                every { userRepository.findById("sender_lim2") } returns Optional.of(
                    User(id = "sender_lim2", phoneNumber = "+250788000009", firstName = "S", lastName = "L", passwordHash = "x"),
                )

                service.sendDirect("sender_lim2", "+250788000299", BigDecimal("100000"), "")

                legsSlot.captured.size shouldBe 2
            }
        }
    }

    // Real fix (2026-09-06, Pay product-completeness pass) -- see
    // P2pTransferLimitService's own doc comment: payRequest (QR-pay) now gets the
    // exact same real per-transfer cap sendDirect already enforces above, closing a
    // real gap where an identical amount sent via QR was unbounded. Uses the REAL
    // P2pTransferLimitService, same style as the sendDirect limit tests above.
    Given("a real pending payment request for more than the real per-transfer limit") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val p2pNotificationService = mockk<P2pNotificationService>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val p2pTransferLimitService = P2pTransferLimitService(transactionRepository, accountRepository)
        val service = P2pService(
            p2pPaymentRequestRepository, accountRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, roundUpService, familyLinkService, autoTopUpService,
            p2pTransferLimitService, p2pNotificationService,
        )

        val request = P2pPaymentRequest(id = "p2p_lim3", requesterUserId = "requester_lim3", amount = BigDecimal("600000"), description = "Rent", expiresAt = Instant.now().plusSeconds(900))
        every { p2pPaymentRequestRepository.findById("p2p_lim3") } returns Optional.of(request)
        every { accountRepository.findByUserIdAndType("payer_lim3", AccountType.MAIN) } returns account("account_payer_lim3", "payer_lim3", "10000000")
        every { accountRepository.findByUserIdAndType("requester_lim3", AccountType.MAIN) } returns account("account_requester_lim3", "requester_lim3", "0")
        every { accountRepository.findByIdForUpdate("account_payer_lim3") } returns Optional.empty()
        every { transactionRepository.findBySenderIdAndTypeAndStatusAndCreatedAtGreaterThanEqual(eq("payer_lim3"), any(), any(), any()) } returns emptyList()

        When("a real user tries to pay it via QR") {
            Then("it real-blocks with P2pTransferLimitExceededException before the ledger is ever touched -- the same cap sendDirect enforces, not a QR-pay loophole") {
                try {
                    service.payRequest("payer_lim3", "p2p_lim3")
                    throw AssertionError("expected P2pTransferLimitExceededException")
                } catch (e: P2pTransferLimitExceededException) {
                    io.mockk.verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a real linked child whose transfer would exceed their real guardian-set daily spend limit") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val p2pNotificationService = mockk<P2pNotificationService>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>()
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val p2pTransferLimitService = mockk<P2pTransferLimitService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, accountRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, roundUpService, familyLinkService, autoTopUpService,
            p2pTransferLimitService, p2pNotificationService,
        )

        every { accountRepository.findByUserIdAndType("child_1", AccountType.MAIN) } returns account("account_child", "child_1", "10000")
        every { userRepository.findByPhoneNumber("+250788000099") } returns
            User(id = "recipient_1", phoneNumber = "+250788000099", firstName = "R", lastName = "T", passwordHash = "x")
        every { accountRepository.findByUserIdAndType("recipient_1", AccountType.MAIN) } returns account("account_recipient", "recipient_1", "0")
        every { familyLinkService.enforceSpendLimit("child_1", "account_child", BigDecimal("2000")) } throws
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

    // Real gap found live (2026-09-14, sibling-asymmetry sweep): payRequest (QR-pay)
    // never got sendDirect's own real FamilyLinkService.enforceSpendLimit call, so a
    // guardian-linked child could bypass their real daily spend limit entirely by
    // paying a QR code / payment request instead of using Send Money.
    Given("a real linked child whose QR-pay would exceed their real guardian-set daily spend limit") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val p2pNotificationService = mockk<P2pNotificationService>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>()
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val p2pTransferLimitService = mockk<P2pTransferLimitService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, accountRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, roundUpService, familyLinkService, autoTopUpService,
            p2pTransferLimitService, p2pNotificationService,
        )

        val request = P2pPaymentRequest(id = "p2p_child_1", requesterUserId = "requester_1", amount = BigDecimal("2000"), description = "Lunch", expiresAt = Instant.now().plusSeconds(900))
        every { p2pPaymentRequestRepository.findById("p2p_child_1") } returns Optional.of(request)
        every { accountRepository.findByUserIdAndType("child_1", AccountType.MAIN) } returns account("account_child", "child_1", "10000")
        every { accountRepository.findByUserIdAndType("requester_1", AccountType.MAIN) } returns account("account_requester", "requester_1", "0")
        every { familyLinkService.enforceSpendLimit("child_1", "account_child", BigDecimal("2000")) } throws
            rw.itunda.family.FamilySpendLimitExceededException("This transfer would exceed your real daily spend limit set by your guardian")

        When("the child tries to pay a QR/payment request past their real limit") {
            Then("it's real-blocked before the transfer-limit check or the ledger are ever touched") {
                try {
                    service.payRequest("child_1", "p2p_child_1")
                    throw AssertionError("expected FamilySpendLimitExceededException")
                } catch (e: rw.itunda.family.FamilySpendLimitExceededException) {
                    io.mockk.verify(exactly = 0) { p2pTransferLimitService.enforce(any(), any(), any()) }
                    io.mockk.verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    // Real Naver Pay Money shortfall auto top-up (2026-07-27) -- see
    // AutoTopUpService.topUpShortfall's own doc comment.
    Given("a real sender with insufficient balance but a real enabled auto top-up setting") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val p2pNotificationService = mockk<P2pNotificationService>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>()
        val p2pTransferLimitService = mockk<P2pTransferLimitService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, accountRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, roundUpService, familyLinkService, autoTopUpService,
            p2pTransferLimitService, p2pNotificationService,
        )

        every { userRepository.findByPhoneNumber("+250788000099") } returns
            User(id = "recipient_1", phoneNumber = "+250788000099", firstName = "R", lastName = "T", passwordHash = "x")
        every { accountRepository.findByUserIdAndType("recipient_1", AccountType.MAIN) } returns account("account_recipient", "recipient_1", "0")
        every { transactionRepository.save(any()) } answers { firstArg() }
        every { userRepository.findById("sender_topup") } returns Optional.of(
            User(id = "sender_topup", phoneNumber = "+250788000001", firstName = "Eric", lastName = "Uwase", passwordHash = "x"),
        )

        When("the real shortfall top-up succeeds, covering the gap") {
            every { accountRepository.findByUserIdAndType("sender_topup", AccountType.MAIN) } returns account("account_sender", "sender_topup", "1000")
            every { autoTopUpService.topUpShortfall("sender_topup", "account_sender", BigDecimal("1000")) } answers {
                every { accountRepository.findById("account_sender") } returns Optional.of(account("account_sender", "sender_topup", "3000"))
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
            every { accountRepository.findByUserIdAndType("sender_topup", AccountType.MAIN) } returns account("account_sender", "sender_topup", "1000")
            every { autoTopUpService.topUpShortfall("sender_topup", "account_sender", BigDecimal("1000")) } returns
                AutoTopUpTriggerResult(false, "No auto top-up setting configured for this account")

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
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val p2pNotificationService = mockk<P2pNotificationService>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val p2pTransferLimitService = mockk<P2pTransferLimitService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, accountRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, roundUpService, familyLinkService, autoTopUpService,
            p2pTransferLimitService, p2pNotificationService,
        )

        every { accountRepository.findByUserIdAndType("sender_2", AccountType.MAIN) } returns account("account_sender2", "sender_2", "10000")
        every { userRepository.findByPhoneNumber("2024448333") } returns null
        every { accountRepository.findByAccountNumber("2024448333") } returns Account(
            id = "account_recipient2", userId = "recipient_2", accountNumber = "2024448333", accountName = "Test account",
            type = AccountType.MAIN, balance = BigDecimal("0"), availableBalance = BigDecimal("0"),
        )
        every { accountRepository.findById("account_sender2") } returns Optional.of(account("account_sender2", "sender_2", "9000"))
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

    // Real gap found live (2026-08-31, Toss security research thread, matching Toss's
    // own real "Fraud Suspicion Siren"): FraudRuleEngine.evaluate's result used to be
    // discarded at this exact call site -- nothing ever told the sender their transfer
    // tripped a fraud heuristic. Asserts sendDirect now threads the real flags through
    // unchanged (P2pController turns them into the sender-facing warning strings).
    Given("a real sender whose transfer trips a fraud heuristic") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val p2pNotificationService = mockk<P2pNotificationService>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val p2pTransferLimitService = mockk<P2pTransferLimitService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, accountRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, roundUpService, familyLinkService, autoTopUpService,
            p2pTransferLimitService, p2pNotificationService,
        )

        every { accountRepository.findByUserIdAndType("sender_flag", AccountType.MAIN) } returns account("account_sender_flag", "sender_flag", "500000")
        every { accountRepository.findById("account_sender_flag") } returns Optional.of(account("account_sender_flag", "sender_flag", "300000"))
        every { userRepository.findByPhoneNumber("+250788000555") } returns
            User(id = "recipient_flag", phoneNumber = "+250788000555", firstName = "R", lastName = "T", passwordHash = "x")
        every { accountRepository.findByUserIdAndType("recipient_flag", AccountType.MAIN) } returns
            account("account_recipient_flag", "recipient_flag", "0")
        val flag = FraudFlag(id = "flag_1", userId = "sender_flag", transactionId = "any", rule = FraudRule.NEW_RECIPIENT, description = "First time sending to this recipient", amount = BigDecimal("200000"))
        every { fraudRuleEngine.evaluate("sender_flag", "recipient_flag", BigDecimal("200000"), any()) } returns listOf(flag)
        every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_flag", emptyList())
        every { transactionRepository.save(any()) } answers { firstArg() }

        When("they send to a genuinely new recipient") {
            val (_, _, fraudFlags) = service.sendDirect("sender_flag", "+250788000555", BigDecimal("200000"), "")

            Then("the real flag the engine raised is returned, not silently discarded") {
                fraudFlags shouldBe listOf(flag)
            }
        }
    }

    Given("a real sender explicitly choosing a non-MAIN account to send from") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val p2pNotificationService = mockk<P2pNotificationService>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val p2pTransferLimitService = mockk<P2pTransferLimitService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, accountRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, roundUpService, familyLinkService, autoTopUpService,
            p2pTransferLimitService, p2pNotificationService,
        )

        every { accountRepository.findById("account_fx1") } returns Optional.of(account("account_fx1", "sender_fx", "5000"))
        every { userRepository.findByPhoneNumber("+250788000099") } returns
            User(id = "recipient_fx", phoneNumber = "+250788000099", firstName = "R", lastName = "T", passwordHash = "x")
        every { accountRepository.findByUserIdAndType("recipient_fx", AccountType.MAIN) } returns
            account("account_recipient_fx", "recipient_fx", "0")
        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_fx", emptyList())
        every { transactionRepository.save(any()) } answers { firstArg() }

        When("they pass fromAccountId for a real account they own") {
            val (transaction, _) = service.sendDirect("sender_fx", "+250788000099", BigDecimal("1000"), "", "account_fx1")

            Then("it debits that specific account, not their MAIN account") {
                transaction.fromAccountId shouldBe "account_fx1"
                legsSlot.captured.first { it.direction == LedgerDirection.DEBIT }.accountId shouldBe "account_fx1"
            }
        }
    }

    Given("a real sender passing a fromAccountId that belongs to someone else") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val p2pNotificationService = mockk<P2pNotificationService>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val p2pTransferLimitService = mockk<P2pTransferLimitService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, accountRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, roundUpService, familyLinkService, autoTopUpService,
            p2pTransferLimitService, p2pNotificationService,
        )

        every { accountRepository.findById("account_not_mine") } returns Optional.of(account("account_not_mine", "someone_else", "5000"))

        When("they try to send from it") {
            Then("it throws P2pNoAccountException -- never lets a sender debit an account they don't own") {
                try {
                    service.sendDirect("sender_fx2", "+250788000099", BigDecimal("1000"), "", "account_not_mine")
                    error("expected P2pNoAccountException")
                } catch (e: P2pNoAccountException) {
                    e.message shouldBe "No account found for this account"
                }
            }
        }
    }

    Given("a real sender sending to an identifier that matches no real itunda account") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val p2pNotificationService = mockk<P2pNotificationService>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val p2pTransferLimitService = mockk<P2pTransferLimitService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, accountRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, roundUpService, familyLinkService, autoTopUpService,
            p2pTransferLimitService, p2pNotificationService,
        )

        every { accountRepository.findByUserIdAndType("sender_3", AccountType.MAIN) } returns account("account_sender3", "sender_3", "10000")
        every { userRepository.findByPhoneNumber("+250700000000") } returns null
        every { accountRepository.findByAccountNumber("+250700000000") } returns null

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
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val p2pNotificationService = mockk<P2pNotificationService>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val p2pTransferLimitService = mockk<P2pTransferLimitService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, accountRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, roundUpService, familyLinkService, autoTopUpService,
            p2pTransferLimitService, p2pNotificationService,
        )

        val selfUser = User(id = "user_self", phoneNumber = "+250788000077", firstName = "S", lastName = "T", passwordHash = "x")
        every { accountRepository.findByUserIdAndType("user_self", AccountType.MAIN) } returns account("account_self", "user_self", "10000")
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
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val p2pNotificationService = mockk<P2pNotificationService>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val p2pTransferLimitService = mockk<P2pTransferLimitService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, accountRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, roundUpService, familyLinkService, autoTopUpService,
            p2pTransferLimitService, p2pNotificationService,
        )

        val recipientUser = User(id = "recipient_20", phoneNumber = "+250788000199", firstName = "Alice", lastName = "Mukamana", passwordHash = "x")
        every { userRepository.findByPhoneNumber("+250788000199") } returns recipientUser
        every { accountRepository.findByUserIdAndType("recipient_20", AccountType.MAIN) } returns account("account_recip20", "recipient_20", "0")
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
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val p2pNotificationService = mockk<P2pNotificationService>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val p2pTransferLimitService = mockk<P2pTransferLimitService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, accountRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, roundUpService, familyLinkService, autoTopUpService,
            p2pTransferLimitService, p2pNotificationService,
        )

        every { userRepository.findByPhoneNumber("+250700000001") } returns null
        every { accountRepository.findByAccountNumber("+250700000001") } returns null

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
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val p2pNotificationService = mockk<P2pNotificationService>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val p2pTransferLimitService = mockk<P2pTransferLimitService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, accountRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, roundUpService, familyLinkService, autoTopUpService,
            p2pTransferLimitService, p2pNotificationService,
        )

        val selfUser = User(id = "user_self_22", phoneNumber = "+250788000222", firstName = "S", lastName = "T", passwordHash = "x")
        every { userRepository.findByPhoneNumber("+250788000222") } returns selfUser
        every { accountRepository.findByUserIdAndType("user_self_22", AccountType.MAIN) } returns account("account_self22", "user_self_22", "10000")

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
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>()
        val userRepository = mockk<UserRepository>()
        val p2pNotificationService = mockk<P2pNotificationService>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val p2pTransferLimitService = mockk<P2pTransferLimitService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, accountRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, roundUpService, familyLinkService, autoTopUpService,
            p2pTransferLimitService, p2pNotificationService,
        )
        every { rateLimiter.checkLimit("p2p:resolve:caller_23", limit = 40, window = Duration.ofHours(1)) } throws RateLimitExceededException("Too many requests")

        When("they try to preview yet another recipient") {
            Then("it real-propagates RateLimitExceededException before ever touching a real account -- closes a real phone-number-enumeration risk") {
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
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val p2pNotificationService = mockk<P2pNotificationService>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val p2pTransferLimitService = mockk<P2pTransferLimitService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, accountRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, roundUpService, familyLinkService, autoTopUpService,
            p2pTransferLimitService, p2pNotificationService,
        )

        val recipientUser = User(id = "recipient_9", phoneNumber = "+250788000088", firstName = "R", lastName = "T", passwordHash = "x")
        every { accountRepository.findByUserIdAndType("sender_9", AccountType.MAIN) } returns account("account_poor2", "sender_9", "500")
        every { userRepository.findByPhoneNumber("+250788000088") } returns recipientUser
        every { accountRepository.findByUserIdAndType("recipient_9", AccountType.MAIN) } returns account("account_recip9", "recipient_9", "0")

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

    // Real proactive-expiry gap (Bank product-completeness pass, cycle 2, 2026-09-09) --
    // see P2pService.getRequestsDueForExpiryCheck/markExpired's own doc comments.
    Given("a real pending payment request whose expiresAt has already passed") {
        val p2pPaymentRequestRepository = mockk<P2pPaymentRequestRepository>()
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val p2pNotificationService = mockk<P2pNotificationService>(relaxed = true)
        val roundUpService = mockk<RoundUpService>(relaxed = true)
        val familyLinkService = mockk<FamilyLinkService>(relaxed = true)
        val autoTopUpService = mockk<AutoTopUpService>(relaxed = true)
        val p2pTransferLimitService = mockk<P2pTransferLimitService>(relaxed = true)
        val service = P2pService(
            p2pPaymentRequestRepository, accountRepository, userRepository, transactionRepository, ledgerService,
            fraudRuleEngine, rateLimiter, roundUpService, familyLinkService, autoTopUpService,
            p2pTransferLimitService, p2pNotificationService,
        )
        val expiredRequest = P2pPaymentRequest(
            id = "p2p_expiring", requesterUserId = "requester_10", amount = BigDecimal("3000"),
            description = "Lunch", expiresAt = Instant.now().minusSeconds(60),
        )
        every { p2pPaymentRequestRepository.findByStatusAndExpiresAtBefore(P2pPaymentRequestStatus.PENDING, any()) } returns listOf(expiredRequest)
        every { p2pPaymentRequestRepository.save(any()) } answers { firstArg() }

        When("the scheduler asks which requests are due for expiry") {
            val due = service.getRequestsDueForExpiryCheck()

            Then("it returns the expired-but-still-PENDING request") {
                due shouldBe listOf(expiredRequest)
            }
        }

        When("markExpired is called") {
            service.markExpired(expiredRequest)

            Then("the request's status flips to EXPIRED, is saved, and the requester is notified") {
                expiredRequest.status shouldBe P2pPaymentRequestStatus.EXPIRED
                verify(exactly = 1) { p2pPaymentRequestRepository.save(expiredRequest) }
                verify(exactly = 1) { p2pNotificationService.notifyRequestExpired("requester_10", BigDecimal("3000")) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
