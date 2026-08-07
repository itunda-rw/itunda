package rw.itunda.wallet

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.fx.ForeignCurrencyRateClient
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.CurrencyConversionRepository
import rw.itunda.core.repository.WalletRepository
import rw.itunda.core.wallet.AccountNumberGenerator
import java.math.BigDecimal
import java.util.Optional

/**
 * First test coverage for the real 토스뱅크 외화통장 (foreign-currency account)
 * equivalent -- see ForeignCurrencyWalletService's own doc comment.
 */
class ForeignCurrencyWalletServiceTest : BehaviorSpec({

    fun wallet(id: String, userId: String, type: WalletType, currency: String = "RWF") = Wallet(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test wallet",
        type = type, balance = BigDecimal("100000"), availableBalance = BigDecimal("100000"), currency = currency,
    )

    Given("a real user opening a foreign-currency account for the first time") {
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateClient = mockk<ForeignCurrencyRateClient>()
        val currencyConversionRepository = mockk<CurrencyConversionRepository>()
        val accountNumberGenerator = mockk<AccountNumberGenerator>(relaxed = true)
        val service = ForeignCurrencyWalletService(walletRepository, ledgerService, rateClient, currencyConversionRepository, accountNumberGenerator)

        val mainWallet = wallet("wallet_main", "user_1", WalletType.MAIN)
        every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns mainWallet
        every { walletRepository.findByIdForUpdate("wallet_main") } returns Optional.of(mainWallet)
        every { walletRepository.findByUserIdAndTypeAndCurrency("user_1", WalletType.FOREIGN_CURRENCY, "USD") } returns null
        every { walletRepository.save(any()) } answers { firstArg() }

        When("opening a real supported USD account") {
            val result = service.openWallet("user_1", "usd")

            Then("it real-creates a new zero-balance USD account") {
                result.type shouldBe WalletType.FOREIGN_CURRENCY
                result.currency shouldBe "USD"
                result.balance shouldBe BigDecimal.ZERO
            }
            // Real bug found live (2026-08-02) -- see openWallet's own doc comment:
            // this asserts the actual fix mechanism, the same "lock a different
            // already-existing row" precedent MiniWalletService.openMiniWallet's own
            // identical-shaped fix establishes for a reject-if-already-exists
            // check-then-CREATE race.
            Then("it real-locks the user's own MAIN wallet row before creating the new account") {
                verify(exactly = 1) { walletRepository.findByIdForUpdate("wallet_main") }
            }
        }

        When("opening an account for an unsupported currency") {
            Then("it throws UnsupportedCurrencyException before ever touching the wallet repository") {
                try {
                    service.openWallet("user_1", "JPY")
                    error("expected UnsupportedCurrencyException")
                } catch (e: UnsupportedCurrencyException) {
                    verify(exactly = 0) { walletRepository.findByIdForUpdate(any()) }
                }
            }
        }
    }

    Given("a real user who already has a USD account") {
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateClient = mockk<ForeignCurrencyRateClient>()
        val currencyConversionRepository = mockk<CurrencyConversionRepository>()
        val accountNumberGenerator = mockk<AccountNumberGenerator>(relaxed = true)
        val service = ForeignCurrencyWalletService(walletRepository, ledgerService, rateClient, currencyConversionRepository, accountNumberGenerator)

        val mainWallet = wallet("wallet_main", "user_1", WalletType.MAIN)
        val existingUsd = wallet("wallet_usd", "user_1", WalletType.FOREIGN_CURRENCY, "USD")
        every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns mainWallet
        every { walletRepository.findByIdForUpdate("wallet_main") } returns Optional.of(mainWallet)
        every { walletRepository.findByUserIdAndTypeAndCurrency("user_1", WalletType.FOREIGN_CURRENCY, "USD") } returns existingUsd

        When("trying to open a second USD account") {
            Then("it throws ForeignCurrencyWalletAlreadyExistsException and never creates a real duplicate") {
                try {
                    service.openWallet("user_1", "USD")
                    error("expected ForeignCurrencyWalletAlreadyExistsException")
                } catch (e: ForeignCurrencyWalletAlreadyExistsException) {
                    verify(exactly = 0) { walletRepository.save(any()) }
                }
            }
        }
    }

    Given("a real user with no MAIN wallet at all trying to open a foreign-currency account") {
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateClient = mockk<ForeignCurrencyRateClient>()
        val currencyConversionRepository = mockk<CurrencyConversionRepository>()
        val accountNumberGenerator = mockk<AccountNumberGenerator>(relaxed = true)
        val service = ForeignCurrencyWalletService(walletRepository, ledgerService, rateClient, currencyConversionRepository, accountNumberGenerator)

        every { walletRepository.findByUserIdAndType("user_2", WalletType.MAIN) } returns null

        When("opening a real supported currency") {
            Then("it throws WalletNotFoundException before ever attempting to lock or create anything") {
                try {
                    service.openWallet("user_2", "USD")
                    error("expected WalletNotFoundException")
                } catch (e: WalletNotFoundException) {
                    verify(exactly = 0) { walletRepository.findByIdForUpdate(any()) }
                    verify(exactly = 0) { walletRepository.save(any()) }
                }
            }
        }
    }

    Given("a real user converting RWF into a USD account they already hold") {
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateClient = mockk<ForeignCurrencyRateClient>()
        val currencyConversionRepository = mockk<CurrencyConversionRepository>()
        val accountNumberGenerator = mockk<AccountNumberGenerator>(relaxed = true)
        val service = ForeignCurrencyWalletService(walletRepository, ledgerService, rateClient, currencyConversionRepository, accountNumberGenerator)

        val mainWallet = wallet("wallet_main", "user_1", WalletType.MAIN)
        val usdWallet = wallet("wallet_usd", "user_1", WalletType.FOREIGN_CURRENCY, "USD")
        every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns mainWallet
        every { walletRepository.findByUserIdAndTypeAndCurrency("user_1", WalletType.FOREIGN_CURRENCY, "USD") } returns usdWallet
        every { rateClient.getRate("RWF", "USD") } returns 0.00069
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_fx_1", emptyList())
        every { currencyConversionRepository.save(any()) } answers { firstArg() }

        When("converting a real 100,000 RWF amount") {
            val conversion = service.convert("user_1", "RWF", "USD", BigDecimal("100000"))

            Then("it real-applies itunda's own margin below the real live mid-market rate") {
                val gross = BigDecimal("100000").multiply(BigDecimal(0.00069)).setScale(2, java.math.RoundingMode.HALF_UP)
                val margin = gross.multiply(BigDecimal("0.015")).setScale(2, java.math.RoundingMode.HALF_UP)
                conversion.toAmount shouldBe gross.subtract(margin)
                conversion.marginAmount shouldBe margin
            }
        }

        When("converting to an unopened foreign currency") {
            every { walletRepository.findByUserIdAndTypeAndCurrency("user_1", WalletType.FOREIGN_CURRENCY, "EUR") } returns null
            every { rateClient.getRate("RWF", "EUR") } returns 0.00064

            Then("it throws ForeignCurrencyWalletNotFoundException before touching the ledger") {
                try {
                    service.convert("user_1", "RWF", "EUR", BigDecimal("10000"))
                    error("expected ForeignCurrencyWalletNotFoundException")
                } catch (e: ForeignCurrencyWalletNotFoundException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
