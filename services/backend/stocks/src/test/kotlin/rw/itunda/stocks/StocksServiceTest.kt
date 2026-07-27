package rw.itunda.stocks

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.Holding
import rw.itunda.core.domain.StockTrade
import rw.itunda.core.domain.StockWatchlist
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.HoldingRepository
import rw.itunda.core.repository.StockTradeRepository
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
        val stockTradeRepository = mockk<StockTradeRepository>(relaxed = true)
        val service = StocksService(walletRepository, holdingRepository, ledgerService, stockWatchlistRepository, stockTradeRepository)

        every { walletRepository.findByUserIdAndType("user_1", WalletType.INVESTMENT) } returns investmentWallet()
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        // Explicit stub even though stockTradeRepository is relaxed -- mockk's relaxed
        // default can't correctly infer JpaRepository's generic `<S extends T> S save(S)`
        // signature, throwing a real ClassCastException back in the caller (same known
        // gotcha MerchantServiceTest/OrderServiceTest/EatsOrderServiceTest/
        // GroupMessagingServiceTest already document).
        every { stockTradeRepository.save(any()) } answers { firstArg() }

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

        // Real bug fix (2026-07-27) -- see buyStock's own doc comment: an unrounded
        // fractional-share cost (the shape round-up-to-invest always passes) could carry
        // more than RWF's real 2 decimal places, which the real ledger's own balance
        // check would crash on with ArithmeticException("Rounding necessary"). Caught
        // live: the first real round-up-to-invest purchase 500'd the whole transfer.
        When("buying a real fractional share amount (round-up-to-invest style)") {
            every { holdingRepository.findByUserIdAndStockId("user_1", "s1") } returns null
            every { holdingRepository.save(any()) } answers { firstArg() }
            val legsSlot = mutableListOf<List<rw.itunda.core.ledger.LedgerLeg>>()
            every { ledgerService.postLedgerTransaction(any(), capture(legsSlot)) } returns LedgerPostResult("ledgertxn_frac", emptyList())

            service.buyStock("user_1", "s1", BigDecimal("1.634146"))

            Then("it real-rounds the ledger cost to exactly 2 real RWF decimal places, not the raw 6dp product") {
                val bokPrice = StockCatalog.find("s1")!!.price
                val expectedCost = BigDecimal("1.634146").multiply(bokPrice).setScale(2, java.math.RoundingMode.HALF_UP)
                legsSlot.last().first().amount shouldBe expectedCost
                legsSlot.last().first().amount.scale() shouldBe 2
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
        val stockTradeRepository = mockk<StockTradeRepository>(relaxed = true)
        val service = StocksService(walletRepository, holdingRepository, ledgerService, stockWatchlistRepository, stockTradeRepository)

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
        val stockTradeRepository = mockk<StockTradeRepository>(relaxed = true)
        val service = StocksService(walletRepository, holdingRepository, ledgerService, stockWatchlistRepository, stockTradeRepository)

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

    Given("a real user with a real trade history, checking their portfolio's real value chart") {
        val walletRepository = mockk<WalletRepository>()
        val holdingRepository = mockk<HoldingRepository>()
        val ledgerService = mockk<LedgerService>()
        val stockWatchlistRepository = mockk<StockWatchlistRepository>(relaxed = true)
        val stockTradeRepository = mockk<StockTradeRepository>()
        val service = StocksService(walletRepository, holdingRepository, ledgerService, stockWatchlistRepository, stockTradeRepository)

        When("a single real buy happened 3 real days ago") {
            val buy = StockTrade(
                id = "trade_1", userId = "user_1", stockId = "s1", type = "BUY",
                shares = BigDecimal("10"), price = BigDecimal("500"),
                executedAt = java.time.LocalDate.now().minusDays(3).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
            )
            every { stockTradeRepository.findByUserIdOrderByExecutedAtAsc("user_1") } returns listOf(buy)

            val history = service.getPortfolioHistory("user_1", 7)

            Then("it real-reconstructs zero before the buy and the real position after -- not an approximation applied to the whole window") {
                history.size shouldBe 7
                // Days 0-3 (7 days ago .. 4 days ago): before the real buy, honestly zero.
                history.take(3).all { it.value == BigDecimal.ZERO } shouldBe true
                // The buy day itself and every day after: real 10 shares at that real day's price.
                val todayPrice = StockCatalog.find("s1")!!.price
                history.last().value shouldBe BigDecimal("10").multiply(todayPrice)
                history.drop(3).all { it.value > BigDecimal.ZERO } shouldBe true
            }
        }

        When("a real buy is followed by a real full sell 2 real days ago") {
            val buy = StockTrade(
                id = "trade_2", userId = "user_1", stockId = "s1", type = "BUY",
                shares = BigDecimal("10"), price = BigDecimal("500"),
                executedAt = java.time.LocalDate.now().minusDays(5).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
            )
            val sell = StockTrade(
                id = "trade_3", userId = "user_1", stockId = "s1", type = "SELL",
                shares = BigDecimal("10"), price = BigDecimal("600"),
                executedAt = java.time.LocalDate.now().minusDays(2).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
            )
            every { stockTradeRepository.findByUserIdOrderByExecutedAtAsc("user_1") } returns listOf(buy, sell)

            val history = service.getPortfolioHistory("user_1", 7)

            Then("it real-reflects the position dropping back to zero after the real sell, not still showing a phantom holding") {
                // Today (the most recent point): fully sold, real zero -- the exact case
                // the old "current holdings" approximation could never get right, since
                // a fully-sold position wouldn't even appear in current holdings at all.
                history.last().value shouldBe BigDecimal.ZERO
            }
        }

        When("the real user has no trade history at all") {
            every { stockTradeRepository.findByUserIdOrderByExecutedAtAsc("user_2") } returns emptyList()

            val history = service.getPortfolioHistory("user_2", 7)

            Then("every real point is honestly zero, not a fabricated value") {
                history.all { it.value == BigDecimal.ZERO } shouldBe true
            }
        }

        When("requesting an invalid range") {
            Then("it throws InvalidPriceHistoryRangeException") {
                try {
                    service.getPortfolioHistory("user_1", 0)
                    error("expected InvalidPriceHistoryRangeException")
                } catch (e: InvalidPriceHistoryRangeException) {
                    // expected
                }
            }
        }
    }

    // Real bug fix (2026-07-27) -- see StocksService.fundInvestmentWallet's own doc
    // comment: without this, a real INVESTMENT wallet existed but nothing could ever
    // move money into it, so buyStock would still 422 (InsufficientFundsException)
    // for every real first purchase.
    Given("a real user with both a real MAIN and a real INVESTMENT wallet") {
        val walletRepository = mockk<WalletRepository>()
        val holdingRepository = mockk<HoldingRepository>()
        val ledgerService = mockk<LedgerService>()
        val stockWatchlistRepository = mockk<StockWatchlistRepository>(relaxed = true)
        val stockTradeRepository = mockk<StockTradeRepository>(relaxed = true)
        val service = StocksService(walletRepository, holdingRepository, ledgerService, stockWatchlistRepository, stockTradeRepository)

        val mainWallet = Wallet(
            id = "wallet_main", userId = "user_1", accountNumber = "ACC-MAIN", accountName = "Main",
            type = WalletType.MAIN, balance = BigDecimal("50000"), availableBalance = BigDecimal("50000"),
        )
        every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns mainWallet
        every { walletRepository.findByUserIdAndType("user_1", WalletType.INVESTMENT) } returns investmentWallet()

        When("funding the investment wallet with a real positive amount") {
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_fund_1", emptyList())

            val result = service.fundInvestmentWallet("user_1", BigDecimal("10000"))

            Then("it posts a real balanced MAIN-debit/INVESTMENT-credit ledger transaction") {
                verify(exactly = 1) {
                    ledgerService.postLedgerTransaction(
                        mainWallet.currency,
                        listOf(
                            rw.itunda.core.ledger.LedgerLeg(mainWallet.id, rw.itunda.core.domain.LedgerAccountType.WALLET, rw.itunda.core.domain.LedgerDirection.DEBIT, BigDecimal("10000"), "Transfer to investment account"),
                            rw.itunda.core.ledger.LedgerLeg("wallet_inv", rw.itunda.core.domain.LedgerAccountType.WALLET, rw.itunda.core.domain.LedgerDirection.CREDIT, BigDecimal("10000"), "Transfer to investment account"),
                        ),
                    )
                }
                result["id"] shouldBe "ledgertxn_fund_1"
            }
        }

        When("funding with a zero amount") {
            Then("it throws InvalidFundingAmountException before touching the ledger") {
                try {
                    service.fundInvestmentWallet("user_1", BigDecimal.ZERO)
                    error("expected InvalidFundingAmountException")
                } catch (e: InvalidFundingAmountException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("funding with a negative amount") {
            Then("it throws InvalidFundingAmountException before touching the ledger") {
                try {
                    service.fundInvestmentWallet("user_1", BigDecimal("-500"))
                    error("expected InvalidFundingAmountException")
                } catch (e: InvalidFundingAmountException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a real user missing a real INVESTMENT wallet, trying to fund it anyway") {
        val walletRepository = mockk<WalletRepository>()
        val holdingRepository = mockk<HoldingRepository>()
        val ledgerService = mockk<LedgerService>()
        val stockWatchlistRepository = mockk<StockWatchlistRepository>(relaxed = true)
        val stockTradeRepository = mockk<StockTradeRepository>(relaxed = true)
        val service = StocksService(walletRepository, holdingRepository, ledgerService, stockWatchlistRepository, stockTradeRepository)

        val mainWallet = Wallet(
            id = "wallet_main", userId = "user_2", accountNumber = "ACC-MAIN2", accountName = "Main",
            type = WalletType.MAIN, balance = BigDecimal("50000"), availableBalance = BigDecimal("50000"),
        )
        every { walletRepository.findByUserIdAndType("user_2", WalletType.MAIN) } returns mainWallet
        every { walletRepository.findByUserIdAndType("user_2", WalletType.INVESTMENT) } returns null

        When("funding is attempted") {
            Then("it throws NoWalletException rather than a null-pointer") {
                try {
                    service.fundInvestmentWallet("user_2", BigDecimal("1000"))
                    error("expected NoWalletException")
                } catch (e: NoWalletException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
