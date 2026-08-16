package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

/**
 * A real watched stock (2026-07-19) -- the "follow a stock without holding it" capability
 * every real securities app has, closing part of "Toss Securities as its own distinct
 * surface." Deliberately minimal, same shape as `EatsFavorite`: just the (user, stock)
 * pair and when it was watched. Real DB unique constraint on (user_id, stock_id) backs
 * the same application-level "add is idempotent" check `StocksService.watchStock` makes,
 * not just the application check alone -- a genuine race between two concurrent watch
 * taps for the same stock still can't create two rows.
 */
@Entity
@Table(name = "stock_watchlist")
class StockWatchlist(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "stock_id", nullable = false, length = 64)
    val stockId: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    // Real Toss Securities 목표가 알림 (target price alert) (2026-08-16) -- Toss's own
    // real feature: set a target price on a watched stock and get notified once it's
    // crossed. Null means no active alert, every existing watchlist row's behavior
    // completely unchanged. "ABOVE"/"BELOW" is the direction the user is watching for
    // (explicit, never inferred from the price at set-time, so the intent stays
    // correct even if the price has already moved by the time this is read back).
    // One-shot: alertTriggeredAt marks it fired so the scheduler never re-notifies for
    // the same crossing -- the user re-arms it by setting a new target.
    @Column(name = "target_price", precision = 18, scale = 2)
    var targetPrice: BigDecimal? = null,

    @Column(name = "target_direction", length = 8)
    var targetDirection: String? = null,

    @Column(name = "alert_triggered_at")
    var alertTriggeredAt: Instant? = null,
) {
    protected constructor() : this(id = "", userId = "", stockId = "")
}
