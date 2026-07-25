package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

enum class UpfrontInterestDepositStatus { ACTIVE, MATURED }

/**
 * Real Toss Bank 먼저 이자받는 정기예금 (interest-paid-upfront term deposit) equivalent
 * -- see docs/DESIGN_REFERENCES.md Section 4 References table for the sourced mechanics
 * (tossbank.com product pages, cross-verified against alphabiz.co.kr/heraldcorp.com
 * press coverage of the real 2025 product launch): the full year's interest is paid the
 * moment the deposit opens, not at maturity like every other savings product in this
 * module (`WeeklySavingsPlan`, `InterestJar`, `SavingsGoal`).
 *
 * Deliberately has NO early-withdrawal/cancel path, unlike `WeeklySavingsPlan.cancelPlan`
 * -- this is the one real, load-bearing design choice this feature depends on. Paying
 * interest upfront and then allowing the principal to be pulled back out immediately
 * would be a real, unlimited money-printing exploit (open, get paid, withdraw, repeat).
 * The real product's own structure is a genuinely locked term deposit for exactly this
 * reason; `[principal]` only ever becomes spendable again once `maturesAt` has actually
 * passed and `UpfrontInterestDepositService.withdraw` is called. Not modeled: the real
 * product's tax-withheld-from-principal-at-closing detail -- no savings product in this
 * codebase models withholding tax yet, and inventing a Rwandan withholding rate without
 * a real source would be dishonest scoping, not a deliberate simplification worth hiding.
 */
@Entity
@Table(name = "upfront_interest_deposits")
class UpfrontInterestDeposit(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "wallet_id", nullable = false, length = 64)
    val walletId: String,

    @Column(nullable = false, precision = 18, scale = 2)
    val principal: BigDecimal,

    @Column(name = "interest_rate", nullable = false)
    val interestRate: Double,

    @Column(name = "interest_paid", nullable = false, precision = 18, scale = 2)
    val interestPaid: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: UpfrontInterestDepositStatus = UpfrontInterestDepositStatus.ACTIVE,

    @Column(name = "opened_at", nullable = false)
    val openedAt: Instant = Instant.now(),

    @Column(name = "matures_at", nullable = false)
    val maturesAt: Instant,

    @Column(name = "matured_at")
    var maturedAt: Instant? = null,

    @Column(name = "withdrawn_at")
    var withdrawnAt: Instant? = null,
) {
    protected constructor() : this(
        id = "", userId = "", walletId = "", principal = BigDecimal.ZERO,
        interestRate = 0.0, interestPaid = BigDecimal.ZERO, maturesAt = Instant.now(),
    )
}
