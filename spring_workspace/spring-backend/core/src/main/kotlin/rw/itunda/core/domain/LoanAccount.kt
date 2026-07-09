package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

enum class LoanStatus { ACTIVE, PAID }

/** Mirrors backend/src/types/index.ts LoanAccount. */
@Entity
@Table(name = "loan_accounts")
class LoanAccount(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "wallet_id", nullable = false, length = 64)
    val walletId: String,

    @Column(name = "offer_id", nullable = false, length = 40)
    val offerId: String,

    @Column(nullable = false, precision = 18, scale = 2)
    val principal: BigDecimal,

    @Column(nullable = false, precision = 18, scale = 2)
    var outstanding: BigDecimal,

    @Column(name = "interest_rate", nullable = false)
    val interestRate: Double,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: LoanStatus,

    @Column(name = "disbursed_at", nullable = false)
    val disbursedAt: Instant = Instant.now(),
) {
    protected constructor() : this(
        id = "", userId = "", walletId = "", offerId = "",
        principal = BigDecimal.ZERO, outstanding = BigDecimal.ZERO, interestRate = 0.0, status = LoanStatus.ACTIVE,
    )
}
