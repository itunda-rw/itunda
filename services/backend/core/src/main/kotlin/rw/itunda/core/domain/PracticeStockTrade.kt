package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

/**
 * Paper-trading execution record isolated from real stock trades, holdings,
 * accounts, and the money ledger.
 */
@Entity
@Table(name = "practice_stock_trades")
class PracticeStockTrade(
    @Id @Column(length = 64)
    val id: String,
    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,
    @Column(name = "stock_id", nullable = false, length = 16)
    val stockId: String,
    @Column(nullable = false, length = 8)
    val type: String,
    @Column(nullable = false, precision = 18, scale = 6)
    val shares: BigDecimal,
    @Column(nullable = false, precision = 18, scale = 2)
    val price: BigDecimal,
    @Column(name = "executed_at", nullable = false)
    val executedAt: Instant = Instant.now(),
) {
    protected constructor() : this("", "", "", "", BigDecimal.ZERO, BigDecimal.ZERO)
}
