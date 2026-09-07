package rw.itunda.stocks

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MissingRequestHeaderException
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.AccountFrozenException
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.security.CurrentUser
import java.math.BigDecimal
import java.time.LocalDate

/**
 * First test coverage for StocksController -- previously untested despite the rest of
 * :stocks already having Service/Catalog/Scheduler Kotest suites. Covers real
 * delegation (caller-scoped userId, never client-supplied) for all 13 endpoints and
 * every real exception-handler mapping, matching this sweep's established
 * BehaviorSpec/MockK shape (see MapsControllerTest.kt/MapsControllerExceptionHandlingTest.kt).
 */
class StocksControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    fun controller(
        stocksService: StocksService = mockk(),
        idempotencyService: IdempotencyService = mockk(),
    ) = StocksController(stocksService, idempotencyService)

    Given("a real stock-catalog request") {
        val stocksService = mockk<StocksService>()
        val ctl = controller(stocksService = stocksService)
        every { stocksService.getStocks() } returns StockCatalog.stocks

        When("fetching it") {
            val response = ctl.getStocks()
            Then("it real-returns the backend's own catalog") {
                response.body?.get("stocks") shouldBe StockCatalog.stocks
            }
        }
    }

    Given("a real price-history request") {
        val stocksService = mockk<StocksService>()
        val ctl = controller(stocksService = stocksService)
        val history = listOf(PricePoint(LocalDate.now(), BigDecimal("500")))
        every { stocksService.getPriceHistory("s1", 30) } returns history

        When("fetching it with the default range") {
            ctl.getPriceHistory("s1", 30)
            Then("it real-delegates by the real stock id and days") {
                verify(exactly = 1) { stocksService.getPriceHistory("s1", 30) }
            }
        }
    }

    Given("a real portfolio request") {
        val stocksService = mockk<StocksService>()
        val ctl = controller(stocksService = stocksService)
        every { stocksService.getPortfolio("user_1") } returns mapOf("totalValue" to BigDecimal("1000"))

        When("fetching it") {
            ctl.getPortfolio(currentUser)
            Then("it queries scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { stocksService.getPortfolio("user_1") }
            }
        }
    }

    Given("a real portfolio-history request") {
        val stocksService = mockk<StocksService>()
        val ctl = controller(stocksService = stocksService)
        every { stocksService.getPortfolioHistory("user_1", 30) } returns listOf(PortfolioValuePoint(LocalDate.now(), BigDecimal("1000")))

        When("fetching it") {
            ctl.getPortfolioHistory(30, currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { stocksService.getPortfolioHistory("user_1", 30) }
            }
        }
    }

    Given("a real investment-account funding request") {
        val stocksService = mockk<StocksService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(stocksService = stocksService, idempotencyService = idempotencyService)
        val request = FundInvestmentRequest(BigDecimal("10000"))
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every { stocksService.fundInvestmentAccount("user_1", BigDecimal("10000")) } returns mapOf("id" to "txn_1")
        every {
            idempotencyService.replayOrExecute("POST /api/v1/stocks/fund", "key-1", request, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("funding it") {
            ctl.fund(request, "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/stocks/fund", "key-1", request, any()) }
                verify(exactly = 1) { stocksService.fundInvestmentAccount("user_1", BigDecimal("10000")) }
            }
        }
    }

    Given("a real stock buy") {
        val stocksService = mockk<StocksService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(stocksService = stocksService, idempotencyService = idempotencyService)
        val request = TradeStockRequest("s1", BigDecimal("10"))
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every { stocksService.buyStock("user_1", "s1", BigDecimal("10")) } returns mapOf("id" to "txn_2")
        every {
            idempotencyService.replayOrExecute("POST /api/v1/stocks/buy", "key-1", request, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("buying it") {
            ctl.buy(request, "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/stocks/buy", "key-1", request, any()) }
                verify(exactly = 1) { stocksService.buyStock("user_1", "s1", BigDecimal("10")) }
            }
        }
    }

    Given("a real stock sell") {
        val stocksService = mockk<StocksService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(stocksService = stocksService, idempotencyService = idempotencyService)
        val request = TradeStockRequest("s1", BigDecimal("5"))
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every { stocksService.sellStock("user_1", "s1", BigDecimal("5")) } returns mapOf("id" to "txn_3")
        every {
            idempotencyService.replayOrExecute("POST /api/v1/stocks/sell", "key-1", request, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("selling it") {
            ctl.sell(request, "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/stocks/sell", "key-1", request, any()) }
                verify(exactly = 1) { stocksService.sellStock("user_1", "s1", BigDecimal("5")) }
            }
        }
    }

    Given("a real watchlist add") {
        val stocksService = mockk<StocksService>()
        val ctl = controller(stocksService = stocksService)
        every { stocksService.watchStock("user_1", "s1") } returns mockk(relaxed = true)

        When("watching it") {
            ctl.watch("s1", currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { stocksService.watchStock("user_1", "s1") }
            }
        }
    }

    Given("a real watchlist remove") {
        val stocksService = mockk<StocksService>(relaxed = true)
        val ctl = controller(stocksService = stocksService)

        When("unwatching it") {
            ctl.unwatch("s1", currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { stocksService.unwatchStock("user_1", "s1") }
            }
        }
    }

    Given("a real watchlist-list request") {
        val stocksService = mockk<StocksService>()
        val ctl = controller(stocksService = stocksService)
        every { stocksService.getWatchlist("user_1") } returns emptyList<Stock>()

        When("fetching it") {
            ctl.getWatchlist(currentUser)
            Then("it queries scoped to the caller's own userId, never an unrelated viewer") {
                verify(exactly = 1) { stocksService.getWatchlist("user_1") }
            }
        }
    }

    Given("a real price-alert request") {
        val stocksService = mockk<StocksService>()
        val ctl = controller(stocksService = stocksService)
        every { stocksService.getPriceAlert("user_1", "s1") } returns null

        When("fetching it") {
            ctl.getPriceAlert("s1", currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { stocksService.getPriceAlert("user_1", "s1") }
            }
        }
    }

    Given("a real price-alert set") {
        val stocksService = mockk<StocksService>()
        val ctl = controller(stocksService = stocksService)
        every { stocksService.setPriceAlert("user_1", "s1", BigDecimal("600"), "ABOVE") } returns mockk(relaxed = true)

        When("setting it") {
            ctl.setPriceAlert("s1", SetPriceAlertRequest(BigDecimal("600"), "ABOVE"), currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { stocksService.setPriceAlert("user_1", "s1", BigDecimal("600"), "ABOVE") }
            }
        }
    }

    Given("a real price-alert clear") {
        val stocksService = mockk<StocksService>()
        val ctl = controller(stocksService = stocksService)
        every { stocksService.clearPriceAlert("user_1", "s1") } returns mockk(relaxed = true)

        When("clearing it") {
            ctl.clearPriceAlert("s1", currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { stocksService.clearPriceAlert("user_1", "s1") }
            }
        }
    }

    listOf(
        Triple(IdempotencyConflictException("Conflict") as RuntimeException, HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT"),
        Triple(IdempotencyInProgressException("Conflict"), HttpStatus.CONFLICT, "IDEMPOTENT_REQUEST_PROCESSING"),
        Triple(StockNotFoundException("Stock not found"), HttpStatus.NOT_FOUND, "STOCK_NOT_FOUND"),
        Triple(InvalidPriceHistoryRangeException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_PRICE_HISTORY_RANGE"),
        Triple(NoAccountException("No investment account found for this account"), HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"),
        Triple(InvalidFundingAmountException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_AMOUNT"),
        Triple(NotEnoughSharesException("Not enough shares to sell"), HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_SHARES"),
        Triple(InsufficientFundsException("Insufficient funds"), HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS"),
        Triple(RateLimitExceededException("Too many requests"), HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED"),
        Triple(AccountFrozenException("Account is frozen"), HttpStatus.FORBIDDEN, "ACCOUNT_FROZEN"),
        Triple(InvalidPriceAlertException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_PRICE_ALERT"),
    ).forEach { (exception, expectedStatus, expectedCode) ->
        Given("a real ${exception::class.simpleName}") {
            val ctl = controller()

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is IdempotencyConflictException -> ctl.handleConflict(exception)
                    is IdempotencyInProgressException -> ctl.handleInProgress(exception)
                    is StockNotFoundException -> ctl.handleNotFound(exception)
                    is InvalidPriceHistoryRangeException -> ctl.handleInvalidRange(exception)
                    is NoAccountException -> ctl.handleNoAccount(exception)
                    is InvalidFundingAmountException -> ctl.handleInvalidFunding(exception)
                    is NotEnoughSharesException -> ctl.handleNotEnough(exception)
                    is InsufficientFundsException -> ctl.handleInsufficientFunds(exception)
                    is RateLimitExceededException -> ctl.handleRateLimit(exception)
                    is AccountFrozenException -> ctl.handleAccountFrozen(exception)
                    is InvalidPriceAlertException -> ctl.handleInvalidPriceAlert(exception)
                    else -> error("unexpected exception type")
                }

                Then("it maps to $expectedStatus with code $expectedCode, not a generic 500") {
                    response.statusCode shouldBe expectedStatus
                    response.body?.code shouldBe expectedCode
                }
            }
        }
    }

    Given("a real MissingRequestHeaderException") {
        val ctl = controller()
        When("its exception handler maps it to a real HTTP response") {
            val response = ctl.handleMissingHeader(mockk<MissingRequestHeaderException>(relaxed = true))
            Then("it maps to 400 with code IDEMPOTENCY_KEY_REQUIRED, not a generic 500") {
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
                response.body?.code shouldBe "IDEMPOTENCY_KEY_REQUIRED"
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
