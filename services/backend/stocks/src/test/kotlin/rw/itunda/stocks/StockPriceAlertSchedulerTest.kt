package rw.itunda.stocks

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.StockWatchlist

/**
 * First test coverage for StockPriceAlertScheduler -- same real "one bad row can't
 * poison the sweep" resilience contract as every other scheduler test in this sweep.
 */
class StockPriceAlertSchedulerTest : BehaviorSpec({

    fun watchlist(id: String) = StockWatchlist(id = id, userId = "user_$id", stockId = "stock_$id")

    Given("3 due price alerts, where triggering the middle one fails") {
        val stocksService = mockk<StocksService>()
        every { stocksService.getDuePriceAlerts() } returns listOf(watchlist("w1"), watchlist("w2"), watchlist("w3"))
        every { stocksService.triggerPriceAlert("w1") } returns Unit
        every { stocksService.triggerPriceAlert("w2") } throws RuntimeException("push notification failed")
        every { stocksService.triggerPriceAlert("w3") } returns Unit
        val scheduler = StockPriceAlertScheduler(stocksService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 are still triggered -- the middle failure doesn't stop the sweep") {
                verify(exactly = 1) { stocksService.triggerPriceAlert("w1") }
                verify(exactly = 1) { stocksService.triggerPriceAlert("w2") }
                verify(exactly = 1) { stocksService.triggerPriceAlert("w3") }
            }
        }
    }

    Given("no due price alerts") {
        val stocksService = mockk<StocksService>()
        every { stocksService.getDuePriceAlerts() } returns emptyList()
        val scheduler = StockPriceAlertScheduler(stocksService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is triggered, no exception is thrown") {
                verify(exactly = 0) { stocksService.triggerPriceAlert(any()) }
            }
        }
    }
})
