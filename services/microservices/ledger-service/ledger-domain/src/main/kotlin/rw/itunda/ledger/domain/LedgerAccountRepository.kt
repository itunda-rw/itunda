package rw.itunda.ledger.domain

interface LedgerAccountRepository {
    fun findById(accountId: String): LedgerAccount?
    /**
     * Loads an account under a database write lock. Implementations must require an
     * active transaction so a money-moving caller cannot accidentally release the
     * lock before persisting the resulting balances.
     */
    fun findByIdForUpdate(accountId: String): LedgerAccount?
    fun save(account: LedgerAccount): LedgerAccount
    fun saveAll(accounts: List<LedgerAccount>): List<LedgerAccount>
}
