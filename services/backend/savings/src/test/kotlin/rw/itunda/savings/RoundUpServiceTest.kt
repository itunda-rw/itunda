package rw.itunda.savings

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.RoundUpSettings
import rw.itunda.core.domain.SavingsGoal
import rw.itunda.core.repository.RoundUpSettingsRepository
import rw.itunda.core.repository.SavingsGoalRepository
import rw.itunda.stocks.StockCatalog
import rw.itunda.stocks.StockNotFoundException
import rw.itunda.stocks.StocksService
import java.math.BigDecimal
import java.util.Optional

/** First test coverage for round-up auto-saving/auto-investing -- this feature (both the
 * original savings-goal target and the 2026-07-27 investment target) previously shipped
 * with zero dedicated unit tests. */
class RoundUpServiceTest : BehaviorSpec({

    Given("a real user configuring round-up settings") {
        val roundUpSettingsRepository = mockk<RoundUpSettingsRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val savingsService = mockk<SavingsService>()
        val stocksService = mockk<StocksService>()
        val service = RoundUpService(roundUpSettingsRepository, savingsGoalRepository, savingsService, stocksService)

        When("enabling with an unsupported increment") {
            Then("it throws InvalidRoundUpIncrementException before touching any repository") {
                try {
                    service.setSettings("user_1", true, BigDecimal("250"), "goal_1", null)
                    error("expected InvalidRoundUpIncrementException")
                } catch (e: InvalidRoundUpIncrementException) {
                    verify(exactly = 0) { roundUpSettingsRepository.findByUserId(any()) }
                }
            }
        }

        When("enabling with neither a goal nor a stock target") {
            Then("it throws RoundUpTargetRequiredException") {
                try {
                    service.setSettings("user_1", true, BigDecimal("100"), null, null)
                    error("expected RoundUpTargetRequiredException")
                } catch (e: RoundUpTargetRequiredException) {
                    // expected
                }
            }
        }

        When("enabling with both a goal and a stock target") {
            Then("it throws RoundUpSingleTargetException rather than silently picking one") {
                try {
                    service.setSettings("user_1", true, BigDecimal("100"), "goal_1", "s1")
                    error("expected RoundUpSingleTargetException")
                } catch (e: RoundUpSingleTargetException) {
                    // expected
                }
            }
        }

        When("enabling with a real savings goal target") {
            val goal = SavingsGoal(
                id = "goal_1", userId = "user_1", accountId = "account_savings", name = "Trip",
                targetAmount = BigDecimal("100000"), currentAmount = BigDecimal.ZERO,
                monthlyContribution = BigDecimal.ZERO, interestRate = 0.0,
            )
            every { savingsGoalRepository.findById("goal_1") } returns Optional.of(goal)
            every { roundUpSettingsRepository.findByUserId("user_1") } returns null
            val savedSlot = mutableListOf<RoundUpSettings>()
            every { roundUpSettingsRepository.save(capture(savedSlot)) } answers { firstArg() }

            service.setSettings("user_1", true, BigDecimal("100"), "goal_1", null)

            Then("it persists a real enabled setting targeting that goal") {
                savedSlot.first().targetGoalId shouldBe "goal_1"
                savedSlot.first().targetStockId shouldBe null
                savedSlot.first().enabled shouldBe true
            }
        }

        When("enabling with a real stock target") {
            every { roundUpSettingsRepository.findByUserId("user_1") } returns null
            val savedSlot = mutableListOf<RoundUpSettings>()
            every { roundUpSettingsRepository.save(capture(savedSlot)) } answers { firstArg() }

            service.setSettings("user_1", true, BigDecimal("500"), null, "s1")

            Then("it persists a real enabled setting targeting that stock, not a goal") {
                savedSlot.first().targetStockId shouldBe "s1"
                savedSlot.first().targetGoalId shouldBe null
            }
        }

        When("enabling with an unknown stock target") {
            Then("it throws StockNotFoundException before saving anything") {
                try {
                    service.setSettings("user_1", true, BigDecimal("100"), null, "s999")
                    error("expected StockNotFoundException")
                } catch (e: StockNotFoundException) {
                    verify(exactly = 0) { roundUpSettingsRepository.save(any()) }
                }
            }
        }
    }

    Given("a real user with round-up enabled targeting a real savings goal") {
        val roundUpSettingsRepository = mockk<RoundUpSettingsRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val savingsService = mockk<SavingsService>(relaxed = true)
        val stocksService = mockk<StocksService>()
        val service = RoundUpService(roundUpSettingsRepository, savingsGoalRepository, savingsService, stocksService)

        val settings = RoundUpSettings(id = "ru_1", userId = "user_1", enabled = true, roundToNearest = BigDecimal("1000"), targetGoalId = "goal_1")
        every { roundUpSettingsRepository.findByUserId("user_1") } returns settings

        When("a real transfer of 12200 (leaving 800 real change under the 1000 increment) completes") {
            service.processRoundUp("user_1", BigDecimal("12200"))

            Then("it deposits exactly the real 800 change into the goal, never touching stocks") {
                verify(exactly = 1) { savingsService.depositToGoal("user_1", "goal_1", BigDecimal("800"), null) }
                verify(exactly = 0) { stocksService.fundInvestmentAccount(any(), any()) }
            }
        }

        When("a real transfer of exactly 10000 (no remainder against the 1000 increment) completes") {
            service.processRoundUp("user_1", BigDecimal("10000"))

            Then("it honestly does not invest -- there is no real change to round up") {
                verify(exactly = 0) { savingsService.depositToGoal(any(), any(), any(), any()) }
            }
        }
    }

    Given("a real user with round-up enabled targeting a real stock (동전 모으기)") {
        val roundUpSettingsRepository = mockk<RoundUpSettingsRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val savingsService = mockk<SavingsService>()
        val stocksService = mockk<StocksService>()
        val service = RoundUpService(roundUpSettingsRepository, savingsGoalRepository, savingsService, stocksService)

        val settings = RoundUpSettings(id = "ru_2", userId = "user_2", enabled = true, roundToNearest = BigDecimal("1000"), targetStockId = "s1")
        every { roundUpSettingsRepository.findByUserId("user_2") } returns settings

        When("a real transfer leaves real change under the increment") {
            every { stocksService.fundInvestmentAccount("user_2", BigDecimal("800")) } returns mapOf("id" to "ledgertxn_1")
            val sharesSlot = mutableListOf<BigDecimal>()
            every { stocksService.buyStock("user_2", "s1", capture(sharesSlot)) } returns mapOf("id" to "trade_1")

            service.processRoundUp("user_2", BigDecimal("12200"))

            Then("it funds the investment account with exactly the real change, then buys a real fractional share at that stock's live price") {
                verify(exactly = 1) { stocksService.fundInvestmentAccount("user_2", BigDecimal("800")) }
                val bokPrice = StockCatalog.find("s1")!!.price
                sharesSlot.first() shouldBe BigDecimal("800").divide(bokPrice, 6, java.math.RoundingMode.DOWN)
            }
        }
    }

    Given("a real user with round-up enabled but insufficient real balance to fund the round-up") {
        val roundUpSettingsRepository = mockk<RoundUpSettingsRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val savingsService = mockk<SavingsService>()
        val stocksService = mockk<StocksService>()
        val service = RoundUpService(roundUpSettingsRepository, savingsGoalRepository, savingsService, stocksService)

        val settings = RoundUpSettings(id = "ru_3", userId = "user_3", enabled = true, roundToNearest = BigDecimal("1000"), targetStockId = "s1")
        every { roundUpSettingsRepository.findByUserId("user_3") } returns settings

        When("funding the investment account real-fails") {
            every { stocksService.fundInvestmentAccount("user_3", any()) } throws rw.itunda.core.ledger.InsufficientFundsException("Insufficient funds")

            Then("processRoundUp swallows it rather than breaking the triggering transfer") {
                service.processRoundUp("user_3", BigDecimal("12200"))
                verify(exactly = 0) { stocksService.buyStock(any(), any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
