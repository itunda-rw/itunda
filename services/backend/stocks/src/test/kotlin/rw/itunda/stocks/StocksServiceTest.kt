package rw.itunda.stocks

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.Holding
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.HoldingRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal

/** First test coverage for stocks -- specifically the weighted-average-cost math on
 * buy (a real, easy-to-get-wrong calculation) and that sell can't oversell a position. */
class StocksServiceTest : BehaviorSpec({

    fun investmentWallet() = Wallet(
        id = "wallet_inv", userId = "user_1", accountNumber = "ACC-INV", accountName = "Investment",
        type = WalletType.INVESTMENT, balance = BigDecimal("1000000"), availableBalance = BigDecimal("1000000"),
    )

    Given("a user with an investment wallet") {
        val walletRepository = mockk<WalletRepository>()
        val holdingRepository = mockk<HoldingRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = StocksService(walletRepository, holdingRepository, ledgerService)

        every { walletRepository.findByUserIdAndType("user_1", WalletType.INVESTMENT) } returns investmentWallet()
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())

        When("buying shares of a stock with no existing position") {
            every { holdingRepository.findByUserIdAndStockId("user_1", "s1") } returns null
            val holdingSlot = mutableListOf<Holding>()
            every { holdingRepository.save(capture(holdingSlot)) } answers { firstArg() }

            service.buyStock("user_1", "s1", BigDecimal("10"))

            Then("it creates a new holding at the stock's current price (600 RWF for BOK)") {
                holdingSlot.first().shares shouldBe BigDecimal("10")
                holdingSlot.first().avgPrice shouldBe BigDecimal("600.0000")
            }
        }

        When("buying more shares of a stock the user already holds at a different price") {
            // 10 shares @ 600 already held; stock's current price is also 600 in the
            // catalog, so buying 10 more at the same price should leave avgPrice
            // unchanged -- a real weighted-average recompute, not just addition.
            val existing = Holding(id = "hold_1", userId = "user_1", walletId = "wallet_inv", stockId = "s1", shares = BigDecimal("10"), avgPrice = BigDecimal("500"))
            every { holdingRepository.findByUserIdAndStockId("user_1", "s1") } returns existing
            every { holdingRepository.save(any()) } answers { firstArg() }

            service.buyStock("user_1", "s1", BigDecimal("10"))

            Then("avgPrice is a real weighted average of the old and new cost basis, not just the latest price") {
                // (10*500 + 10*600) / 20 = 550
                existing.avgPrice shouldBe BigDecimal("550.0000")
                existing.shares shouldBe BigDecimal("20")
            }
        }

        When("buying an unknown stock id") {
            Then("it throws StockNotFoundException before touching the ledger") {
                try {
                    service.buyStock("user_1", "s999", BigDecimal("1"))
                    error("expected StockNotFoundException")
                } catch (e: StockNotFoundException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("selling shares within the held position") {
            val holding = Holding(id = "hold_2", userId = "user_1", walletId = "wallet_inv", stockId = "s1", shares = BigDecimal("10"), avgPrice = BigDecimal("500"))
            every { holdingRepository.findByUserIdAndStockId("user_1", "s1") } returns holding
            every { holdingRepository.save(any()) } answers { firstArg() }

            service.sellStock("user_1", "s1", BigDecimal("4"))

            Then("it reduces the share count by exactly the amount sold") {
                holding.shares shouldBe BigDecimal("6")
            }
        }

        When("selling more shares than are held") {
            val holding = Holding(id = "hold_3", userId = "user_1", walletId = "wallet_inv", stockId = "s1", shares = BigDecimal("5"), avgPrice = BigDecimal("500"))
            every { holdingRepository.findByUserIdAndStockId("user_1", "s1") } returns holding

            Then("it throws NotEnoughSharesException rather than allowing a negative position") {
                try {
                    service.sellStock("user_1", "s1", BigDecimal("10"))
                    error("expected NotEnoughSharesException")
                } catch (e: NotEnoughSharesException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("selling a stock the user has never held") {
            every { holdingRepository.findByUserIdAndStockId("user_1", "s1") } returns null

            Then("it throws NotEnoughSharesException, not a null-pointer") {
                try {
                    service.sellStock("user_1", "s1", BigDecimal("1"))
                    error("expected NotEnoughSharesException")
                } catch (e: NotEnoughSharesException) {
                    // expected
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
