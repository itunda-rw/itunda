package rw.itunda.ledger.db.repository

import org.springframework.stereotype.Repository
import rw.itunda.ledger.domain.LedgerAccount
import rw.itunda.ledger.domain.LedgerAccountRepository
import rw.itunda.ledger.domain.OutboxEvent
import rw.itunda.ledger.domain.OutboxEventRepository
import rw.itunda.ledger.db.entity.LedgerAccountEntity
import rw.itunda.ledger.db.entity.OutboxEventEntity
import rw.itunda.ledger.domain.AccountType
import rw.itunda.ledger.domain.AccountStatus
import org.springframework.data.repository.findByIdOrNull

@Repository
class LedgerAccountRepositoryImpl(
    private val jpaRepository: LedgerAccountJpaRepository
) : LedgerAccountRepository {

    override fun findById(accountId: String): LedgerAccount? {
        val entity = jpaRepository.findByIdOrNull(accountId) ?: return null
        return LedgerAccount(
            accountId = entity.accountId,
            customerId = entity.customerId,
            currency = entity.currency,
            balance = entity.balance,
            accountType = AccountType.valueOf(entity.accountType),
            status = AccountStatus.valueOf(entity.status)
        )
    }

    override fun save(account: LedgerAccount): LedgerAccount {
        val entity = LedgerAccountEntity(
            accountId = account.accountId,
            customerId = account.customerId,
            currency = account.currency,
            balance = account.balance,
            accountType = account.accountType.name,
            status = account.status.name
        )
        // Note: Real implementation would handle JPA versioning (optimistic lock)
        jpaRepository.save(entity)
        return account
    }

    override fun saveAll(accounts: List<LedgerAccount>): List<LedgerAccount> {
        val entities = accounts.map {
            LedgerAccountEntity(
                accountId = it.accountId,
                customerId = it.customerId,
                currency = it.currency,
                balance = it.balance,
                accountType = it.accountType.name,
                status = it.status.name
            )
        }
        jpaRepository.saveAll(entities)
        return accounts
    }
}

@Repository
class OutboxEventRepositoryImpl(
    private val jpaRepository: OutboxEventJpaRepository
) : OutboxEventRepository {

    override fun save(event: OutboxEvent): OutboxEvent {
        val entity = OutboxEventEntity(
            eventId = event.eventId,
            aggregateType = event.aggregateType,
            aggregateId = event.aggregateId,
            payload = event.payload,
            createdAt = event.createdAt
        )
        jpaRepository.save(entity)
        return event
    }
}
