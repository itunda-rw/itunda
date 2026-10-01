package rw.itunda.core.events

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Relays durable outbox rows to Kafka only when the Kafka integration is enabled.
 * Authentication/API traffic must remain independent from Kafka availability.
 */
@Component
@ConditionalOnProperty(prefix = "itunda.kafka", name = ["enabled"], havingValue = "true")
class OutboxRelay(
    private val outboxEventRepository: OutboxEventRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
) {
    private val log = LoggerFactory.getLogger(OutboxRelay::class.java)

    companion object {
        private val relayBudget = Duration.ofSeconds(8)
        private val perSendTimeout = 2L to TimeUnit.SECONDS
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun relay() {
        val pending = outboxEventRepository.findTop100ByProcessedAtIsNullOrderByCreatedAtAsc()
        val deadline = Instant.now().plus(relayBudget)
        for (event in pending) {
            if (Instant.now().isAfter(deadline)) {
                log.warn("Outbox relay budget exceeded with events still pending -- deferring the rest to the next poll")
                break
            }
            try {
                kafkaTemplate.send(event.topic, event.key, event.payload)
                    .get(perSendTimeout.first, perSendTimeout.second)
                event.processedAt = Instant.now()
                outboxEventRepository.save(event)
            } catch (e: Exception) {
                log.warn(
                    "Failed to relay outbox event {} (topic={}): {} -- will retry next poll",
                    event.id, event.topic, e.message
                )
            }
        }
    }
}
