package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal

/** Mirrors backend/src/types/index.ts Holding.
 *
 * Real bug found live (2026-08-02): StocksService.buyStock/sellStock both read this
 * exact entity, check-then-mutate `shares` (sellStock explicitly guards against
 * overselling: `holding.shares < shares`), then save -- the same check-then-act shape
 * every other real money-moving entity in this codebase already needed `@Version` for
 * (Listing, SupportTicket, Ikimina, ...). With none here, two concurrent sellStock
 * calls for the same holding (e.g. two sell orders for 8 shares each on a 10-share
 * position, raced) could both read `shares == 10`, both pass the `< shares` guard,
 * and both post a real ledger payout (debiting `securities_suspense`, crediting the
 * user's account) for shares that were never actually available to sell twice -- real
 * money credited with no real custody behind it, a more severe instance of this bug
 * class than a simple lost update. `@Version` closes both that oversell race and the
 * quieter buyStock lost-update race (two concurrent buys losing one side's shares
 * even though both sides' accounts were correctly debited) -- the loser of either race
 * now real-409s via the existing global `ObjectOptimisticLockingFailureException`
 * handler instead of silently corrupting the position.
 */
@Entity
@Table(name = "holdings")
class Holding(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "account_id", nullable = false, length = 64)
    val accountId: String,

    @Column(name = "stock_id", nullable = false, length = 16)
    val stockId: String,

    @Column(nullable = false, precision = 18, scale = 4)
    var shares: BigDecimal,

    @Column(name = "avg_price", nullable = false, precision = 18, scale = 4)
    var avgPrice: BigDecimal,

    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(id = "", userId = "", accountId = "", stockId = "", shares = BigDecimal.ZERO, avgPrice = BigDecimal.ZERO)
}
