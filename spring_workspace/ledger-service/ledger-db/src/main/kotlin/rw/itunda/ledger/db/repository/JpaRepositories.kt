package rw.itunda.ledger.db.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.ledger.db.entity.LedgerAccountEntity
import rw.itunda.ledger.db.entity.OutboxEventEntity

interface LedgerAccountJpaRepository : JpaRepository<LedgerAccountEntity, String>
interface OutboxEventJpaRepository : JpaRepository<OutboxEventEntity, String>
