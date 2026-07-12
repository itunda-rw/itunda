package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

enum class LedgerDirection { DEBIT, CREDIT }

enum class LedgerAccountType {
    WALLET, FEE_REVENUE, RAIL_SUSPENSE, LOAN_PAYABLE, SECURITIES_SUSPENSE,
    SAVINGS_GOAL_PAYABLE, INTEREST_EXPENSE, INSURANCE_PREMIUM_REVENUE, REWARDS_EXPENSE,
    INSURANCE_CLAIMS_EXPENSE,
}

/**
 * Append-only double-entry ledger row. Mirrors backend/src/types/index.ts LedgerEntry
 * and the invariant enforced by backend/src/services/ledger.ts: every transactionId's
 * entries must sum to zero (debits == credits) before any of them are persisted.
 */
@Entity
@Table(name = "ledger_entries")
class LedgerEntry(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "transaction_id", nullable = false, length = 64)
    val transactionId: String,

    @Column(name = "account_id", nullable = false, length = 64)
    val accountId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 32)
    val accountType: LedgerAccountType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    val direction: LedgerDirection,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Column(nullable = false, length = 8)
    val currency: String,

    @Column(name = "balance_after", nullable = false, precision = 18, scale = 2)
    val balanceAfter: BigDecimal,

    @Column(nullable = false, length = 255)
    val memo: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(
        id = "", transactionId = "", accountId = "", accountType = LedgerAccountType.WALLET,
        direction = LedgerDirection.DEBIT, amount = BigDecimal.ZERO, currency = "RWF",
        balanceAfter = BigDecimal.ZERO, memo = "",
    )
}
