package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

/**
 * Non-wallet ledger clearing accounts (fee income, money in flight to external rails/
 * custody/lending) — mirrors the `ledgerAccounts` map in backend/src/services/ledger.ts.
 * Kept separate from `wallets` so they never show up as a customer-facing balance.
 */
@Entity
@Table(name = "ledger_accounts")
class LedgerAccount(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false, precision = 18, scale = 2)
    var balance: BigDecimal = BigDecimal.ZERO,
) {
    protected constructor() : this(id = "", name = "")

    companion object {
        val SEED_IDS = listOf(
            "fee_revenue" to "Fee Revenue",
            "rail_suspense" to "Rail Settlement Suspense",
            "loan_payable" to "Loan Principal Payable",
            "securities_suspense" to "Securities Custody Suspense",
            "savings_goal_payable" to "Savings Goal Payable",
            "interest_expense" to "Interest Expense",
            "insurance_premium_revenue" to "Insurance Premium Revenue",
            "rewards_expense" to "Rewards Expense",
            "insurance_claims_expense" to "Insurance Claims Expense",
        )
    }
}
