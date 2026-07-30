package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant

enum class SavingsGoalStatus { active, completed }

/** Mirrors backend/src/types/index.ts SavingsGoal. */
@Entity
@Table(name = "savings_goals")
class SavingsGoal(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "wallet_id", nullable = false, length = 64)
    val walletId: String,

    @Column(nullable = false)
    var name: String,

    @Column(name = "target_amount", nullable = false, precision = 18, scale = 2)
    val targetAmount: BigDecimal,

    @Column(name = "current_amount", nullable = false, precision = 18, scale = 2)
    var currentAmount: BigDecimal,

    @Column(name = "monthly_contribution", nullable = false, precision = 18, scale = 2)
    val monthlyContribution: BigDecimal,

    @Column(name = "interest_rate", nullable = false)
    val interestRate: Double,

    @Column(name = "target_date", length = 32)
    val targetDate: String? = null,

    @Column(length = 32)
    val category: String = "general",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: SavingsGoalStatus = SavingsGoalStatus.active,

    @Column(length = 16)
    val color: String = "#0066FF",

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    // Real recurring auto-save (2026-07-13) -- null means "never auto-contributed yet",
    // treated as immediately due. See AutoSaveScheduler in the savings module.
    @Column(name = "last_auto_contribution_at")
    var lastAutoContributionAt: Instant? = null,

    // Manual deposits and the recurring auto-save scheduler update this balance
    // independently.  Versioning prevents a lost contribution update.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(id = "", userId = "", walletId = "", name = "", targetAmount = BigDecimal.ZERO, currentAmount = BigDecimal.ZERO, monthlyContribution = BigDecimal.ZERO, interestRate = 0.0)
}
