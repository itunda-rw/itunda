package rw.itunda.ledger.domain

import java.time.LocalDateTime

/**
 * Pure domain model for Outbox Events.
 * Toss Rule: Reliable messaging via Transactional Outbox.
 */
data class OutboxEvent(
    val eventId: String,
    val aggregateType: String,
    val aggregateId: String,
    val payload: String, // JSON payload
    val createdAt: LocalDateTime = LocalDateTime.now()
)

interface OutboxEventRepository {
    fun save(event: OutboxEvent): OutboxEvent
}
