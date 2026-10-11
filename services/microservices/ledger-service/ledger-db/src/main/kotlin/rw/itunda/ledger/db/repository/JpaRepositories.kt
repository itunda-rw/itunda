package rw.itunda.ledger.db.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.ledger.db.entity.LedgerAccountEntity
import rw.itunda.ledger.db.entity.OutboxEventEntity

interface LedgerAccountJpaRepository : JpaRepository<LedgerAccountEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from LedgerAccountEntity account where account.accountId = :accountId")
    fun findByIdForUpdate(@Param("accountId") accountId: String): LedgerAccountEntity?
}

interface OutboxEventJpaRepository : JpaRepository<OutboxEventEntity, String>
