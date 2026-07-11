package rw.itunda.core.events

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

/**
 * Real transactional outbox row (2026-07-11). Closes the honest gap
 * [EventPublisher]'s own earlier doc comment named: "This is a lighter pattern than
 * a true transactional outbox... if Kafka is unreachable at commit time, the event
 * is dropped rather than retried." `services/microservices/ledger-service` already
 * had an `OutboxEvent`/`OutboxEventEntity` (real, its own separate table, this
 * doesn't touch or depend on it) -- but on inspection it's also incomplete: it
 * writes outbox rows durably (the correct half of the pattern) but nothing in that
 * codebase ever reads and relays them to Kafka, no Debezium/CDC connector config
 * exists anywhere in this repo despite that being named as the intended mechanism.
 * This implementation completes both halves: [EventPublisher] writes rows here
 * (durable, part of the same transaction as the domain write), and [OutboxRelay]
 * polls and publishes them.
 */
@Entity
@Table(name = "outbox_events")
class OutboxEventEntity(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(nullable = false, length = 100)
    val topic: String,

    @Column(name = "message_key", nullable = false, length = 100)
    val key: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val payload: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(name = "processed_at")
    var processedAt: Instant? = null,
) {
    protected constructor() : this(id = "", topic = "", key = "", payload = "", createdAt = Instant.now())
}

interface OutboxEventRepository : JpaRepository<OutboxEventEntity, String> {
    fun findTop100ByProcessedAtIsNullOrderByCreatedAtAsc(): List<OutboxEventEntity>
}
