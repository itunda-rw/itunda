package rw.itunda.core.events

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Publishes domain events to Kafka only after the enclosing database transaction has
 * actually committed -- publishing mid-transaction would risk emitting an event for a
 * row that never persisted if the transaction later rolled back.
 *
 * This is a lighter pattern than a true transactional outbox (which
 * services/microservices/ledger-service uses for real, via an OutboxEventEntity +
 * Debezium CDC) -- honestly labeled as such: if Kafka is unreachable at commit time,
 * the event is dropped rather than retried, unlike a real outbox's at-least-once
 * guarantee. Ledger correctness never depends on this succeeding -- publish failures
 * are logged, not thrown, so a Kafka outage can never block or roll back a real money
 * movement. Closes the gap docs/ARCHITECTURE.md's backlog names: "the event model
 * [...] is designed but not emitted anywhere yet."
 */
@Component
class EventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(EventPublisher::class.java)

    fun publishAfterCommit(topic: String, key: String, payload: Any) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // No active transaction (e.g. called outside a @Transactional method) --
            // publish immediately rather than silently dropping the event.
            publishNow(topic, key, payload)
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = publishNow(topic, key, payload)
        })
    }

    /**
     * For events that report a fact about something *other than* the enclosing
     * transaction's own writes -- e.g. an external provider declining a payment
     * before any ledger row is touched. [publishAfterCommit]'s afterCommit hook only
     * fires on commit, never on rollback, so it would silently drop an event for a
     * caller that (correctly) rolls back its transaction after catching the same
     * failure this event is reporting. Use this instead in that situation.
     */
    fun publishImmediately(topic: String, key: String, payload: Any) = publishNow(topic, key, payload)

    private fun publishNow(topic: String, key: String, payload: Any) {
        try {
            kafkaTemplate.send(topic, key, objectMapper.writeValueAsString(payload))
        } catch (e: Exception) {
            log.warn("Failed to publish event to topic {} (key={}): {}", topic, key, e.message)
        }
    }
}
