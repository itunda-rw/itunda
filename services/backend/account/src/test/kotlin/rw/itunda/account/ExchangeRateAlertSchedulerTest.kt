package rw.itunda.account

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.ExchangeRateAlert

/**
 * First test coverage for ExchangeRateAlertScheduler -- same real resilience property
 * as AutoTopUpSchedulerTest's own doc comment: "one bad row (or one currently-
 * unreachable rate) can never poison the sweep for every other real due alert."
 */
class ExchangeRateAlertSchedulerTest : BehaviorSpec({

    fun alert(id: String, userId: String) = ExchangeRateAlert(
        id = id, userId = userId, fromCurrency = "RWF", toCurrency = "USD", targetRate = 1400.0, direction = "ABOVE",
    )

    Given("3 due exchange-rate alerts, where the middle one throws") {
        val foreignCurrencyAccountService = mockk<ForeignCurrencyAccountService>()
        every { foreignCurrencyAccountService.getDueRateAlerts() } returns listOf(
            alert("fx_1", "user_1"), alert("fx_2", "user_2"), alert("fx_3", "user_3"),
        )
        every { foreignCurrencyAccountService.triggerRateAlert("fx_1") } returns Unit
        every { foreignCurrencyAccountService.triggerRateAlert("fx_2") } throws RuntimeException("rate provider unreachable")
        every { foreignCurrencyAccountService.triggerRateAlert("fx_3") } returns Unit
        val scheduler = ExchangeRateAlertScheduler(foreignCurrencyAccountService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 are still attempted -- the middle failure doesn't stop the sweep") {
                verify(exactly = 1) { foreignCurrencyAccountService.triggerRateAlert("fx_1") }
                verify(exactly = 1) { foreignCurrencyAccountService.triggerRateAlert("fx_2") }
                verify(exactly = 1) { foreignCurrencyAccountService.triggerRateAlert("fx_3") }
            }
        }
    }

    Given("no due alerts at all") {
        val foreignCurrencyAccountService = mockk<ForeignCurrencyAccountService>()
        every { foreignCurrencyAccountService.getDueRateAlerts() } returns emptyList()
        val scheduler = ExchangeRateAlertScheduler(foreignCurrencyAccountService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is triggered, no exception is thrown") {
                verify(exactly = 0) { foreignCurrencyAccountService.triggerRateAlert(any()) }
            }
        }
    }
})
