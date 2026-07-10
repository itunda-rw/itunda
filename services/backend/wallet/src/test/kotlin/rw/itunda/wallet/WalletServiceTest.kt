package rw.itunda.wallet

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
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
        val ledgerService = mockk<LedgerService>()
        val service = WalletService(walletRepository, transactionRepository, ledgerService)

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
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
