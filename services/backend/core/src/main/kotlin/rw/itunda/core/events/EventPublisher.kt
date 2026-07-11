package rw.itunda.core.events

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Publishes domain events via a real transactional outbox (2026-07-11) -- upgraded
 * from an earlier, honestly-lighter pattern (Kafka published directly from an
 * `afterCommit` transaction-synchronization callback: correct about *timing*, but if
 * Kafka was unreachable at that moment the event was logged and dropped, not
 * retried).
 *
 * `services/microservices/ledger-service` already had its own `OutboxEvent`/
 * `OutboxEventEntity` (separate table, untouched by this) — but on inspection it's
 * also incomplete: it writes outbox rows durably (the correct half of the pattern)
 * but nothing in that codebase ever reads and relays them to Kafka, and no
 * Debezium/CDC connector config exists anywhere in this repo despite that being
 * named as the intended relay mechanism. This implementation completes both halves:
 * this class writes the row, [OutboxRelay] polls and publishes it — real
 * at-least-once delivery, not just durable-but-unrelayed storage.
 */
@Component
class EventPublisher(
    private val outboxEventRepository: OutboxEventRepository,
    private val objectMapper: ObjectMapper,
) {
    /**
     * Writes the outbox row as part of whatever transaction the caller is already
     * in (Spring's default `REQUIRED` propagation) — it commits or rolls back
     * atomically with the caller's own domain write, so this row only ever exists
     * durably if that write actually committed. That's the exact safety property
     * the old `afterCommit`-callback approach was approximating with an in-memory
     * hook; this gets it for real, backed by the database, and if there's no active
     * transaction at all (e.g. called outside a `@Transactional` method), Spring
     * Data's own per-method transaction on `save()` still commits the row on its
     * own — no special-case branch needed either way.
     */
    fun publishAfterCommit(topic: String, key: String, payload: Any) = writeOutboxRow(topic, key, payload)

    /**
     * For events that report a fact about something *other than* the enclosing
     * transaction's own writes -- e.g. an external provider declining a payment
     * before any ledger row is touched. `REQUIRES_NEW` so this row commits in its
     * own, independent transaction and survives the caller's rollback (the caller
     * is about to roll back after catching the same failure this event reports;
     * without `REQUIRES_NEW` this row would roll back right along with it).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun publishImmediately(topic: String, key: String, payload: Any) = writeOutboxRow(topic, key, payload)

    private fun writeOutboxRow(topic: String, key: String, payload: Any) {
        outboxEventRepository.save(
            OutboxEventEntity(
                id = "outbox_${UUID.randomUUID()}",
                topic = topic,
                key = key,
                payload = objectMapper.writeValueAsString(payload),
                createdAt = Instant.now(),
            )
        )
    }
}
