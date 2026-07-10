package rw.itunda.ledger.domain

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Pure Domain Model for an Account in the Ledger.
 * Toss Rule: Zero frameworks here. Just pure Kotlin business rules.
 */
data class LedgerAccount(
    val accountId: String,
    val customerId: String,
    val currency: String = "RWF", // Built for Rwanda
    val balance: BigDecimal,
    val accountType: AccountType,
    val status: AccountStatus
) {
    fun canDebit(amount: BigDecimal): Boolean {
        if (status != AccountStatus.ACTIVE) return false
        if (amount <= BigDecimal.ZERO) return false
        // For liabilities or user wallets, they can't go below zero without credit limits.
        return balance >= amount
    }

    fun debit(amount: BigDecimal): LedgerAccount {
        require(canDebit(amount)) { "Insufficient funds or invalid account state" }
        return this.copy(balance = this.balance - amount)
    }

    fun credit(amount: BigDecimal): LedgerAccount {
        require(status == AccountStatus.ACTIVE) { "Account is not active" }
        require(amount > BigDecimal.ZERO) { "Amount must be positive" }
        return this.copy(balance = this.balance + amount)
    }
}

enum class AccountType {
    USER_WALLET,
    SAVINGS_GOAL,
    LOAN_PAYABLE,
    MERCHANT_SETTLEMENT,
    FEE_REVENUE,
    RAIL_SUSPENSE // e.g., waiting for MTN MoMo confirmation
}

enum class AccountStatus {
    ACTIVE,
    FROZEN,
    CLOSED
}
