package rw.itunda.payment.db.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

/**
 * Outbox Pattern Implementation:
 * Matches Toss's exact architecture for CDC (Change Data Capture) via Debezium.
 * This entity is persisted in the SAME transaction as the business logic (e.g., Ledger posting).
 * Debezium tails the MySQL binlog and reliably pushes these events to Kafka.
 */
@Entity
@Table(name = "outbox_events")
class OutboxEvent(
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "aggregate_type", nullable = false)
    val aggregateType: String,

    @Column(name = "aggregate_id", nullable = false)
    val aggregateId: String,

    @Column(name = "event_type", nullable = false)
    val eventType: String,

    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    val payload: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
