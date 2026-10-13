package rw.itunda.stocks

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.PracticeStockTrade
import rw.itunda.core.repository.PracticeStockTradeRepository
import java.math.BigDecimal

class PracticeStockServiceTest : BehaviorSpec({
    val repository = mockk<PracticeStockTradeRepository>()
    val service = PracticeStockService(repository)

    Given("an empty practice ledger") {
        every { repository.findByUserIdOrderByExecutedAtAsc("user_1") } returns emptyList()

        When("previewing a buy") {
            val preview = service.preview("user_1", "s1", "BUY", BigDecimal("2"))
            Then("it uses virtual cash and explicitly disables real execution") {
                preview["mode"] shouldBe "SIMULATED"
                preview["availableBalance"] shouldBe PracticeStockService.STARTING_VIRTUAL_CASH
                preview["estimatedNotionalCovered"] shouldBe true
                preview["realOrderExecutionAvailable"] shouldBe false
                preview["reservesFunds"] shouldBe false
            }
            Then("preview is read-only") {
                verify(exactly = 0) { repository.save(any()) }
            }
        }

        When("selling shares not held in the practice ledger") {
            Then("it rejects the order without writing a trade") {
                io.kotest.assertions.throwables.shouldThrow<NotEnoughSharesException> {
                    service.execute("user_1", "s1", "SELL", BigDecimal("1"))
                }
                verify(exactly = 0) { repository.save(any()) }
            }
        }
    }

    Given("a user with a practice purchase") {
        val trade = PracticeStockTrade(
            id = "practice_1", userId = "user_1", stockId = "s1",
            type = "BUY", shares = BigDecimal("10"), price = StockCatalog.find("s1")!!.price,
        )
        every { repository.findByUserIdOrderByExecutedAtAsc("user_1") } returns listOf(trade)
        every { repository.save(any()) } answers { firstArg() }

        When("selling a portion of the paper position") {
            val result = service.execute("user_1", "s1", "SELL", BigDecimal("2"))
            Then("it records a practice execution only") {
                result["mode"] shouldBe "SIMULATED"
                result["realLedgerMutated"] shouldBe false
                result["type"] shouldBe "PRACTICE_SELL"
                verify(exactly = 1) { repository.save(match { it.type == "SELL" && it.shares == BigDecimal("2") }) }
            }
        }
    }
})
