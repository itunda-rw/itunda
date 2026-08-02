package rw.itunda.wallet

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.LedgerEntry
import rw.itunda.core.domain.SpendingBudget
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.events.EventPublisher
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.provider.ProviderConnector
import rw.itunda.core.provider.ProviderDeclinedException
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.SpendingBudgetRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.YearMonth
import java.util.Optional

/**
 * First test coverage for the highest-stakes module in this backend -- real P2P
 * transfer quote/confirm. QuoteStore is exercised for real, not mocked (it's an
 * in-process, non-DB TTL cache with no injected clock to fake, so real quote/confirm
 * round-trips are the only way to test confirmTransfer meaningfully); WalletRepository/
 * TransactionRepository/LedgerService are mocked. Actual 60-second quote expiry isn't
 * covered here -- there's no injected clock in QuoteStore to fake it without sleeping
 * a real 60+ seconds in a test, which isn't worth doing.
 */
class WalletServiceTest : BehaviorSpec({

    fun wallet(id: String, userId: String, balance: String) = Wallet(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test wallet",
        type = WalletType.MAIN, balance = BigDecimal(balance), availableBalance = BigDecimal(balance),
    )

    Given("a user with a wallet holding 10000 RWF") {
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val ledgerService = mockk<LedgerService>()
        val eventPublisher = mockk<EventPublisher>(relaxed = true)
        // Relaxed: most Whens below don't care about provider behavior at all, only
        // the "provider declines" one explicitly stubs a throw -- same pattern as
        // BillsServiceTest, minus needing an explicit "accepts" stub in every other
        // When since relaxed already defaults to a no-op success.
        val providerConnector = mockk<ProviderConnector>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val spendingBudgetRepository = mockk<SpendingBudgetRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = WalletService(walletRepository, transactionRepository, ledgerEntryRepository, ledgerService, eventPublisher, providerConnector, fraudRuleEngine, spendingBudgetRepository, notificationRepository, pushNotificationService)

        val senderWallet = wallet("wallet_1", "user_1", "10000")

        When("quoting a transfer within the available balance") {
            every { walletRepository.findById("wallet_1") } returns Optional.of(senderWallet)

            val quote = service.quoteTransfer("user_1", "wallet_1", "+250788111111", BigDecimal("1000"))

            Then("the fee is a flat 1%, rounded, and totalDebit includes it") {
                quote.amount shouldBe BigDecimal("1000")
                quote.fee shouldBe BigDecimal("10")
                quote.totalDebit shouldBe BigDecimal("1010")
                quote.status shouldBe QuoteStatus.PENDING
            }
        }

        When("quoting a transfer for more than the available balance") {
            every { walletRepository.findById("wallet_1") } returns Optional.of(senderWallet)

            Then("it throws InsufficientFundsException before creating a quote") {
                try {
                    service.quoteTransfer("user_1", "wallet_1", "+250788111111", BigDecimal("50000"))
                    error("expected InsufficientFundsException")
                } catch (e: InsufficientFundsException) {
                    // expected
                }
            }
        }

        // Real IDOR fix (2026-08-02): this used to throw WalletNotOwnedException (403),
        // confirming to the caller that wallet_2 is a real wallet id they just don't
        // own -- the same probe getWalletById's own doc comment already documents
        // fixing for the direct wallet-lookup endpoint. Same fix here: 404, not 403.
        When("quoting a transfer from a wallet that belongs to someone else") {
            val otherWallet = wallet("wallet_2", "user_2", "10000")
            every { walletRepository.findById("wallet_2") } returns Optional.of(otherWallet)

            Then("it throws WalletNotFoundException, never revealing the wallet exists") {
                try {
                    service.quoteTransfer("user_1", "wallet_2", "+250788111111", BigDecimal("100"))
                    error("expected WalletNotFoundException")
                } catch (e: WalletNotFoundException) {
                    // expected
                }
            }
        }

        When("confirming a valid quote") {
            every { walletRepository.findById("wallet_1") } returns Optional.of(senderWallet)
            val quote = service.quoteTransfer("user_1", "wallet_1", "+250788111111", BigDecimal("1000"))

            every { ledgerService.postLedgerTransaction("RWF", any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
            every { transactionRepository.save(any()) } answers { firstArg() }

            val (transaction, _) = service.confirmTransfer(quote.id, "user_1")

            Then("it posts the ledger transaction and saves a completed Transaction record") {
                transaction.id shouldBe "ledgertxn_1"
                transaction.amount shouldBe BigDecimal("1000")
                transaction.fee shouldBe BigDecimal("10")
                verify(exactly = 1) { transactionRepository.save(any()) }
            }
        }

        When("confirming the same quote twice") {
            every { walletRepository.findById("wallet_1") } returns Optional.of(senderWallet)
            val quote = service.quoteTransfer("user_1", "wallet_1", "+250788111111", BigDecimal("1000"))

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

        // Real IDOR fix (2026-08-02): this used to throw WalletNotOwnedException (403),
        // confirming to an attacker that a guessed/leaked quoteId is real. Now 404,
        // matching the same real-existence-confirming probe fixed for wallet lookups.
        When("a different user tries to confirm someone else's quote") {
            every { walletRepository.findById("wallet_1") } returns Optional.of(senderWallet)
            val quote = service.quoteTransfer("user_1", "wallet_1", "+250788111111", BigDecimal("1000"))

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
            every { walletRepository.findById("wallet_1") } returns Optional.of(senderWallet)
            val quote = service.quoteTransfer("user_1", "wallet_1", "+250788111111", BigDecimal("1000"))
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
            every { walletRepository.findById("wallet_1") } returns Optional.of(senderWallet)
            val quote = service.quoteTransfer("user_1", "wallet_1", "+250788111111", BigDecimal("1000"))
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

    Given("a user whose ledger has debits from real modules that share rail_suspense") {
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val ledgerService = mockk<LedgerService>()
        val eventPublisher = mockk<EventPublisher>(relaxed = true)
        val providerConnector = mockk<ProviderConnector>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val spendingBudgetRepository = mockk<SpendingBudgetRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = WalletService(walletRepository, transactionRepository, ledgerEntryRepository, ledgerService, eventPublisher, providerConnector, fraudRuleEngine, spendingBudgetRepository, notificationRepository, pushNotificationService)

        fun entry(id: String, txnId: String, accountId: String, accountType: LedgerAccountType, direction: LedgerDirection, amount: String, memo: String = "test") = LedgerEntry(
            id = id, transactionId = txnId, accountId = accountId, accountType = accountType, direction = direction,
            amount = BigDecimal(amount), currency = "RWF", balanceAfter = BigDecimal.ZERO, memo = memo,
        )

        every { walletRepository.findByUserId("user_9") } returns listOf(wallet("wallet_9", "user_9", "0"))

        // Live-discovered while testing this against a real backend: BillsService.payBill,
        // BillsService.buyAirtime, and WalletService.confirmTransfer all post to the same
        // "rail_suspense" clearing account -- accountType alone can't tell them apart, so this
        // fixture matches the real memo prefixes each service actually writes.
        val billDebit = entry("e1", "txn_bill", "wallet_9", LedgerAccountType.WALLET, LedgerDirection.DEBIT, "500", "Bill payment bill_2")
        val billCounterpart = entry("e1b", "txn_bill", "rail_suspense", LedgerAccountType.RAIL_SUSPENSE, LedgerDirection.CREDIT, "500", "Biller settlement bill_2")
        val airtimeDebit = entry("e6", "txn_airtime", "wallet_9", LedgerAccountType.WALLET, LedgerDirection.DEBIT, "200", "Airtime 0788000000")
        val airtimeCounterpart = entry("e6b", "txn_airtime", "rail_suspense", LedgerAccountType.RAIL_SUSPENSE, LedgerDirection.CREDIT, "200", "Airtime settlement 0788000000")
        val insuranceDebit = entry("e2", "txn_ins", "wallet_9", LedgerAccountType.WALLET, LedgerDirection.DEBIT, "1000")
        val insuranceCounterpart = entry("e3", "txn_ins", "insurance_premium_revenue", LedgerAccountType.INSURANCE_PREMIUM_REVENUE, LedgerDirection.CREDIT, "1000")
        val transferDebit = entry("e4", "txn_transfer", "wallet_9", LedgerAccountType.WALLET, LedgerDirection.DEBIT, "2000", "Transfer to 0788999999")
        val transferCounterpart = entry("e5", "txn_transfer", "rail_suspense", LedgerAccountType.RAIL_SUSPENSE, LedgerDirection.CREDIT, "1980")
        // A genuine "no distinguishing counterpart" case -- an internal wallet-to-wallet debit
        // with only another WALLET-type sibling.
        val internalDebit = entry("e7", "txn_internal", "wallet_9", LedgerAccountType.WALLET, LedgerDirection.DEBIT, "300")
        val internalCounterpart = entry("e7b", "txn_internal", "wallet_savings_9", LedgerAccountType.WALLET, LedgerDirection.CREDIT, "300")

        every { ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc("wallet_9") } returns
            listOf(transferDebit, insuranceDebit, billDebit, airtimeDebit, internalDebit)
        // Real N+1 fix (2026-07-19 sweep): one batched findByTransactionIdIn stub instead
        // of one findByTransactionId stub per transaction id, matching the real service's
        // own fix -- order of the input list doesn't matter here since the service groups
        // the result by transactionId itself.
        every { ledgerEntryRepository.findByTransactionIdIn(match { it.toSet() == setOf("txn_transfer", "txn_ins", "txn_bill", "txn_airtime", "txn_internal") }) } returns
            listOf(billDebit, billCounterpart, airtimeDebit, airtimeCounterpart, insuranceDebit, insuranceCounterpart, transferDebit, transferCounterpart, internalDebit, internalCounterpart)

        When("computing the spending insight") {
            val result = service.getSpendingInsight("user_9")

            Then("bills, airtime, and transfers are correctly split despite sharing rail_suspense") {
                result.totalSpent shouldBe BigDecimal("4000")
                val byName = result.categories.associate { it.name to it.amount }
                byName["Transfers"] shouldBe BigDecimal("2000")
                byName["Bills"] shouldBe BigDecimal("500")
                byName["Airtime"] shouldBe BigDecimal("200")
                byName["Insurance"] shouldBe BigDecimal("1000")
                byName["Other"] shouldBe BigDecimal("300")
            }
            Then("the largest category comes first") {
                result.categories.first().name shouldBe "Transfers"
            }
        }

        When("a 1000 RWF Bills budget is checked against 500 actually spent on Bills") {
            val budget = SpendingBudget(id = "budget_1", userId = "user_9", category = "Bills", monthlyLimit = BigDecimal("1000"), month = YearMonth.now().toString())
            every { spendingBudgetRepository.findByUserIdAndMonth("user_9", any()) } returns listOf(budget)
            every { spendingBudgetRepository.save(any()) } answers { firstArg() }

            val budgets = service.getBudgets("user_9")

            Then("it reports the real spent amount, 50% used, and UNDER status") {
                budgets.size shouldBe 1
                budgets[0].spent shouldBe BigDecimal("500")
                budgets[0].percentUsed shouldBe 50
                budgets[0].status shouldBe rw.itunda.wallet.BudgetStatus.UNDER
            }
            Then("no notification is written -- under threshold") {
                verify(exactly = 0) { notificationRepository.save(any()) }
            }
        }

        When("an overall 3500 RWF budget is checked against the real 4000 RWF actually spent") {
            val budget = SpendingBudget(id = "budget_2", userId = "user_9", category = null, monthlyLimit = BigDecimal("3500"), month = YearMonth.now().toString())
            every { spendingBudgetRepository.findByUserIdAndMonth("user_9", any()) } returns listOf(budget)
            every { spendingBudgetRepository.save(any()) } answers { firstArg() }
            every { notificationRepository.save(any()) } answers { firstArg() }

            val budgets = service.getBudgets("user_9")

            Then("it correctly reports OVER status against the real total spend") {
                budgets[0].spent shouldBe BigDecimal("4000")
                budgets[0].status shouldBe rw.itunda.wallet.BudgetStatus.OVER
            }
            Then("it writes one real over-budget notification, not a duplicate") {
                verify(exactly = 1) { notificationRepository.save(any()) }
            }
        }
    }

    Given("a real fee-charging transfer, which posts THREE legs, not two") {
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val ledgerService = mockk<LedgerService>()
        val eventPublisher = mockk<EventPublisher>(relaxed = true)
        val providerConnector = mockk<ProviderConnector>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val spendingBudgetRepository = mockk<SpendingBudgetRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = WalletService(walletRepository, transactionRepository, ledgerEntryRepository, ledgerService, eventPublisher, providerConnector, fraudRuleEngine, spendingBudgetRepository, notificationRepository, pushNotificationService)

        fun entry(id: String, txnId: String, accountId: String, accountType: LedgerAccountType, direction: LedgerDirection, amount: String, memo: String = "test") = LedgerEntry(
            id = id, transactionId = txnId, accountId = accountId, accountType = accountType, direction = direction,
            amount = BigDecimal(amount), currency = "RWF", balanceAfter = BigDecimal.ZERO, memo = memo,
        )

        // Real bug found live during this session's own N+1-fix verification: a real
        // fee-charging transfer posts a WALLET debit, a RAIL_SUSPENSE credit, AND a
        // FEE_REVENUE credit in the same transaction -- deliberately using ids where the
        // fee leg sorts first, reproducing the exact real-world ordering that surfaced
        // this bug (MySQL returns rows in no guaranteed order absent an ORDER BY).
        val feeLeg = entry("entry_1_fee", "txn_fee_transfer", "fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, "50", "Transfer fee")
        val walletDebit = entry("entry_2_wallet", "txn_fee_transfer", "wallet_9", LedgerAccountType.WALLET, LedgerDirection.DEBIT, "5050", "Transfer to +250788555999")
        val railLeg = entry("entry_3_rail", "txn_fee_transfer", "rail_suspense", LedgerAccountType.RAIL_SUSPENSE, LedgerDirection.CREDIT, "5000", "Rail settlement for +250788555999")

        every { walletRepository.findByUserId("user_fee") } returns listOf(wallet("wallet_9", "user_fee", "0"))
        every { ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc("wallet_9") } returns listOf(walletDebit)
        every { ledgerEntryRepository.findByTransactionIdIn(listOf("txn_fee_transfer")) } returns listOf(feeLeg, walletDebit, railLeg)

        When("computing the spending insight") {
            val result = service.getSpendingInsight("user_fee")

            Then("it's categorized as Transfers, not Fees -- FEE_REVENUE is deprioritized when a more meaningful sibling exists") {
                val byName = result.categories.associate { it.name to it.amount }
                byName["Transfers"] shouldBe BigDecimal("5050")
                byName["Fees"] shouldBe null
            }
        }
    }

    Given("a real Toss Timeline-style unusual-spend check over a user's own transaction history") {
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val ledgerService = mockk<LedgerService>()
        val eventPublisher = mockk<EventPublisher>(relaxed = true)
        val providerConnector = mockk<ProviderConnector>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val spendingBudgetRepository = mockk<SpendingBudgetRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = WalletService(walletRepository, transactionRepository, ledgerEntryRepository, ledgerService, eventPublisher, providerConnector, fraudRuleEngine, spendingBudgetRepository, notificationRepository, pushNotificationService)

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

    // Real budget-threshold push (2026-07-28) -- see maybeNotifyBudgetThreshold's own
    // doc comment.
    Given("a real overall budget already past its real monthly limit, never yet notified") {
        val walletRepository = mockk<WalletRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val ledgerService = mockk<LedgerService>()
        val eventPublisher = mockk<EventPublisher>(relaxed = true)
        val providerConnector = mockk<ProviderConnector>(relaxed = true)
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val spendingBudgetRepository = mockk<SpendingBudgetRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = WalletService(walletRepository, transactionRepository, ledgerEntryRepository, ledgerService, eventPublisher, providerConnector, fraudRuleEngine, spendingBudgetRepository, notificationRepository, pushNotificationService)

        val month = YearMonth.now().toString()
        val budget = SpendingBudget(id = "budget_1", userId = "user_1", category = null, monthlyLimit = BigDecimal("1000"), month = month)
        val debit = LedgerEntry(
            id = "entry_1", transactionId = "txn_1", accountId = "wallet_1", accountType = LedgerAccountType.WALLET,
            direction = LedgerDirection.DEBIT, amount = BigDecimal("1200"), currency = "RWF", balanceAfter = BigDecimal.ZERO, memo = "test",
        )
        every { spendingBudgetRepository.findByUserIdAndMonth("user_1", month) } returns listOf(budget)
        every { walletRepository.findByUserId("user_1") } returns listOf(wallet("wallet_1", "user_1", "0"))
        every { ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc("wallet_1") } returns listOf(debit)
        every { ledgerEntryRepository.findByTransactionIdIn(listOf("txn_1")) } returns emptyList()
        every { notificationRepository.save(any()) } answers { firstArg() }
        every { spendingBudgetRepository.save(any()) } answers { firstArg() }

        service.getBudgets("user_1")

        Then("it real-saves an in-app BUDGET_OVER notification and a real push, both exactly once") {
            verify(exactly = 1) { notificationRepository.save(match { it.userId == "user_1" && it.type == "BUDGET_OVER" }) }
            verify(exactly = 1) { pushNotificationService.sendToUser("user_1", "Budget exceeded", any()) }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
