package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

/**
 * Real monthly spending budget -- closes docs/TOSS_PARITY_MATRIX.md's Spending row's
 * own named gap ("Budgeting/limits still target"). `category` is null for an overall
 * (all-spending) budget, or one of [rw.itunda.wallet.SpendingInsightResult]'s own
 * category names (e.g. "Bills", "Transfers") for a category-specific one -- reuses
 * `WalletService.getSpendingInsight`'s existing real categorization rather than a
 * separate, parallel one. `month` is a real calendar month ("yyyy-MM"); a budget resets
 * every month by definition, matching how a real household budget works, not a
 * rolling window. `notifiedNear`/`notifiedOver` track whether a real threshold-crossing
 * notification has already been sent for this specific budget this month, so re-checking
 * on every read doesn't spam the same alert repeatedly.
 */
@Entity
@Table(name = "spending_budgets")
class SpendingBudget(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(length = 32)
    val category: String? = null,

    @Column(name = "monthly_limit", nullable = false, precision = 18, scale = 2)
    var monthlyLimit: BigDecimal,

    @Column(nullable = false, length = 7)
    val month: String,

    @Column(name = "notified_near", nullable = false)
    var notifiedNear: Boolean = false,

    @Column(name = "notified_over", nullable = false)
    var notifiedOver: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    protected constructor() : this(
        id = "", userId = "", monthlyLimit = BigDecimal.ZERO, month = "",
    )
}
