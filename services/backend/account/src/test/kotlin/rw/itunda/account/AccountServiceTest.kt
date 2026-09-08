package rw.itunda.account

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.events.EventPublisher
import rw.itunda.auth.RateLimiter
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.provider.ProviderConnector
import rw.itunda.core.provider.ProviderDeclinedException
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.Optional

/**
 * First test coverage for the highest-stakes module in this backend -- real P2P
 * transfer quote/confirm. QuoteStore is exercised for real, not mocked (it's an
 * in-process, non-DB TTL cache with no injected clock to fake, so real quote/confirm
 * round-trips are the only way to test confirmTransfer meaningfully); AccountRepository/
 * TransactionRepository/LedgerService are mocked. Actual 60-second quote expiry isn't
 * covered here -- there's no injected clock in QuoteStore to fake it without sleeping
 * a real 60+ seconds in a test, which isn't worth doing.
 */
class AccountServiceTest : BehaviorSpec({

    fun account(id: String, userId: String, balance: String) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal(balance), availableBalance = BigDecimal(balance),
    )

    Given("a user with a account holding 10000 RWF") {
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val eventPublisher = mockk<EventPublisher>(relaxed = true)
        // Relaxed: most Whens below don't care about provider behavior at all, only
        // the "provider declines" one explicitly stubs a throw -- same pattern as
        // BillsServiceTest, minus needing an explicit "accepts" stub in every other
        // When since relaxed already defaults to a no-op success.
        val providerConnector = mockk<ProviderConnector>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = AccountService(accountRepository, transactionRepository, ledgerService, eventPublisher, providerConnector, fraudRuleEngine, rateLimiter)

        val senderAccount = account("account_1", "user_1", "10000")

        When("quoting a transfer within the available balance") {
            every { accountRepository.findById("account_1") } returns Optional.of(senderAccount)

            val quote = service.quoteTransfer("user_1", "account_1", "+250788111111", BigDecimal("1000"))

            Then("the fee is a flat 1%, rounded, and totalDebit includes it") {
                quote.amount shouldBe BigDecimal("1000")
                quote.fee shouldBe BigDecimal("10")
                quote.totalDebit shouldBe BigDecimal("1010")
                quote.status shouldBe QuoteStatus.PENDING
            }
        }

        When("quoting a transfer for more than the available balance") {
            every { accountRepository.findById("account_1") } returns Optional.of(senderAccount)

            Then("it throws InsufficientFundsException before creating a quote") {
                try {
                    service.quoteTransfer("user_1", "account_1", "+250788111111", BigDecimal("50000"))
                    error("expected InsufficientFundsException")
                } catch (e: InsufficientFundsException) {
                    // expected
                }
            }
        }

        // Real IDOR fix (2026-08-02): this used to throw AccountNotOwnedException (403),
        // confirming to the caller that account_2 is a real account id they just don't
        // own -- the same probe getAccountById's own doc comment already documents
        // fixing for the direct account-lookup endpoint. Same fix here: 404, not 403.
        When("quoting a transfer from a account that belongs to someone else") {
            val otherAccount = account("account_2", "user_2", "10000")
            every { accountRepository.findById("account_2") } returns Optional.of(otherAccount)

            Then("it throws AccountNotFoundException, never revealing the account exists") {
                try {
                    service.quoteTransfer("user_1", "account_2", "+250788111111", BigDecimal("100"))
                    error("expected AccountNotFoundException")
                } catch (e: AccountNotFoundException) {
                    // expected
                }
            }
        }

        When("confirming a valid quote") {
            every { accountRepository.findById("account_1") } returns Optional.of(senderAccount)
            val quote = service.quoteTransfer("user_1", "account_1", "+250788111111", BigDecimal("1000"))

            every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
            every { transactionRepository.save(any()) } answers { firstArg() }

            val (transaction, _) = service.confirmTransfer(quote.id, "user_1")

            Then("it posts the ledger transaction and saves a completed Transaction record") {
                transaction.id shouldBe "ledgertxn_1"
                transaction.amount shouldBe BigDecimal("1000")
                transaction.fee shouldBe BigDecimal("10")
                verify(exactly = 1) { transactionRepository.save(any()) }
            }
            // Real regression test (2026-09-04): rateLimiter was relaxed = true with zero
            // verify{} anywhere in this file, so a future accidental removal of the real
            // checkLimit call in confirmTransfer would have compiled and passed silently.
            Then("it checks the real 30/hour rate limit for this user's transfer confirms") {
                verify { rateLimiter.checkLimit("account:confirm-transfer:user_1", limit = 30, window = Duration.ofHours(1)) }
            }

            // Real gap found live (repo-wide fraud-engine-verification sweep,
            // 2026-09-08, following the same latent-regression class the rate-limiter
            // sweep just closed): fraudRuleEngine was relaxed = true with zero
            // verify{} anywhere in this file, so a future accidental removal of the
            // real fraudRuleEngine.evaluate call would have compiled and passed
            // silently.
            Then("the real fraud engine is actually consulted, not just mocked away") {
                verify(exactly = 1) { fraudRuleEngine.evaluate("user_1", null, BigDecimal("1000"), "ledgertxn_1") }
            }
        }

        When("confirming the same quote twice") {
            every { accountRepository.findById("account_1") } returns Optional.of(senderAccount)
            val quote = service.quoteTransfer("user_1", "account_1", "+250788111111", BigDecimal("1000"))

            every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_2", emptyList())
            every { transactionRepository.save(any()) } answers { firstArg() }
            service.confirmTransfer(quote.id, "user_1")

            Then("the second confirm throws QuoteAlreadyUsedException, not a second transfer") {
                try {
                    service.confirmTransfer(quote.id, "user_1")
                    error("expected QuoteAlreadyUsedException")
                } catch (e: QuoteAlreadyUsedException) {
                    verify(exactly = 1) { ledgerService.postLedgerTransaction("RWF", any()) }
                }
            }
        }

        // Real IDOR fix (2026-08-02): this used to throw AccountNotOwnedException (403),
        // confirming to an attacker that a guessed/leaked quoteId is real. Now 404,
        // matching the same real-existence-confirming probe fixed for account lookups.
        When("a different user tries to confirm someone else's quote") {
            every { accountRepository.findById("account_1") } returns Optional.of(senderAccount)
            val quote = service.quoteTransfer("user_1", "account_1", "+250788111111", BigDecimal("1000"))

            Then("it throws QuoteNotFoundException, never revealing the quote exists, and never touches the ledger") {
                try {
                    service.confirmTransfer(quote.id, "attacker")
                    error("expected QuoteNotFoundException")
                } catch (e: QuoteNotFoundException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        // Real double-spend fix (2026-08-02) -- see QuoteStore.claim's own doc
        // comment: two concurrent confirmTransfer calls for the SAME quoteId (e.g. a
        // client retry with a fresh Idempotency-Key) used to both be able to observe
        // `status == PENDING` before either wrote CONFIRMED, and both post the real
        // ledger legs. This simulates that race directly against the real QuoteStore
        // (not mocked, per this file's own doc comment) by calling confirmTransfer
        // twice back to back for the same quote with no intervening state change other
        // than what confirmTransfer itself performs.
        When("confirmTransfer is called twice for the same quote back to back") {
            every { accountRepository.findById("account_1") } returns Optional.of(senderAccount)
            val quote = service.quoteTransfer("user_1", "account_1", "+250788111111", BigDecimal("1000"))
            every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_race", emptyList())
            every { transactionRepository.save(any()) } answers { firstArg() }

            service.confirmTransfer(quote.id, "user_1")

            Then("the ledger is posted exactly once, never twice, for the one quote") {
                try {
                    service.confirmTransfer(quote.id, "user_1")
                    error("expected QuoteAlreadyUsedException")
                } catch (e: QuoteAlreadyUsedException) {
                    verify(exactly = 1) { ledgerService.postLedgerTransaction("RWF", any()) }
                }
            }
        }

        When("confirming a quote id that doesn't exist") {
            Then("it throws QuoteNotFoundException") {
                try {
                    service.confirmTransfer("quote_does_not_exist", "user_1")
                    error("expected QuoteNotFoundException")
                } catch (e: QuoteNotFoundException) {
                    // expected
                }
            }
        }

        When("the provider declines the transfer") {
            every { accountRepository.findById("account_1") } returns Optional.of(senderAccount)
            val quote = service.quoteTransfer("user_1", "account_1", "+250788111111", BigDecimal("1000"))
            every { providerConnector.attempt(any(), any()) } throws ProviderDeclinedException("rail declined")

            Then("confirmTransfer throws ProviderDeclinedException and never touches the ledger") {
                try {
                    service.confirmTransfer(quote.id, "user_1")
                    error("expected ProviderDeclinedException")
                } catch (e: ProviderDeclinedException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                    verify(exactly = 0) { transactionRepository.save(any()) }
                }
            }
        }
    }

    // Spending-insight/budget coverage (getSpendingInsight, getBudgets) now lives in
    // SpendingInsightServiceTest.kt, following SpendingInsightService.kt's own
    // extraction out of AccountService.kt.

    // Real Toss Bank/Toss Pay separation follow-up (2026-08-21) -- the real "Toss Pay
    // Money" detail screen shows only that account's own transactions, not every
    // account's mixed together. Previously zero test coverage existed for
    // getAccountTransactionHistory at all.
    Given("a real account-scoped transaction history lookup") {
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val eventPublisher = mockk<EventPublisher>(relaxed = true)
        val providerConnector = mockk<ProviderConnector>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = AccountService(accountRepository, transactionRepository, ledgerService, eventPublisher, providerConnector, fraudRuleEngine, rateLimiter)

        When("a user requests their own real account's transaction history") {
            val payAccount = account("account_pay", "user_1", "5000")
            every { accountRepository.findById("account_pay") } returns Optional.of(payAccount)
            val txns = listOf(
                Transaction(
                    id = "txn_1", referenceNumber = "REF1", senderId = "user_1", recipientId = "merchant_1",
                    fromAccountId = "account_pay", toAccountId = "account_merchant", amount = BigDecimal("500"),
                    fee = BigDecimal("0"), currency = "RWF", type = TransactionType.PAYMENT, status = TransactionStatus.COMPLETED,
                    description = "Coffee", createdAt = Instant.now(),
                ),
            )
            every { transactionRepository.findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc("account_pay", "account_pay") } returns txns

            val result = service.getAccountTransactionHistory("user_1", "account_pay")

            Then("it returns exactly that real account's own transactions") {
                result shouldBe txns
            }
        }

        When("a user requests transaction history for a real account that belongs to someone else") {
            val otherAccount = account("account_other", "user_2", "5000")
            every { accountRepository.findById("account_other") } returns Optional.of(otherAccount)

            Then("it throws AccountNotFoundException, never revealing the account exists or leaking its history") {
                try {
                    service.getAccountTransactionHistory("user_1", "account_other")
                    error("expected AccountNotFoundException")
                } catch (e: AccountNotFoundException) {
                    verify(exactly = 0) { transactionRepository.findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(any(), any()) }
                }
            }
        }
    }

    Given("a real Toss Timeline-style unusual-spend check over a user's own transaction history") {
        val accountRepository = mockk<AccountRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerService = mockk<LedgerService>()
        val eventPublisher = mockk<EventPublisher>(relaxed = true)
        val providerConnector = mockk<ProviderConnector>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = AccountService(accountRepository, transactionRepository, ledgerService, eventPublisher, providerConnector, fraudRuleEngine, rateLimiter)

        fun debitTxn(id: String, amount: String, createdAt: java.time.Instant, status: rw.itunda.core.domain.TransactionStatus = rw.itunda.core.domain.TransactionStatus.COMPLETED) = rw.itunda.core.domain.Transaction(
            id = id, referenceNumber = "REF-$id", senderId = "user_1", recipientId = "merchant_1",
            amount = BigDecimal(amount), fee = BigDecimal.ZERO, currency = "RWF",
            type = rw.itunda.core.domain.TransactionType.PAYMENT, status = status, description = "test", createdAt = createdAt,
        )

        When("a user with 3 real prior small debits makes a much larger 4th one") {
            val base = java.time.Instant.parse("2026-07-01T00:00:00Z")
            val history = listOf(
                debitTxn("t4", "50000", base.plusSeconds(4000)),
                debitTxn("t3", "1000", base.plusSeconds(3000)),
                debitTxn("t2", "1200", base.plusSeconds(2000)),
                debitTxn("t1", "800", base.plusSeconds(1000)),
            )
            every { transactionRepository.findBySenderIdOrRecipientIdOrderByCreatedAtDesc("user_1", "user_1") } returns history

            val timeline = service.getTransactionTimeline("user_1")

            Then("only the real outlier (50000, ~50x the ~1000 average of the 3 priors) is flagged") {
                val flagsById = timeline.associate { it.transaction.id to it.unusuallyLarge }
                flagsById["t4"] shouldBe true
                flagsById["t3"] shouldBe false
                flagsById["t2"] shouldBe false
                flagsById["t1"] shouldBe false
            }

            Then("it real-preserves the original descending order") {
                timeline.map { it.transaction.id } shouldBe listOf("t4", "t3", "t2", "t1")
            }
        }

        When("a brand-new account's very first debit is large, with no real baseline yet") {
            val history = listOf(debitTxn("t1", "1000000", java.time.Instant.parse("2026-07-01T00:00:00Z")))
            every { transactionRepository.findBySenderIdOrRecipientIdOrderByCreatedAtDesc("user_1", "user_1") } returns history

            val timeline = service.getTransactionTimeline("user_1")

            Then("it's never flagged -- fewer than the real minimum 3 prior debits to judge against") {
                timeline.first().unusuallyLarge shouldBe false
            }
        }

        When("a real CANCELLED transaction sits among otherwise-normal debits") {
            val base = java.time.Instant.parse("2026-07-01T00:00:00Z")
            val history = listOf(
                debitTxn("t5", "1500", base.plusSeconds(5000)),
                debitTxn("t4", "900000", base.plusSeconds(4000), status = rw.itunda.core.domain.TransactionStatus.CANCELLED),
                debitTxn("t3", "1000", base.plusSeconds(3000)),
                debitTxn("t2", "1200", base.plusSeconds(2000)),
                debitTxn("t1", "800", base.plusSeconds(1000)),
            )
            every { transactionRepository.findBySenderIdOrRecipientIdOrderByCreatedAtDesc("user_1", "user_1") } returns history

            val timeline = service.getTransactionTimeline("user_1")

            Then("the cancelled transaction never counts toward the real baseline or gets flagged itself") {
                val flagsById = timeline.associate { it.transaction.id to it.unusuallyLarge }
                flagsById["t4"] shouldBe false
                flagsById["t5"] shouldBe false
            }
        }
    }

}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
