package rw.itunda.stocks

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.Holding
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.HoldingRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

class StockNotFoundException(message: String) : RuntimeException(message)
class NoWalletException(message: String) : RuntimeException(message)
class NotEnoughSharesException(message: String) : RuntimeException(message)

/** Port of backend/src/controllers/stock.controller.ts. */
@Service
class StocksService(
    private val walletRepository: WalletRepository,
    private val holdingRepository: HoldingRepository,
    private val ledgerService: LedgerService,
) {
    fun getStocks() = StockCatalog.stocks

    fun getPortfolio(userId: String): Map<String, Any?> {
        val holdings = holdingRepository.findByUserId(userId).filter { it.shares > BigDecimal.ZERO }
        val enriched = holdings.map { holding ->
            val stock = StockCatalog.find(holding.stockId)!!
            val value = holding.shares.multiply(stock.price)
            val cost = holding.shares.multiply(holding.avgPrice)
            mapOf(
                "stockId" to holding.stockId, "symbol" to stock.symbol, "name" to stock.name,
                "shares" to holding.shares, "avgPrice" to holding.avgPrice, "currentPrice" to stock.price,
                "value" to value,
                "return" to if (cost > BigDecimal.ZERO) value.subtract(cost).divide(cost, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100)) else BigDecimal.ZERO,
            )
        }
        val totalValue = enriched.sumOf { it["value"] as BigDecimal }
        val totalCost = holdings.sumOf { it.shares.multiply(it.avgPrice) }
        return mapOf(
            "totalValue" to totalValue,
            "totalReturn" to totalValue.subtract(totalCost),
            "totalReturnPercent" to if (totalCost > BigDecimal.ZERO) totalValue.subtract(totalCost).divide(totalCost, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100)) else BigDecimal.ZERO,
            "holdings" to enriched,
        )
    }

    @Transactional
    fun buyStock(userId: String, stockId: String, shares: BigDecimal): Map<String, Any?> {
        val stock = StockCatalog.find(stockId) ?: throw StockNotFoundException("Stock not found")
        val wallet = walletRepository.findByUserIdAndType(userId, WalletType.INVESTMENT) ?: throw NoWalletException("No investment wallet found for this account")
        val cost = shares.multiply(stock.price)

        val result = ledgerService.postLedgerTransaction(
            wallet.currency,
            listOf(
                LedgerLeg(wallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, cost, "Buy $shares ${stock.symbol}"),
                LedgerLeg("securities_suspense", LedgerAccountType.SECURITIES_SUSPENSE, LedgerDirection.CREDIT, cost, "Custody for $shares ${stock.symbol}"),
            ),
        )

        val holding = holdingRepository.findByUserIdAndStockId(userId, stock.id)
            ?: Holding(id = "hold_${UUID.randomUUID()}", userId = userId, walletId = wallet.id, stockId = stock.id, shares = BigDecimal.ZERO, avgPrice = stock.price)
        val totalCostBasis = holding.shares.multiply(holding.avgPrice).add(cost)
        holding.shares = holding.shares.add(shares)
        holding.avgPrice = totalCostBasis.divide(holding.shares, 4, RoundingMode.HALF_UP)
        holdingRepository.save(holding)

        return mapOf(
            "id" to result.transactionId, "referenceNumber" to "STK${System.currentTimeMillis()}",
            "type" to "INVESTMENT", "status" to "COMPLETED",
            "description" to "Bought $shares shares of ${stock.symbol}", "completedAt" to Instant.now().toString(),
        )
    }

    @Transactional
    fun sellStock(userId: String, stockId: String, shares: BigDecimal): Map<String, Any?> {
        val stock = StockCatalog.find(stockId) ?: throw StockNotFoundException("Stock not found")
        val holding = holdingRepository.findByUserIdAndStockId(userId, stock.id)
        if (holding == null || holding.shares < shares) throw NotEnoughSharesException("Not enough shares to sell")

        val proceeds = shares.multiply(stock.price)
        val result = ledgerService.postLedgerTransaction(
            "RWF",
            listOf(
                LedgerLeg("securities_suspense", LedgerAccountType.SECURITIES_SUSPENSE, LedgerDirection.DEBIT, proceeds, "Release custody for $shares ${stock.symbol}"),
                LedgerLeg(holding.walletId, LedgerAccountType.WALLET, LedgerDirection.CREDIT, proceeds, "Sell $shares ${stock.symbol}"),
            ),
        )

        holding.shares = holding.shares.subtract(shares)
        holdingRepository.save(holding)

        return mapOf(
            "id" to result.transactionId, "referenceNumber" to "STK${System.currentTimeMillis()}",
            "type" to "INVESTMENT", "status" to "COMPLETED",
            "description" to "Sold $shares shares of ${stock.symbol}", "completedAt" to Instant.now().toString(),
        )
    }
}
