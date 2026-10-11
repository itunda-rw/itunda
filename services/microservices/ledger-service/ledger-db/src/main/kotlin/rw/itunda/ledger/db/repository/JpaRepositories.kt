package rw.itunda.ledger.db.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.ledger.db.entity.LedgerAccountEntity
import rw.itunda.ledger.db.entity.LedgerIdempotencyEntity
import rw.itunda.ledger.db.entity.OutboxEventEntity
import java.time.Instant

interface LedgerAccountJpaRepository : JpaRepository<LedgerAccountEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from LedgerAccountEntity account where account.accountId = :accountId")
    fun findByIdForUpdate(@Param("accountId") accountId: String): LedgerAccountEntity?
}

interface OutboxEventJpaRepository : JpaRepository<OutboxEventEntity, String>

interface LedgerIdempotencyJpaRepository : JpaRepository<LedgerIdempotencyEntity, String> {
    @Modifying
    @Query(
        value = """
            INSERT IGNORE INTO ledger_idempotency_records
                (record_key, request_json, status_code, transaction_id, created_at)
            VALUES (:recordKey, :requestJson, :statusCode, NULL, :createdAt)
        """,
        nativeQuery = true
    )
    fun insertIfAbsent(
        @Param("recordKey") recordKey: String,
        @Param("requestJson") requestJson: String,
        @Param("statusCode") statusCode: Int,
        @Param("createdAt") createdAt: Instant
    ): Int

    @Modifying
    @Query(
        "update LedgerIdempotencyEntity record set record.statusCode = :statusCode, " +
            "record.transactionId = :transactionId where record.recordKey = :recordKey and record.statusCode = -1"
    )
    fun complete(
        @Param("recordKey") recordKey: String,
        @Param("statusCode") statusCode: Int,
        @Param("transactionId") transactionId: String
    ): Int

    @Modifying
    @Query("delete from LedgerIdempotencyEntity record where record.createdAt < :expiredBefore")
    fun deleteExpiredBefore(@Param("expiredBefore") expiredBefore: Instant): Int
}
