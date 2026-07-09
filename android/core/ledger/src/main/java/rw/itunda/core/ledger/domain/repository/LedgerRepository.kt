package rw.itunda.core.ledger.domain.repository

import kotlinx.coroutines.flow.Flow
import rw.itunda.core.ledger.domain.model.LedgerAccount
import rw.itunda.core.ledger.domain.model.LedgerTransaction

/**
 * Repository interface for interacting with the core ledger system.
 */
interface LedgerRepository {
    
    /**
     * Gets a real-time stream of the user's primary accounts.
     */
    fun getAccounts(customerId: String): Flow<List<LedgerAccount>>
    
    /**
     * Gets the transaction history for a specific account.
     */
    suspend fun getTransactionHistory(accountId: String, limit: Int = 50, offset: Int = 0): Result<List<LedgerTransaction>>
    
    /**
     * Verifies if a transfer intent is possible based on available balances and risk limits.
     * In Toss, this is done before showing the final confirmation screen.
     */
    suspend fun verifyTransferIntent(
        sourceAccountId: String,
        amount: java.math.BigDecimal,
        currency: String
    ): Result<Boolean>
}
