package rw.itunda.ledger.domain

import java.math.BigDecimal
import java.util.UUID

/**
 * Pure Domain Service for executing Transfers between Ledger Accounts.
 * Toss Rule: Business logic stays pure, independent of Spring or DB.
 */
class TransferService {

    fun executeTransfer(
        source: LedgerAccount,
        destination: LedgerAccount,
        amount: BigDecimal
    ): Pair<LedgerAccount, LedgerAccount> {
        require(source.currency == destination.currency) { "Cross-currency transfers require FX handling" }
        require(source.accountId != destination.accountId) { "Cannot transfer to the same account" }
        
        // Strict double-entry accounting execution
        val updatedSource = source.debit(amount)
        val updatedDestination = destination.credit(amount)

        return Pair(updatedSource, updatedDestination)
    }
}
