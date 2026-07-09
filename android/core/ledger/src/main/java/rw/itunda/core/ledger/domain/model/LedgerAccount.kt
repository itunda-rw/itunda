package rw.itunda.core.ledger.domain.model

import java.math.BigDecimal

/**
 * Represents a single ledger account in the double-entry system.
 * This can represent a user's wallet, a bank account, or a system holding account.
 */
data class LedgerAccount(
    val id: String,
    val customerId: String,
    val type: AccountType,
    val currency: String, // e.g., "RWF"
    val postedBalance: BigDecimal, // Settled and cleared balance
    val pendingBalance: BigDecimal, // Outstanding holds or processing debits
    val isActive: Boolean
) {
    val availableBalance: BigDecimal
        get() = postedBalance - pendingBalance
}

enum class AccountType {
    ASSET,       // User's deposited money
    LIABILITY,   // Money the platform owes (e.g., to merchants)
    EQUITY,      // Platform equity
    REVENUE,     // Fees collected
    EXPENSE      // Platform expenses
}
