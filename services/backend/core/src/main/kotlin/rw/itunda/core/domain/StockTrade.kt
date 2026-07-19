package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

/**
 * A real, immutable trade-log row (2026-07-20) -- one per real buy/sell execution,
 * closing "true historical portfolio value" honestly and safely. Deliberately purely
 * additive: `StocksService.buyStock`/`sellStock` ALSO save one of these alongside their
 * existing, already-tested `Holding` update, rather than that already-proven
 * money-movement logic being rewritten to derive its rolled-up `(shares, avgPrice)`
 * from this log instead -- this project's own established discipline favors additive
 * new entities over touching already-tested money-movement code, and `Holding`'s
 * existing weighted-average-cost math is exactly the kind of code that discipline
 * exists to protect.
 *
 * `StocksService.getPortfolioHistory` replays this real log (sum BUY shares - sum SELL
 * shares, filtered to `executedAt` on or before each target date) to reconstruct the
 * REAL number of shares actually held on a given past day -- not the "current holdings
 * applied to past prices" approximation this feature shipped with initially.
 */
@Entity
@Table(name = "stock_trades")
class StockTrade(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "stock_id", nullable = false, length = 16)
    val stockId: String,

    @Column(nullable = false, length = 8)
    val type: String,

    @Column(nullable = false, precision = 18, scale = 4)
    val shares: BigDecimal,

    @Column(nullable = false, precision = 18, scale = 2)
    val price: BigDecimal,

    @Column(name = "executed_at", nullable = false)
    val executedAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", stockId = "", type = "", shares = BigDecimal.ZERO, price = BigDecimal.ZERO)
}
