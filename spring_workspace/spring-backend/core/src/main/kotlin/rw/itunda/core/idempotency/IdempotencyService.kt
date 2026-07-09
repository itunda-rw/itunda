package rw.itunda.core.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

class IdempotencyConflictException(message: String) : RuntimeException(message)
class IdempotencyInProgressException(message: String) : RuntimeException(message)
data class IdempotentReplay(val statusCode: Int, val body: Map<String, Any?>)

private const val PROCESSING_STATUS = -1

/**
 * Mirrors Toss Payments' own public idempotency-key contract
 * (docs.tosspayments.com/reference/using-api/idempotency-key): a key is scoped per API
 * route (method + path), not global — reusing the same Idempotency-Key value on two
 * different endpoints must not collide or replay across them. `recordKey` encodes both.
 */
@Entity
@Table(name = "idempotency_records")
class IdempotencyRecordEntity(
    @Id
    @Column(name = "record_key", length = 300)
    val recordKey: String,

    @Column(name = "body_json", nullable = false, columnDefinition = "TEXT")
    val bodyJson: String,

    // TINYTEXT (255 bytes) is Hibernate's default for a bare @Lob String on MySQL,
    // which silently truncated real request/response JSON — explicit columnDefinition
    // forces a real TEXT column (64KB) instead.
    @Column(name = "status_code", nullable = false)
    var statusCode: Int,

    @Column(name = "response_json", nullable = false, columnDefinition = "TEXT")
    var responseJson: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(recordKey = "", bodyJson = "", statusCode = 0, responseJson = "")
}

interface IdempotencyRecordRepository : JpaRepository<IdempotencyRecordEntity, String> {
    /**
     * `INSERT IGNORE` instead of `save`/`saveAndFlush`: a plain JPA insert that hits a
     * duplicate primary key throws a `DataIntegrityViolationException` from the flush,
     * and Hibernate marks that persistence context/transaction rollback-only *before*
     * application code ever gets to catch anything — catching the exception doesn't
     * undo that, so the surrounding REQUIRES_NEW transaction still fails to commit with
     * an uncaught `UnexpectedRollbackException`. That uncaught exception was escaping
     * this service entirely, past Spring MVC's error handling, forcing an internal
     * `/error` forward — which isn't on the security allowlist, so it came back as a
     * bare 401 instead of the intended 409. `INSERT IGNORE` never throws on a duplicate
     * key; MySQL just reports 0 affected rows, which is a normal return value we can
     * branch on instead of an exception we can't safely recover from mid-transaction.
     */
    @Modifying
    @Query(
        value = """
            INSERT IGNORE INTO idempotency_records (record_key, body_json, status_code, response_json, created_at)
            VALUES (:recordKey, :bodyJson, :statusCode, :responseJson, :createdAt)
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("recordKey") recordKey: String,
        @Param("bodyJson") bodyJson: String,
        @Param("statusCode") statusCode: Int,
        @Param("responseJson") responseJson: String,
        @Param("createdAt") createdAt: Instant,
    ): Int
}

sealed class ClaimOutcome {
    data object Claimed : ClaimOutcome()
    data object InProgress : ClaimOutcome()
    data object Conflict : ClaimOutcome()
    data class Replay(val replay: IdempotentReplay) : ClaimOutcome()
}

/**
 * Separate bean (not just separate methods on IdempotencyService) so `@Transactional`
 * REQUIRES_NEW actually takes effect: Spring's transaction advice is a proxy around
 * the bean, and a method calling another method on `this` bypasses that proxy entirely
 * (a well-known Spring AOP self-invocation pitfall) — REQUIRES_NEW would silently do
 * nothing if `claim`/`release` lived on IdempotencyService and were called from its own
 * `replayOrExecute`.
 */
@Service
class IdempotencyClaimStore(
    private val repository: IdempotencyRecordRepository,
    private val objectMapper: ObjectMapper,
) {
    private val ttl: Duration = Duration.ofHours(24)

    /**
     * Runs in its own transaction and commits immediately, so the claim is visible to a
     * concurrent racer as soon as it lands rather than staying pending inside the
     * caller's still-open outer transaction. The actual claim uses `insertIfAbsent`
     * (`INSERT IGNORE`) rather than `save`, specifically so a lost race reports back as
     * a return value (0 rows) instead of a thrown exception — see that method's doc for
     * why a caught exception here still isn't safe to just swallow and continue past.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun claim(recordKey: String, bodyJson: String): ClaimOutcome {
        val existing = repository.findById(recordKey).orElse(null)
        if (existing != null) {
            if (Duration.between(existing.createdAt, Instant.now()) > ttl) {
                repository.deleteById(recordKey)
            } else if (existing.statusCode == PROCESSING_STATUS) {
                // Same key, still being handled by another in-flight request — Toss
                // Payments returns 409 IDEMPOTENT_REQUEST_PROCESSING for exactly this.
                return ClaimOutcome.InProgress
            } else if (existing.bodyJson != bodyJson) {
                return ClaimOutcome.Conflict
            } else {
                @Suppress("UNCHECKED_CAST")
                val responseBody = objectMapper.readValue(existing.responseJson, Map::class.java) as Map<String, Any?>
                return ClaimOutcome.Replay(IdempotentReplay(existing.statusCode, responseBody))
            }
        }
        val inserted = repository.insertIfAbsent(recordKey, bodyJson, PROCESSING_STATUS, "{}", Instant.now())
        return if (inserted > 0) ClaimOutcome.Claimed else ClaimOutcome.InProgress
    }

    /** A failed business action must free the key so a genuine retry isn't stuck behind
     * a permanent "processing" marker — matching the Express backend's behavior, where
     * a thrown error skips storeIdempotentResult entirely and the key stays retryable. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun release(recordKey: String) {
        repository.deleteById(recordKey)
    }
}

@Service
class IdempotencyService(
    private val claimStore: IdempotencyClaimStore,
    private val repository: IdempotencyRecordRepository,
    private val objectMapper: ObjectMapper,
) {
    private fun scopedKey(endpoint: String, key: String) = "$endpoint::$key"
    private fun canonicalJson(body: Any?) = objectMapper.writeValueAsString(body ?: emptyMap<String, Any?>())

    /**
     * Shared shape for every money-moving controller. `endpoint` (e.g.
     * `"POST /api/v1/wallet/transfer/confirm"`) scopes the key per route, and the claim
     * step closes a real race: two simultaneous requests with the same key used to both
     * find no completed record and both run the business action concurrently.
     *
     * The finalize step runs in the *ambient* transaction (not REQUIRES_NEW) so the
     * business write and the idempotency write still commit or roll back together —
     * the atomicity fix from the previous pass, preserved: a failure finalizing the
     * idempotency record rolls back the business write too, which then triggers
     * `release` so the key isn't left stuck mid-flight.
     */
    @Transactional
    fun replayOrExecute(endpoint: String, key: String, body: Any?, action: () -> Pair<Int, Map<String, Any?>>): Pair<Int, Map<String, Any?>> {
        val recordKey = scopedKey(endpoint, key)
        val bodyJson = canonicalJson(body)

        when (val outcome = claimStore.claim(recordKey, bodyJson)) {
            is ClaimOutcome.Replay -> return outcome.replay.statusCode to outcome.replay.body
            ClaimOutcome.InProgress -> throw IdempotencyInProgressException("A request with this Idempotency-Key is already being processed")
            ClaimOutcome.Conflict -> throw IdempotencyConflictException("Idempotency-Key was already used with a different request body")
            ClaimOutcome.Claimed -> {
                val (status, response) = try {
                    action()
                } catch (e: Exception) {
                    claimStore.release(recordKey)
                    throw e
                }
                val record = repository.findById(recordKey).orElseThrow()
                record.statusCode = status
                record.responseJson = objectMapper.writeValueAsString(response)
                repository.save(record)
                return status to response
            }
        }
    }
}
