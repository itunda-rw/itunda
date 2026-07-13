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
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.events.EventPublisher
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.provider.ProviderConnector
import rw.itunda.core.provider.ProviderDeclinedException
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
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
        val service = WalletService(walletRepository, transactionRepository, ledgerEntryRepository, ledgerService, eventPublisher, providerConnector, fraudRuleEngine)

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

        When("quoting a transfer from a wallet that belongs to someone else") {
            val otherWallet = wallet("wallet_2", "user_2", "10000")
            every { walletRepository.findById("wallet_2") } returns Optional.of(otherWallet)

            Then("it throws WalletNotOwnedException rather than quoting against it") {
                try {
                    service.quoteTransfer("user_1", "wallet_2", "+250788111111", BigDecimal("100"))
                    error("expected WalletNotOwnedException")
                } catch (e: WalletNotOwnedException) {
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

        When("a different user tries to confirm someone else's quote") {
            every { walletRepository.findById("wallet_1") } returns Optional.of(senderWallet)
            val quote = service.quoteTransfer("user_1", "wallet_1", "+250788111111", BigDecimal("1000"))

            Then("it throws WalletNotOwnedException and never touches the ledger") {
                try {
                    service.confirmTransfer(quote.id, "attacker")
                    error("expected WalletNotOwnedException")
                } catch (e: WalletNotOwnedException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
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
        val service = WalletService(walletRepository, transactionRepository, ledgerEntryRepository, ledgerService, eventPublisher, providerConnector, fraudRuleEngine)

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
        every { ledgerEntryRepository.findByTransactionId("txn_bill") } returns listOf(billDebit, billCounterpart)
        every { ledgerEntryRepository.findByTransactionId("txn_airtime") } returns listOf(airtimeDebit, airtimeCounterpart)
        every { ledgerEntryRepository.findByTransactionId("txn_ins") } returns listOf(insuranceDebit, insuranceCounterpart)
        every { ledgerEntryRepository.findByTransactionId("txn_transfer") } returns listOf(transferDebit, transferCounterpart)
        every { ledgerEntryRepository.findByTransactionId("txn_internal") } returns listOf(internalDebit, internalCounterpart)

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
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
