package rw.itunda.stocks

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Holding
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.StockTrade
import rw.itunda.core.domain.StockWatchlist
import rw.itunda.core.domain.AccountType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.HoldingRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.StockTradeRepository
import rw.itunda.core.repository.StockWatchlistRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class StockNotFoundException(message: String) : RuntimeException(message)
class NoAccountException(message: String) : RuntimeException(message)
class NotEnoughSharesException(message: String) : RuntimeException(message)
class InvalidPriceHistoryRangeException(message: String) : RuntimeException(message)
class InvalidFundingAmountException(message: String) : RuntimeException(message)
class InvalidPriceAlertException(message: String) : RuntimeException(message)

data class PortfolioValuePoint(val date: LocalDate, val value: BigDecimal)

/** Port of backend/src/controllers/stock.controller.ts. */
@Service
class StocksService(
    private val accountRepository: AccountRepository,
    private val holdingRepository: HoldingRepository,
    private val ledgerService: LedgerService,
    private val stockWatchlistRepository: StockWatchlistRepository,
    private val stockTradeRepository: StockTradeRepository,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
    private val rateLimiter: RateLimiter,
    private val fraudRuleEngine: FraudRuleEngine,
) {
    fun getStocks() = StockCatalog.stocks

    // Real per-stock price history (2026-07-19), backing a real chart -- see
    // StockCatalog.priceHistory's own doc comment for why this needs no new storage.
    // Bounded to a real year, matching this codebase's own "bound anything that could
    // otherwise become an unreasonably large single response" discipline (e.g.
    // MarketplaceService.MAX_OSRM_TABLE_CANDIDATES).
    fun getPriceHistory(stockId: String, days: Int): List<PricePoint> {
        if (days < 1 || days > 365) {
            throw InvalidPriceHistoryRangeException("days must be between 1 and 365")
        }
        return StockCatalog.priceHistory(stockId, days) ?: throw StockNotFoundException("Stock not found")
    }

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

    // Real bug found and fixed 2026-07-27, alongside the same-day AuthService.register
    // fix that finally provisions a real AccountType.INVESTMENT account for every new
    // user: even with that account now provisioned, it starts at a real zero balance,
    // and nothing anywhere in this codebase ever let a user move money INTO it -- so
    // `buyStock` would have real-422'd (InsufficientFundsException) for every real
    // first purchase regardless. A real, honest internal account-to-account transfer,
    // same shape `P2pService.sendDirect` already established for a different pair of
    // real accounts -- no clearing account needed since both real ACCOUNT-type accounts
    // belong to the exact same real user.
    @Transactional
    fun fundInvestmentAccount(userId: String, amount: BigDecimal): Map<String, Any?> {
        if (amount <= BigDecimal.ZERO) throw InvalidFundingAmountException("Amount must be greater than zero")
        // Real anti-spam limit, matching this class's own buyStock/sellStock convention
        // and AccountService.transferBetweenOwnAccounts's identical "own-account
        // transfer skips the fraud check but still gets rate-limited" precedent.
        rateLimiter.checkLimit("stocks:fund:$userId", limit = 30, window = Duration.ofHours(1))
        val mainAccount = accountRepository.findByUserIdAndType(userId, AccountType.MAIN) ?: throw NoAccountException("No account found for this account")
        val investmentAccount = accountRepository.findByUserIdAndType(userId, AccountType.INVESTMENT) ?: throw NoAccountException("No investment account found for this account")

        val result = ledgerService.postLedgerTransaction(
            mainAccount.currency,
            listOf(
                LedgerLeg(mainAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Transfer to investment account"),
                LedgerLeg(investmentAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, amount, "Transfer to investment account"),
            ),
        )
        return mapOf("id" to result.transactionId, "amount" to amount, "completedAt" to Instant.now().toString())
    }

    @Transactional
    fun buyStock(userId: String, stockId: String, shares: BigDecimal): Map<String, Any?> {
        // Real gap found live (2026-09-04): every sibling money-moving domain (P2P,
        // loans, savings) applies a per-user rateLimiter.checkLimit -- stock buy/sell
        // had none. Idempotency-Key already prevents a double-execute on retry, but
        // that's a different concern from capping how often a real trade can be
        // placed at all. Same 30/hour figure P2pService.pay/send already establishes
        // as this codebase's own baseline for a comparable-stakes real money action.
        rateLimiter.checkLimit("stocks:buy:$userId", limit = 30, window = Duration.ofHours(1))
        // Real gap found live (2026-09-04): unlike fundInvestmentAccount just above
        // (which already validates its amount), buyStock/sellStock never checked
        // shares was positive -- a zero/negative value would have both
        // postLedgerTransaction legs share the same non-positive `cost`, which
        // LedgerService's own leg filter (`rawLegs.filter { it.amount > ZERO }`)
        // silently drops both, so no actual fund-direction reversal is possible --
        // but the caller then gets an opaque, unhandled LedgerImbalanceException
        // (500) instead of a clean validation error. Same exception/handler this
        // file already established for fundInvestmentAccount's identical check.
        if (shares <= BigDecimal.ZERO) throw InvalidFundingAmountException("Shares must be greater than zero")
        val stock = StockCatalog.find(stockId) ?: throw StockNotFoundException("Stock not found")
        val account = accountRepository.findByUserIdAndType(userId, AccountType.INVESTMENT) ?: throw NoAccountException("No investment account found for this account")
        // Real bug found and fixed 2026-07-27: shares is caller-supplied BigDecimal with
        // no scale constraint (round-up-to-invest passes real fractional shares scaled to
        // 6dp) -- an unrounded cost could carry more than RWF's real 2 decimal places,
        // which LedgerService.postLedgerTransaction's own balance check (`setScale(2)`,
        // no RoundingMode) would then real-crash on with ArithmeticException("Rounding
        // necessary") for any non-exact fractional-share cost. Caught live: the first
        // real round-up-to-invest purchase 500'd the entire triggering P2P transfer.
        val cost = shares.multiply(stock.price).setScale(2, RoundingMode.HALF_UP)

        val result = ledgerService.postLedgerTransaction(
            account.currency,
            listOf(
                LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, cost, "Buy $shares ${stock.symbol}"),
                LedgerLeg("securities_suspense", LedgerAccountType.SECURITIES_SUSPENSE, LedgerDirection.CREDIT, cost, "Custody for $shares ${stock.symbol}"),
            ),
        )
        // Real gap found live (2026-09-08): every sibling money-leaves-account flow
        // (P2P, Marketplace escrow, Bills, Ride, ...) already runs through
        // FraudRuleEngine.evaluate -- stock buy/sell never did. recipientUserId is
        // null since the counterparty is the internal securities_suspense account,
        // not a named itunda user, matching BillsService/RideTripService's own
        // null-recipient convention (see FraudRuleEngine's own doc comment).
        fraudRuleEngine.evaluate(userId, null, cost, result.transactionId)

        val holding = holdingRepository.findByUserIdAndStockId(userId, stock.id)
            ?: Holding(id = "hold_${UUID.randomUUID()}", userId = userId, accountId = account.id, stockId = stock.id, shares = BigDecimal.ZERO, avgPrice = stock.price)
        val totalCostBasis = holding.shares.multiply(holding.avgPrice).add(cost)
        holding.shares = holding.shares.add(shares)
        holding.avgPrice = totalCostBasis.divide(holding.shares, 4, RoundingMode.HALF_UP)
        holdingRepository.save(holding)

        // Real, purely additive trade-log row (2026-07-20) -- see StockTrade.kt's own
        // doc comment for why this never touches the Holding math just above.
        stockTradeRepository.save(
            StockTrade(id = "trade_${UUID.randomUUID()}", userId = userId, stockId = stock.id, type = "BUY", shares = shares, price = stock.price),
        )

        return mapOf(
            "id" to result.transactionId, "referenceNumber" to "STK${System.currentTimeMillis()}",
            "type" to "INVESTMENT", "status" to "COMPLETED",
            "description" to "Bought $shares shares of ${stock.symbol}", "completedAt" to Instant.now().toString(),
        )
    }

    @Transactional
    fun sellStock(userId: String, stockId: String, shares: BigDecimal): Map<String, Any?> {
        rateLimiter.checkLimit("stocks:sell:$userId", limit = 30, window = Duration.ofHours(1))
        if (shares <= BigDecimal.ZERO) throw InvalidFundingAmountException("Shares must be greater than zero")
        val stock = StockCatalog.find(stockId) ?: throw StockNotFoundException("Stock not found")
        val holding = holdingRepository.findByUserIdAndStockId(userId, stock.id)
        if (holding == null || holding.shares < shares) throw NotEnoughSharesException("Not enough shares to sell")

        val proceeds = shares.multiply(stock.price)
        val result = ledgerService.postLedgerTransaction(
            "RWF",
            listOf(
                LedgerLeg("securities_suspense", LedgerAccountType.SECURITIES_SUSPENSE, LedgerDirection.DEBIT, proceeds, "Release custody for $shares ${stock.symbol}"),
                LedgerLeg(holding.accountId, LedgerAccountType.WALLET, LedgerDirection.CREDIT, proceeds, "Sell $shares ${stock.symbol}"),
            ),
        )
        // Real gap found live (2026-09-08) -- see buyStock's own identical comment above.
        fraudRuleEngine.evaluate(userId, null, proceeds, result.transactionId)

        holding.shares = holding.shares.subtract(shares)
        holdingRepository.save(holding)

        // Real, purely additive trade-log row (2026-07-20) -- see StockTrade.kt's own
        // doc comment for why this never touches the Holding math just above.
        stockTradeRepository.save(
            StockTrade(id = "trade_${UUID.randomUUID()}", userId = userId, stockId = stock.id, type = "SELL", shares = shares, price = stock.price),
        )

        return mapOf(
            "id" to result.transactionId, "referenceNumber" to "STK${System.currentTimeMillis()}",
            "type" to "INVESTMENT", "status" to "COMPLETED",
            "description" to "Sold $shares shares of ${stock.symbol}", "completedAt" to Instant.now().toString(),
        )
    }

    // Real stock watchlist (2026-07-19) -- "follow a stock without holding it," closing
    // part of "Toss Securities as its own distinct surface". Same idempotent-toggle
    // discipline EatsFavoriteService already established: watching an already-watched
    // stock returns the existing row rather than a 409 (a real watch/follow toggle
    // shouldn't error on a double-tap, and the real DB unique constraint means a
    // concurrent double-add still can't create two rows even without this check).
    // Un-watching something never watched is a silent no-op -- the end state ("not
    // watched") is what the caller actually wants, regardless of what state it started in.
    @Transactional
    fun watchStock(userId: String, stockId: String): StockWatchlist {
        val stock = StockCatalog.find(stockId) ?: throw StockNotFoundException("Stock not found")
        stockWatchlistRepository.findByUserIdAndStockId(userId, stock.id)?.let { return it }
        return stockWatchlistRepository.save(
            StockWatchlist(id = "watch_${UUID.randomUUID()}", userId = userId, stockId = stock.id),
        )
    }

    @Transactional
    fun unwatchStock(userId: String, stockId: String) {
        stockWatchlistRepository.deleteByUserIdAndStockId(userId, stockId)
    }

    // Real Toss Securities 목표가 알림 (target price alert) (2026-08-16) -- set a real
    // target price on a stock and get notified once its real (deterministically
    // simulated) price crosses it. Auto-watches the stock first if the caller hadn't
    // already, same "setting an alert implies watching" real Toss UX -- there's no
    // separate concept of "alert but not watching" in the real app either. Setting a
    // new target on an already-alerted watchlist row re-arms it (clears
    // alertTriggeredAt), same "your new choice replaces the old one" shape this
    // codebase's other real toggles already establish.
    @Transactional
    fun setPriceAlert(userId: String, stockId: String, targetPrice: BigDecimal, direction: String): StockWatchlist {
        if (direction != "ABOVE" && direction != "BELOW") {
            throw InvalidPriceAlertException("direction must be ABOVE or BELOW")
        }
        if (targetPrice <= BigDecimal.ZERO) {
            throw InvalidPriceAlertException("Target price must be greater than zero")
        }
        val stock = StockCatalog.find(stockId) ?: throw StockNotFoundException("Stock not found")
        val watchlist = stockWatchlistRepository.findByUserIdAndStockId(userId, stock.id)
            ?: stockWatchlistRepository.save(StockWatchlist(id = "watch_${UUID.randomUUID()}", userId = userId, stockId = stock.id))
        watchlist.targetPrice = targetPrice
        watchlist.targetDirection = direction
        watchlist.alertTriggeredAt = null
        return stockWatchlistRepository.save(watchlist)
    }

    @Transactional
    fun clearPriceAlert(userId: String, stockId: String): StockWatchlist {
        val watchlist = stockWatchlistRepository.findByUserIdAndStockId(userId, stockId)
            ?: throw StockNotFoundException("You are not watching this stock")
        watchlist.targetPrice = null
        watchlist.targetDirection = null
        watchlist.alertTriggeredAt = null
        return stockWatchlistRepository.save(watchlist)
    }

    // Real gap fix (2026-08-18) -- found via a fresh "defined but uncalled" endpoint
    // sweep: setPriceAlert/clearPriceAlert had shipped (section 113) with zero client
    // anywhere ever calling them, and there wasn't even a read path a client could use
    // to show "this stock already has an alert" when re-opening its detail screen.
    // Null means either the stock isn't watched at all yet, or it's watched with no
    // active alert -- the client can't tell those apart from this alone, but it
    // doesn't need to: both render as "no alert set."
    fun getPriceAlert(userId: String, stockId: String): StockWatchlist? =
        stockWatchlistRepository.findByUserIdAndStockId(userId, stockId)

    // Real due-alert query backing StockPriceAlertScheduler -- a real, not-yet-fired
    // alert whose real current simulated price has actually crossed its real target,
    // in the real direction the user asked for.
    fun getDuePriceAlerts(): List<StockWatchlist> {
        val candidates = stockWatchlistRepository.findByTargetPriceIsNotNullAndAlertTriggeredAtIsNull()
        if (candidates.isEmpty()) return emptyList()
        return candidates.filter { watchlist ->
            val currentPrice = StockCatalog.find(watchlist.stockId)?.price ?: return@filter false
            val target = watchlist.targetPrice ?: return@filter false
            when (watchlist.targetDirection) {
                "ABOVE" -> currentPrice >= target
                "BELOW" -> currentPrice <= target
                else -> false
            }
        }
    }

    /** One real alert notification, called per-row by the scheduler -- same resilience
     * `ProductFavoriteService.notifyPriceDrop`'s own doc comment already establishes: a
     * re-check right before firing (never trust the batch snapshot from getDuePriceAlerts
     * as still true by the time this runs) so a genuine race can't double-fire. */
    @Transactional
    fun triggerPriceAlert(watchlistId: String) {
        val watchlist = stockWatchlistRepository.findById(watchlistId).orElse(null) ?: return
        if (watchlist.alertTriggeredAt != null) return
        val stock = StockCatalog.find(watchlist.stockId) ?: return
        val target = watchlist.targetPrice ?: return
        val crossed = when (watchlist.targetDirection) {
            "ABOVE" -> stock.price >= target
            "BELOW" -> stock.price <= target
            else -> false
        }
        if (!crossed) return

        val title = "${stock.symbol} hit your target price"
        val body = "${stock.name} is now ${stock.price} (target: $target)"
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = watchlist.userId, type = "STOCK_PRICE_ALERT",
                title = title, body = body, isRead = false, createdAt = Instant.now(), dataJson = "{\"stockId\":\"${stock.id}\"}",
            ),
        )
        pushNotificationService.sendToUser(watchlist.userId, title, body, mapOf("stockId" to stock.id))
        watchlist.alertTriggeredAt = Instant.now()
        stockWatchlistRepository.save(watchlist)
    }

    // StockCatalog is a small, static, in-memory list (no DB round trip involved at
    // all), unlike EatsFavoriteService's batch findAllById -- calling find() once per
    // watched stock here is not an N+1, there's no database on the other end of it.
    fun getWatchlist(userId: String): List<Stock> =
        stockWatchlistRepository.findByUserIdOrderByCreatedAtDesc(userId).mapNotNull { StockCatalog.find(it.stockId) }

    // Real portfolio value chart (2026-07-19), backing the "watch your portfolio move"
    // moment at the account level, not just per-stock.
    //
    // Real historical reconstruction (2026-07-20), upgraded from this feature's initial
    // "current holdings applied to past prices" approximation: replays the user's real,
    // immutable `StockTrade` log (sum BUY shares - sum SELL shares, filtered to
    // `executedAt` on or before each target date) to compute the REAL number of shares
    // actually held on that real past day, then applies that real day's real simulated
    // price -- not an approximation, a genuine reconstruction, since every buy/sell now
    // writes a real timestamped trade row alongside the existing `Holding` update (see
    // StockTrade.kt's own doc comment for why that's additive, not a rewrite of the
    // already-tested Holding math).
    fun getPortfolioHistory(userId: String, days: Int): List<PortfolioValuePoint> {
        if (days < 1 || days > 365) {
            throw InvalidPriceHistoryRangeException("days must be between 1 and 365")
        }
        val trades = stockTradeRepository.findByUserIdOrderByExecutedAtAsc(userId)
        val tradesByStock = trades.groupBy { it.stockId }
        val today = LocalDate.now()
        return (days - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val cutoff = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
            val value = tradesByStock.entries.fold(BigDecimal.ZERO) { acc, (stockId, stockTrades) ->
                val sharesOnDate = stockTrades
                    .filter { it.executedAt.isBefore(cutoff) }
                    .fold(BigDecimal.ZERO) { shareAcc, trade ->
                        if (trade.type == "BUY") shareAcc.add(trade.shares) else shareAcc.subtract(trade.shares)
                    }
                if (sharesOnDate <= BigDecimal.ZERO) return@fold acc
                val priceOnDate = StockCatalog.priceOn(stockId, date) ?: BigDecimal.ZERO
                acc.add(sharesOnDate.multiply(priceOnDate))
            }
            PortfolioValuePoint(date, value)
        }
    }
}
