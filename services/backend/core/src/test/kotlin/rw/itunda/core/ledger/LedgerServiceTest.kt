package rw.itunda.core.ledger

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.LedgerAccount
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.events.EventPublisher
import rw.itunda.core.repository.LedgerAccountRepository
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.util.Optional

/**
 * First real test coverage for the ledger — the most critical piece of this backend
 * (previously zero tests existed anywhere in spring-backend). Written Kotest-style
 * (Given/When/Then + MockK) to match Toss's own documented Kotlin testing convention
 * (toss.tech/article/test-strategy-server), not JUnit+Mockito.
 */
class LedgerServiceTest : BehaviorSpec({

    fun wallet(id: String, balance: String) = Wallet(
        id = id, userId = "user_1", accountNumber = "ACC-$id", accountName = "Test wallet",
        type = WalletType.MAIN, balance = BigDecimal(balance), availableBalance = BigDecimal(balance),
    )

    Given("a wallet with 1000 RWF available and a fee_revenue clearing account") {
        val walletRepository = mockk<WalletRepository>()
        val ledgerAccountRepository = mockk<LedgerAccountRepository>()
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        // relaxed: this test suite is about ledger balance correctness, not event
        // publishing (see EventPublisherTest / LedgerService's own wiring comment) --
        // a relaxed mock lets publishAfterCommit calls no-op without stubbing each one.
        val eventPublisher = mockk<EventPublisher>(relaxed = true)
        val service = LedgerService(walletRepository, ledgerAccountRepository, ledgerEntryRepository, eventPublisher)

        val sourceWallet = wallet("wallet_1", "1000")
        val feeAccount = LedgerAccount(id = "fee_revenue", name = "Fee Revenue")

        every { walletRepository.findByIdForUpdate("wallet_1") } returns Optional.of(sourceWallet)
        every { ledgerAccountRepository.findByIdForUpdate("fee_revenue") } returns Optional.of(feeAccount)
        every { ledgerEntryRepository.saveAll(any<List<rw.itunda.core.domain.LedgerEntry>>()) } answers { firstArg() }

        When("posting a balanced 100 RWF debit/credit pair") {
            val result = service.postLedgerTransaction(
                "RWF",
                listOf(
                    LedgerLeg("wallet_1", LedgerAccountType.WALLET, LedgerDirection.DEBIT, BigDecimal("100"), "test debit"),
                    LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, BigDecimal("100"), "test credit"),
                ),
            )

            Then("the wallet balance is debited") {
                sourceWallet.balance shouldBe BigDecimal("900")
            }
            Then("the clearing account is credited") {
                feeAccount.balance shouldBe BigDecimal("100")
            }
            Then("both entries are persisted under the same transaction id") {
                result.entries.map { it.transactionId }.toSet().size shouldBe 1
                verify(exactly = 1) { ledgerEntryRepository.saveAll(any<List<rw.itunda.core.domain.LedgerEntry>>()) }
            }
        }

        When("posting legs that don't balance (debits != credits)") {
            Then("it throws LedgerImbalanceException before touching any balance") {
                try {
                    service.postLedgerTransaction(
                        "RWF",
                        listOf(
                            LedgerLeg("wallet_1", LedgerAccountType.WALLET, LedgerDirection.DEBIT, BigDecimal("100"), "debit"),
                            LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, BigDecimal("50"), "credit"),
                        ),
                    )
                    error("expected LedgerImbalanceException")
                } catch (e: LedgerImbalanceException) {
                    sourceWallet.balance shouldBe BigDecimal("1000")
                }
            }
        }

        When("debiting more than the wallet's available balance") {
            Then("it throws InsufficientFundsException before touching any balance") {
                try {
                    service.postLedgerTransaction(
                        "RWF",
                        listOf(
                            LedgerLeg("wallet_1", LedgerAccountType.WALLET, LedgerDirection.DEBIT, BigDecimal("5000"), "debit"),
                            LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, BigDecimal("5000"), "credit"),
                        ),
                    )
                    error("expected InsufficientFundsException")
                } catch (e: InsufficientFundsException) {
                    sourceWallet.balance shouldBe BigDecimal("1000")
                }
            }
        }
    }

    Given("a frozen wallet (isActive = false), same as SupportService's real account-takeover response") {
        val walletRepository = mockk<WalletRepository>()
        val ledgerAccountRepository = mockk<LedgerAccountRepository>()
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val eventPublisher = mockk<EventPublisher>(relaxed = true)
        val service = LedgerService(walletRepository, ledgerAccountRepository, ledgerEntryRepository, eventPublisher)

        val frozenWallet = wallet("wallet_frozen", "1000").apply { isActive = false }
        val feeAccount = LedgerAccount(id = "fee_revenue", name = "Fee Revenue")

        every { walletRepository.findByIdForUpdate("wallet_frozen") } returns Optional.of(frozenWallet)
        every { ledgerAccountRepository.findByIdForUpdate("fee_revenue") } returns Optional.of(feeAccount)
        every { ledgerEntryRepository.saveAll(any<List<rw.itunda.core.domain.LedgerEntry>>()) } answers { firstArg() }

        When("attempting to debit it") {
            Then("it throws WalletFrozenException before touching any balance") {
                try {
                    service.postLedgerTransaction(
                        "RWF",
                        listOf(
                            LedgerLeg("wallet_frozen", LedgerAccountType.WALLET, LedgerDirection.DEBIT, BigDecimal("100"), "debit"),
                            LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, BigDecimal("100"), "credit"),
                        ),
                    )
                    error("expected WalletFrozenException")
                } catch (e: WalletFrozenException) {
                    frozenWallet.balance shouldBe BigDecimal("1000")
                }
            }
        }

        When("crediting it (e.g. a refund arriving while frozen)") {
            val result = service.postLedgerTransaction(
                "RWF",
                listOf(
                    LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.DEBIT, BigDecimal("50"), "debit"),
                    LedgerLeg("wallet_frozen", LedgerAccountType.WALLET, LedgerDirection.CREDIT, BigDecimal("50"), "credit"),
                ),
            )
            Then("it succeeds -- a frozen account can still receive money") {
                frozenWallet.balance shouldBe BigDecimal("1050")
                result.entries.size shouldBe 2
            }
        }
    }
}) {
    // Default Kotest behavior shares one spec instance (and therefore the mutable
    // `sourceWallet`/`feeAccount` fixtures) across every sibling When/Then under a
    // Given — without this, the successful-transfer test's mutation of sourceWallet
    // leaks into the imbalance/insufficient-funds tests below it. InstancePerLeaf gives
    // every leaf test its own fresh fixture, so each starts from the real 1000 balance.
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
