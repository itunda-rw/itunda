package rw.itunda.core.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

enum class InsurancePremiumFundStatus { active, cancelled }

@Entity
@Table(name = "insurance_premium_funds")
class InsurancePremiumFund(
    @Id @Column(length = 64) val id: String,
    @Column(name = "user_id", nullable = false, length = 64) val userId: String,
    @Column(name = "policy_id", nullable = false, length = 64) val policyId: String,
    @Column(name = "target_amount", nullable = false, precision = 18, scale = 2) val targetAmount: BigDecimal,
    @Column(name = "current_amount", nullable = false, precision = 18, scale = 2) var currentAmount: BigDecimal,
    @Column(name = "daily_contribution", nullable = false, precision = 18, scale = 2) val dailyContribution: BigDecimal,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) var status: InsurancePremiumFundStatus = InsurancePremiumFundStatus.active,
    @Column(name = "last_auto_contribution_at") var lastAutoContributionAt: Instant? = null,
    @Column(name = "created_at", nullable = false) val createdAt: Instant = Instant.now(),
    @Version @Column(nullable = false) var version: Long = 0,
) {
    protected constructor() : this(id = "", userId = "", policyId = "", targetAmount = BigDecimal.ZERO, currentAmount = BigDecimal.ZERO, dailyContribution = BigDecimal.ZERO)
}
