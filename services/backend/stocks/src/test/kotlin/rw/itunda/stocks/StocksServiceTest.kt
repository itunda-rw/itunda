package rw.itunda.stocks

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.Holding
import rw.itunda.core.domain.StockWatchlist
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.HoldingRepository
import rw.itunda.core.repository.StockWatchlistRepository
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
        val stockWatchlistRepository = mockk<StockWatchlistRepository>(relaxed = true)
        val service = StocksService(walletRepository, holdingRepository, ledgerService, stockWatchlistRepository)

        every { walletRepository.findByUserIdAndType("user_1", WalletType.INVESTMENT) } returns investmentWallet()
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())

        When("buying shares of a stock with no existing position") {
            every { holdingRepository.findByUserIdAndStockId("user_1", "s1") } returns null
            val holdingSlot = mutableListOf<Holding>()
            every { holdingRepository.save(capture(holdingSlot)) } answers { firstArg() }

            service.buyStock("user_1", "s1", BigDecimal("10"))

            // Real deterministic daily simulation (2026-07-19): BOK's live price
            // fluctuates +/-3% day to day, so this asserts against StockCatalog's own
            // live price rather than a hardcoded number that would drift out of sync.
            val bokPrice = StockCatalog.find("s1")!!.price

            Then("it creates a new holding at the stock's real current price") {
                holdingSlot.first().shares shouldBe BigDecimal("10")
                holdingSlot.first().avgPrice shouldBe bokPrice.setScale(4)
            }
        }

        When("buying more shares of a stock the user already holds at a different price") {
            // 10 shares @ 500 already held; buying 10 more at the stock's real live
            // price is a real weighted-average recompute, not just the latest price.
            val bokPrice = StockCatalog.find("s1")!!.price
            val existing = Holding(id = "hold_1", userId = "user_1", walletId = "wallet_inv", stockId = "s1", shares = BigDecimal("10"), avgPrice = BigDecimal("500"))
            every { holdingRepository.findByUserIdAndStockId("user_1", "s1") } returns existing
            every { holdingRepository.save(any()) } answers { firstArg() }

            service.buyStock("user_1", "s1", BigDecimal("10"))

            Then("avgPrice is a real weighted average of the old and new cost basis, not just the latest price") {
                val expected = BigDecimal("10").multiply(BigDecimal("500")).add(BigDecimal("10").multiply(bokPrice))
                    .divide(BigDecimal("20"), 4, java.math.RoundingMode.HALF_UP)
                existing.avgPrice shouldBe expected
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

    Given("a real user managing their real stock watchlist") {
        val walletRepository = mockk<WalletRepository>()
        val holdingRepository = mockk<HoldingRepository>()
        val ledgerService = mockk<LedgerService>()
        val stockWatchlistRepository = mockk<StockWatchlistRepository>()
        val service = StocksService(walletRepository, holdingRepository, ledgerService, stockWatchlistRepository)

        When("watching a real stock for the first time") {
            every { stockWatchlistRepository.findByUserIdAndStockId("user_1", "s1") } returns null
            val savedSlot = mutableListOf<StockWatchlist>()
            every { stockWatchlistRepository.save(capture(savedSlot)) } answers { firstArg() }

            val watch = service.watchStock("user_1", "s1")

            Then("it persists a real new watch") {
                watch.userId shouldBe "user_1"
                watch.stockId shouldBe "s1"
                savedSlot.first().stockId shouldBe "s1"
            }
        }

        When("watching an already-watched stock") {
            val existing = StockWatchlist(id = "watch_1", userId = "user_1", stockId = "s1")
            every { stockWatchlistRepository.findByUserIdAndStockId("user_1", "s1") } returns existing

            val watch = service.watchStock("user_1", "s1")

            Then("it idempotently returns the real existing watch, never a duplicate") {
                watch shouldBe existing
                verify(exactly = 0) { stockWatchlistRepository.save(any()) }
            }
        }

        When("watching a stock that doesn't exist") {
            Then("it throws StockNotFoundException before touching the repository") {
                try {
                    service.watchStock("user_1", "s999")
                    error("expected StockNotFoundException")
                } catch (e: StockNotFoundException) {
                    verify(exactly = 0) { stockWatchlistRepository.save(any()) }
                }
            }
        }

        When("un-watching a stock that was never watched") {
            every { stockWatchlistRepository.deleteByUserIdAndStockId("user_1", "never_watched") } returns 0L

            Then("it silently no-ops rather than throwing") {
                service.unwatchStock("user_1", "never_watched")
                verify { stockWatchlistRepository.deleteByUserIdAndStockId("user_1", "never_watched") }
            }
        }

        When("listing a real user's watchlist") {
            every { stockWatchlistRepository.findByUserIdOrderByCreatedAtDesc("user_1") } returns
                listOf(StockWatchlist(id = "watch_1", userId = "user_1", stockId = "s1"), StockWatchlist(id = "watch_2", userId = "user_1", stockId = "s2"))

            val watchlist = service.getWatchlist("user_1")

            Then("it resolves each real stock's live current data") {
                watchlist.map { it.id } shouldBe listOf("s1", "s2")
                watchlist.all { it.price > BigDecimal.ZERO } shouldBe true
            }
        }
    }

    Given("a real caller requesting a real stock's price history") {
        val walletRepository = mockk<WalletRepository>()
        val holdingRepository = mockk<HoldingRepository>()
        val ledgerService = mockk<LedgerService>()
        val stockWatchlistRepository = mockk<StockWatchlistRepository>(relaxed = true)
        val service = StocksService(walletRepository, holdingRepository, ledgerService, stockWatchlistRepository)

        When("requesting a real 30-day window") {
            val history = service.getPriceHistory("s1", 30)

            Then("it returns the real deterministic 30-point series") {
                history.size shouldBe 30
            }
        }

        When("requesting zero days") {
            Then("it throws InvalidPriceHistoryRangeException") {
                try {
                    service.getPriceHistory("s1", 0)
                    error("expected InvalidPriceHistoryRangeException")
                } catch (e: InvalidPriceHistoryRangeException) {
                    // expected
                }
            }
        }

        When("requesting more than a real year") {
            Then("it throws InvalidPriceHistoryRangeException") {
                try {
                    service.getPriceHistory("s1", 366)
                    error("expected InvalidPriceHistoryRangeException")
                } catch (e: InvalidPriceHistoryRangeException) {
                    // expected
                }
            }
        }

        When("requesting history for an unknown stock") {
            Then("it throws StockNotFoundException") {
                try {
                    service.getPriceHistory("s999", 30)
                    error("expected StockNotFoundException")
                } catch (e: StockNotFoundException) {
                    // expected
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
