package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
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
) {
    protected constructor() : this(id = "", userId = "", stockId = "")
}
