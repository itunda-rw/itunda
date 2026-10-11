package rw.itunda.ledger.domain

import java.time.Instant

data class LedgerIdempotencyRecord(
    val recordKey: String,
    val requestJson: String,
    val statusCode: Int,
    val transactionId: String?,
    val createdAt: Instant
}

/**
 * Durable idempotency state for money-moving API requests.
 * Claim creation is committed separately; complete() must participate in the
 * same database transaction as the ledger balance and outbox writes.
 */
interface LedgerIdempotencyRepository {
    fun findByKey(recordKey: String): LedgerIdempotencyRecord?
    fun insertIfAbsent(record: LedgerIdempotencyRecord): Boolean
    fun complete(recordKey: String, statusCode: Int, transactionId: String): Int
    fun deleteByKey(recordKey: String)
}
