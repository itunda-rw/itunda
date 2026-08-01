package rw.itunda.stocks

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe

/** Real coverage for the deterministic daily price simulation (2026-07-19) -- see
 * StockCatalog's own doc comment for why this exists (no real RSE market-data feed is
 * reachable from this backend) and why it must be deterministic, not random-per-call. */
class StockCatalogTest : BehaviorSpec({

    Given("the real stock catalog") {
        When("looking up the same real stock twice in the same call") {
            val first = StockCatalog.find("s1")
            val second = StockCatalog.find("s1")

            Then("it returns the identical real price both times -- deterministic, not random-per-request") {
                first?.price shouldBe second?.price
                first?.change shouldBe second?.change
                first?.changePercent shouldBe second?.changePercent
            }
        }

        When("checking every real listed stock's simulated movement") {
            val stocks = StockCatalog.stocks

            Then("every stock has a real, bounded, non-fabricated daily change -- not the old hardcoded-zero placeholder") {
                // 6 real RSE-domestic + 5 real overseas (해외주식, added 2026-08-01).
                stocks.size shouldBe 11
                stocks.forEach { stock ->
                    // Today's and yesterday's prices are each independently drawn from
                    // a +/-3% band, so the real worst-case day-over-day change is
                    // (0.03 - (-0.03)) / (1 - 0.03) =~ 6.19%, not +/-3% -- bounded with
                    // headroom, not the old hardcoded-zero placeholder either way.
                    stock.changePercent.toDouble() shouldBeGreaterThan -7.0
                    stock.changePercent.toDouble() shouldBeLessThan 7.0
                }
            }
        }

        When("looking up an unknown stock id") {
            val result = StockCatalog.find("s999")

            Then("it returns null, not a fabricated stock") {
                result shouldBe null
            }
        }

        When("comparing every real stock's simulated daily return") {
            val changePercents = StockCatalog.stocks.map { it.changePercent }

            Then("they real-simulate independently -- not the identical movement stamped onto every symbol") {
                changePercents.toSet().size shouldBe changePercents.size
            }
        }

        When("fetching a real 30-day price history") {
            val history = StockCatalog.priceHistory("s1", 30)

            Then("it returns exactly 30 real points, oldest to newest, with the last one matching today's live price") {
                history?.size shouldBe 30
                history?.first()?.date shouldBe java.time.LocalDate.now().minusDays(29)
                history?.last()?.date shouldBe java.time.LocalDate.now()
                history?.last()?.price shouldBe StockCatalog.find("s1")?.price
            }
        }

        When("fetching the real price history twice") {
            val first = StockCatalog.priceHistory("s1", 7)
            val second = StockCatalog.priceHistory("s1", 7)

            Then("every real point is identical both times -- deterministic, not random-per-call") {
                first shouldBe second
            }
        }

        When("fetching real price history for an unknown stock") {
            val history = StockCatalog.priceHistory("s999", 30)

            Then("it returns null, not a fabricated series") {
                history shouldBe null
            }
        }

        // Real 해외주식 (overseas stock trading, added 2026-08-01) -- see StockCatalog's
        // own doc comment for the sourced account.
        When("looking up a real overseas stock by symbol") {
            val stock = StockCatalog.find("AAPL")

            Then("it resolves with the real NASDAQ market tag, not silently absent") {
                stock?.market shouldBe "NASDAQ"
                stock?.name shouldBe "Apple Inc."
            }
        }

        When("checking every real domestic stock's market tag") {
            val domestic = StockCatalog.stocks.filter { it.id in setOf("s1", "s2", "s3", "s4", "s5", "s6") }

            Then("every domestic symbol is still real-tagged RSE, unchanged by the overseas addition") {
                domestic.forEach { it.market shouldBe "RSE" }
            }
        }
    }
})
