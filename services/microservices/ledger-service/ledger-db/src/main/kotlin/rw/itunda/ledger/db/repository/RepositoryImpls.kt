package rw.itunda.ledger.db.repository

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import rw.itunda.ledger.db.entity.LedgerAccountEntity
import rw.itunda.ledger.db.entity.LedgerIdempotencyEntity
import rw.itunda.ledger.db.entity.OutboxEventEntity
import rw.itunda.ledger.domain.AccountStatus
import rw.itunda.ledger.domain.AccountType
import rw.itunda.ledger.domain.LedgerAccount
import rw.itunda.ledger.domain.LedgerAccountRepository
import rw.itunda.ledger.domain.LedgerIdempotencyRecord
import rw.itunda.ledger.domain.LedgerIdempotencyRepository
import rw.itunda.ledger.domain.OutboxEvent
import rw.itunda.ledger.domain.OutboxEventRepository

@Repository
class LedgerAccountRepositoryImpl(
    private val jpaRepository: LedgerAccountJpaRepository
) : LedgerAccountRepository {

    override fun findById(accountId: String): LedgerAccount? =
        jpaRepository.findByIdOrNull(accountId)?.toDomain()

    override fun findByIdForUpdate(accountId: String): LedgerAccount? =
        jpaRepository.findByIdForUpdate(accountId)?.toDomain()

    override fun save(account: LedgerAccount): LedgerAccount {
        jpaRepository.save(account.toEntity())
        return account
    }

    override fun saveAll(accounts: List<LedgerAccount>): List<LedgerAccount> {
        jpaRepository.saveAll(accounts.map { it.toEntity() })
        return accounts
    }

    private fun LedgerAccountEntity.toDomain() = LedgerAccount(
        accountId = accountId,
        customerId = customerId,
        currency = currency,
        balance = balance,
        accountType = AccountType.valueOf(accountType),
        status = AccountStatus.valueOf(status)
    )

    private fun LedgerAccount.toEntity() = LedgerAccountEntity(
        accountId = accountId,
        customerId = customerId,
        currency = currency,
        balance = balance,
        accountType = accountType.name,
        status = status.name
    )
}

@Repository
class OutboxEventRepositoryImpl(
    private val jpaRepository: OutboxEventJpaRepository
) : OutboxEventRepository {

    override fun save(event: OutboxEvent): OutboxEvent {
        jpaRepository.save(
            OutboxEventEntity(
                eventId = event.eventId,
                aggregateType = event.aggregateType,
                aggregateId = event.aggregateId,
                payload = event.payload,
                createdAt = event.createdAt
            )
        )
        return event
    }
}

@Repository
class LedgerIdempotencyRepositoryImpl(
    private val jpaRepository: LedgerIdempotencyJpaRepository
) : LedgerIdempotencyRepository {

    override fun findByKey(recordKey: String): LedgerIdempotencyRecord? =
        jpaRepository.findByIdOrNull(recordKey)?.toDomain()

    override fun insertIfAbsent(record: LedgerIdempotencyRecord): Boolean =
        jpaRepository.insertIfAbsent(
            recordKey = record.recordKey,
            requestJson = record.requestJson,
            statusCode = record.statusCode,
            createdAt = record.createdAt
        ) > 0

    override fun complete(recordKey: String, statusCode: Int, transactionId: String): Int =
        jpaRepository.complete(recordKey, statusCode, transactionId)

    override fun deleteByKey(recordKey: String) {
        jpaRepository.deleteById(recordKey)
        // Ensure an expired key is physically removed before INSERT IGNORE tries to
        // reclaim the same primary key in this transaction.
        jpaRepository.flush()
    }

    private fun LedgerIdempotencyEntity.toDomain() = LedgerIdempotencyRecord(
        recordKey = recordKey,
        requestJson = requestJson,
        statusCode = statusCode,
        transactionId = transactionId,
        createdAt = createdAt
    )
}
