package rw.itunda.ledger.domain

interface LedgerAccountRepository {
    fun findById(accountId: String): LedgerAccount?
    fun save(account: LedgerAccount): LedgerAccount
    fun saveAll(accounts: List<LedgerAccount>): List<LedgerAccount>
}
