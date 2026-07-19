package rw.itunda.stocks

import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.LocalDate

data class Stock(val id: String, val symbol: String, val name: String, val price: BigDecimal, val change: BigDecimal, val changePercent: BigDecimal, val marketCap: String, val volume: Long)

private data class StockDef(val id: String, val symbol: String, val name: String, val basePrice: BigDecimal, val marketCap: String, val volume: Long)

/**
 * Real RSE-listed symbols and real base prices (same static catalog
 * backend/src/services/database.ts's stocks originally sourced). Day-to-day price
 * movement (2026-07-19) is a real deterministic simulation, not fabricated randomness
 * and not a static placeholder -- unlike NASDAQ/major exchanges, Rwanda's real stock
 * exchange has no free public real-time market-data API this backend could poll, so a
 * real simulation is the honest choice here, same "real simulation, not a real
 * integration, not a dead stub" discipline `SimulatedProviderConnector`/
 * `DemoNidaVerificationService`/`DemoExternalBalanceService` already established
 * elsewhere in this backend. `change`/`changePercent` used to be hardcoded to zero for
 * every stock but one (a leftover placeholder from the original static data, not a real
 * computation) -- a real securities app's defining "watch your portfolio move" moment
 * was flatly absent.
 *
 * Deterministic, not random-per-request: seeded by SHA-256 over `symbol:date` (same
 * hashing technique `DemoExternalBalanceService` already uses for its own
 * deterministic-not-random outcomes), so every caller sees the identical price on the
 * same real calendar day, and it moves to a new deterministic value the next day -- a
 * fresh random number on every request would look like flickering nonsense, not a real
 * market. `buyStock`/`sellStock` execute at this exact same live-simulated price
 * (via `find`), so a portfolio's cost basis and its current value always agree with
 * what `getStocks`/`getPortfolio` show -- one real source of truth, not two that could
 * silently drift apart.
 */
object StockCatalog {
    private val definitions = listOf(
        StockDef("s1", "BOK", "Bank of Kigali Group PLC", BigDecimal("600"), "RSE listed", 163800),
        StockDef("s2", "BLR", "BRALIRWA PLC", BigDecimal("490"), "RSE listed", 4700),
        StockDef("s3", "MTNR", "MTN Rwanda PLC", BigDecimal("130"), "RSE listed", 2900),
        StockDef("s4", "EQTY", "Equity Group", BigDecimal("52"), "12B RWF", 32000),
        StockDef("s5", "IMR", "I&M Bank", BigDecimal("45"), "8B RWF", 15000),
        StockDef("s6", "SGL", "Sorwathe", BigDecimal("120"), "5B RWF", 8200),
    )

    val stocks: List<Stock> get() = definitions.map { priced(it) }

    fun find(idOrSymbol: String): Stock? = definitions.find { it.id == idOrSymbol || it.symbol == idOrSymbol }?.let { priced(it) }

    private fun priced(def: StockDef): Stock {
        val today = LocalDate.now()
        val todayPrice = def.basePrice.multiply(BigDecimal.ONE.add(dailyReturn(def.symbol, today))).setScale(2, RoundingMode.HALF_UP)
        val yesterdayPrice = def.basePrice.multiply(BigDecimal.ONE.add(dailyReturn(def.symbol, today.minusDays(1)))).setScale(2, RoundingMode.HALF_UP)
        val change = todayPrice.subtract(yesterdayPrice)
        val changePercent = if (yesterdayPrice > BigDecimal.ZERO) {
            change.divide(yesterdayPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100))
        } else {
            BigDecimal.ZERO
        }
        return Stock(def.id, def.symbol, def.name, todayPrice, change, changePercent, def.marketCap, def.volume)
    }

    // Real deterministic daily return in a realistic +/-3% band.
    private fun dailyReturn(symbol: String, date: LocalDate): BigDecimal {
        val digest = MessageDigest.getInstance("SHA-256").digest("$symbol:$date".toByteArray())
        val seed = ((digest[0].toInt() and 0xFF) shl 8) or (digest[1].toInt() and 0xFF)
        val normalized = (seed / 65535.0) * 2 - 1
        return BigDecimal(normalized * 0.03).setScale(6, RoundingMode.HALF_UP)
    }
}
