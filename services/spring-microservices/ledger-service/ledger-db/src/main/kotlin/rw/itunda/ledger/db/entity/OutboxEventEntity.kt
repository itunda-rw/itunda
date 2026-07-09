package rw.itunda.ledger.db.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "outbox_events")
class OutboxEventEntity(
    @Id
    @Column(name = "event_id", length = 36, nullable = false)
    val eventId: String,

    @Column(name = "aggregate_type", length = 50, nullable = false)
    val aggregateType: String,

    @Column(name = "aggregate_id", length = 36, nullable = false)
    val aggregateId: String,

    @Column(name = "payload", columnDefinition = "JSON", nullable = false)
    val payload: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime
)
