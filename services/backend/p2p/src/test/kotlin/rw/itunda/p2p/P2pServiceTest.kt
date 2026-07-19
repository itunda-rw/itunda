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
import rw.itunda.core.domain.P2pPaymentRequest
import rw.itunda.core.domain.P2pPaymentRequestStatus
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.P2pPaymentRequestRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
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
        val service = P2pService(p2pPaymentRequestRepository, walletRepository, transactionRepository, ledgerService, fraudRuleEngine, rateLimiter)

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
            val (transaction, newBalance) = service.payRequest("payer_1", "p2p_1")

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
        val service = P2pService(p2pPaymentRequestRepository, walletRepository, transactionRepository, ledgerService, fraudRuleEngine, rateLimiter)

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
        val service = P2pService(p2pPaymentRequestRepository, walletRepository, transactionRepository, ledgerService, fraudRuleEngine, rateLimiter)

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
        val service = P2pService(p2pPaymentRequestRepository, walletRepository, transactionRepository, ledgerService, fraudRuleEngine, rateLimiter)

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
        val service = P2pService(p2pPaymentRequestRepository, walletRepository, transactionRepository, ledgerService, fraudRuleEngine, rateLimiter)
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
        val service = P2pService(p2pPaymentRequestRepository, walletRepository, transactionRepository, ledgerService, fraudRuleEngine, rateLimiter)

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
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
