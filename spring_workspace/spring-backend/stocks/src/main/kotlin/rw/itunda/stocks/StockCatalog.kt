package rw.itunda.stocks

import java.math.BigDecimal

data class Stock(val id: String, val symbol: String, val name: String, val price: BigDecimal, val change: BigDecimal, val changePercent: BigDecimal, val marketCap: String, val volume: Long)

/** Same static catalog as backend/src/services/database.ts's stocks — real RSE symbols. */
object StockCatalog {
    val stocks = listOf(
        Stock("s1", "BOK", "Bank of Kigali Group PLC", BigDecimal("600"), BigDecimal.ZERO, BigDecimal.ZERO, "RSE listed", 163800),
        Stock("s2", "BLR", "BRALIRWA PLC", BigDecimal("490"), BigDecimal.ZERO, BigDecimal.ZERO, "RSE listed", 4700),
        Stock("s3", "MTNR", "MTN Rwanda PLC", BigDecimal("130"), BigDecimal.ZERO, BigDecimal.ZERO, "RSE listed", 2900),
        Stock("s4", "EQTY", "Equity Group", BigDecimal("52"), BigDecimal.ZERO, BigDecimal.ZERO, "12B RWF", 32000),
        Stock("s5", "IMR", "I&M Bank", BigDecimal("45"), BigDecimal.ZERO, BigDecimal.ZERO, "8B RWF", 15000),
        Stock("s6", "SGL", "Sorwathe", BigDecimal("120"), BigDecimal("3.6"), BigDecimal("3.0"), "5B RWF", 8200),
    )

    fun find(idOrSymbol: String) = stocks.find { it.id == idOrSymbol || it.symbol == idOrSymbol }
}
