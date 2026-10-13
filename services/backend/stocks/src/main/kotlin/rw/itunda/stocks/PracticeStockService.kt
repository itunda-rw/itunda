package rw.itunda.stocks

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.PracticeStockTrade
import rw.itunda.core.repository.PracticeStockTradeRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

/**
 * Paper-trading ledger. It intentionally has no AccountRepository, HoldingRepository,
 * LedgerService, or StockTradeRepository dependency: practice orders cannot touch
 * customer money or the real investment portfolio.
 */
@Service
class PracticeStockService(private val practiceTrades: PracticeStockTradeRepository) {
    companion object {
        val STARTING_VIRTUAL_CASH: BigDecimal = BigDecimal("1000000.00")
    }

    fun getPortfolio(userId: String): Map<String, Any?> {
        val trades = practiceTrades.findByUserIdOrderByExecutedAtAsc(userId)
        val grouped = trades.groupBy { it.stockId }
        val holdings = grouped.mapNotNull { (stockId, rows) ->
            val stock = StockCatalog.find(stockId) ?: return@mapNotNull null
            var shares = BigDecimal.ZERO
            var costBasis = BigDecimal.ZERO
            rows.forEach { trade ->
                when (trade.type) {
                    "BUY" -> {
                        shares = shares.add(trade.shares)
                        costBasis = costBasis.add(trade.shares.multiply(trade.price))
                    }
                    "SELL" -> {
                        val average = if (shares > BigDecimal.ZERO) costBasis.divide(shares, 8, RoundingMode.HALF_UP) else BigDecimal.ZERO
                        val sold = trade.shares.min(shares)
                        shares = shares.subtract(sold)
                        costBasis = costBasis.subtract(average.multiply(sold)).max(BigDecimal.ZERO)
                    }
                }
            }
            if (shares <= BigDecimal.ZERO) return@mapNotNull null
            val avgPrice = costBasis.divide(shares, 4, RoundingMode.HALF_UP)
            val value = shares.multiply(stock.price).setScale(2, RoundingMode.HALF_UP)
            mapOf(
                "stockId" to stock.id, "symbol" to stock.symbol, "name" to stock.name,
                "shares" to shares, "avgPrice" to avgPrice, "currentPrice" to stock.price,
                "value" to value, "return" to value.subtract(shares.multiply(avgPrice)),
            )
        }
        val totalValue = holdings.sumOf { it["value"] as BigDecimal }
        val realizedBalance = trades.fold(STARTING_VIRTUAL_CASH) { balance, trade ->
            val amount = trade.shares.multiply(trade.price).setScale(2, RoundingMode.HALF_UP)
            when (trade.type) {
                "BUY" -> balance.subtract(amount)
                "SELL" -> balance.add(amount)
                else -> balance
            }
        }
        val totalCost = holdings.sumOf { (it["shares"] as BigDecimal).multiply(it["avgPrice"] as BigDecimal) }
        return mapOf(
            "totalValue" to totalValue,
            "totalReturn" to totalValue.add(realizedBalance).subtract(STARTING_VIRTUAL_CASH),
            "totalReturnPercent" to if (STARTING_VIRTUAL_CASH > BigDecimal.ZERO) totalValue.add(realizedBalance).subtract(STARTING_VIRTUAL_CASH).divide(STARTING_VIRTUAL_CASH, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100)) else BigDecimal.ZERO,
            "virtualCashBalance" to realizedBalance,
            "startingVirtualCash" to STARTING_VIRTUAL_CASH,
            "holdings" to holdings,
        )
    }

    @Transactional
    fun execute(userId: String, stockId: String, side: String, shares: BigDecimal): Map<String, Any?> {
        val normalized = side.uppercase()
        if (normalized != "BUY" && normalized != "SELL") throw InvalidOrderPreviewException("side must be BUY or SELL")
        if (shares <= BigDecimal.ZERO || shares.scale() > 6) throw InvalidOrderPreviewException("shares must be greater than zero with at most 6 decimal places")
        val stock = StockCatalog.find(stockId) ?: throw StockNotFoundException("Stock not found")
        val existing = practiceTrades.findByUserIdOrderByExecutedAtAsc(userId)
        val notional = shares.multiply(stock.price).setScale(2, RoundingMode.HALF_UP)
        val cash = existing.fold(STARTING_VIRTUAL_CASH) { balance, trade ->
            val amount = trade.shares.multiply(trade.price).setScale(2, RoundingMode.HALF_UP)
            when (trade.type) { "BUY" -> balance.subtract(amount); "SELL" -> balance.add(amount); else -> balance }
        }
        val ownedShares = existing.filter { it.stockId == stock.id }.fold(BigDecimal.ZERO) { acc, trade ->
            when (trade.type) { "BUY" -> acc.add(trade.shares); "SELL" -> acc.subtract(trade.shares); else -> acc }
        }
        if (normalized == "BUY" && cash < notional) throw InvalidOrderPreviewException("Insufficient virtual practice cash")
        if (normalized == "SELL" && ownedShares < shares) throw NotEnoughSharesException("Not enough practice shares to sell")
        val trade = practiceTrades.save(
            PracticeStockTrade(
                id = "practice_${UUID.randomUUID()}",
                userId = userId,
                stockId = stock.id,
                type = normalized,
                shares = shares,
                price = stock.price,
                executedAt = Instant.now(),
            ),
        )
        return mapOf(
            "id" to trade.id,
            "type" to "PRACTICE_${normalized}",
            "status" to "COMPLETED",
            "stockId" to stock.id,
            "symbol" to stock.symbol,
            "shares" to shares,
            "price" to stock.price,
            "notional" to notional,
            "currency" to "RWF",
            "executedAt" to trade.executedAt,
            "mode" to "SIMULATED",
            "realOrderExecutionAvailable" to false,
            "realLedgerMutated" to false,
            "message" to "Practice trade recorded. No real money or holdings were changed.",
        )
    }
}
