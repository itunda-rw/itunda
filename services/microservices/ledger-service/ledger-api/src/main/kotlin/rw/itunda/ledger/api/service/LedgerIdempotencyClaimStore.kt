package rw.itunda.ledger.api.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import rw.itunda.ledger.domain.LedgerIdempotencyRecord
import rw.itunda.ledger.domain.LedgerIdempotencyRepository
import java.time.Duration
import java.time.Instant

private const val PROCESSING_STATUS = -1
private val IDEMPOTENCY_RETENTION: Duration = Duration.ofDays(15)

sealed interface LedgerIdempotencyClaim {
    data object Claimed : LedgerIdempotencyClaim
    data object InProgress : LedgerIdempotencyClaim
    data object Conflict : LedgerIdempotencyClaim
    data class Replay(val transactionId: String) : LedgerIdempotencyClaim
}

/**
 * Claims are committed in a separate transaction so concurrent requests can see
 * the in-progress marker. The successful result is written by LedgerTransferExecutor
 * in the SAME transaction as account balances and the transactional outbox.
 */
@Service
class LedgerIdempotencyClaimStore(
    private val repository: LedgerIdempotencyRepository
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun claim(recordKey: String, requestJson: String): LedgerIdempotencyClaim {
        val existing = repository.findByKey(recordKey)
        if (existing != null) {
            val resolved = resolveExisting(recordKey, requestJson, existing)
            if (resolved != null) return resolved
        }

        val inserted = repository.insertIfAbsent(
            LedgerIdempotencyRecord(
                recordKey = recordKey,
                requestJson = requestJson,
                statusCode = PROCESSING_STATUS,
                transactionId = null,
                createdAt = Instant.now()
            )
        )
        if (inserted) return LedgerIdempotencyClaim.Claimed

        // A concurrent request may have inserted the key after our first read.
        // Re-read the winner so a different payload gets a conflict, and a
        // completed request gets replayed rather than a misleading processing result.
        val winner = repository.findByKey(recordKey) ?: return LedgerIdempotencyClaim.InProgress
        return resolveExisting(recordKey, requestJson, winner)
            ?: LedgerIdempotencyClaim.InProgress
    }

    /**
     * Returns null only when an expired completed record was removed and a new
     * claim may be attempted. Unresolved claims never expire automatically:
     * a prior commit may have succeeded even if the caller lost its response.
     */
    private fun resolveExisting(
        recordKey: String,
        requestJson: String,
        existing: LedgerIdempotencyRecord
    ): LedgerIdempotencyClaim? {
        if (existing.requestJson != requestJson) return LedgerIdempotencyClaim.Conflict
        if (existing.statusCode == PROCESSING_STATUS) return LedgerIdempotencyClaim.InProgress

        if (Duration.between(existing.createdAt, Instant.now()) > IDEMPOTENCY_RETENTION) {
            repository.deleteByKey(recordKey)
            return null
        }

        return existing.transactionId
            ?.let(LedgerIdempotencyClaim::Replay)
            ?: LedgerIdempotencyClaim.InProgress
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun release(recordKey: String) {
        repository.deleteByKey(recordKey)
    }
}
