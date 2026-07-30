package rw.itunda.core.events

import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Polls `outbox_events` for unprocessed rows and relays them to Kafka -- the second
 * half of the transactional outbox pattern [EventPublisher] writes the first half of.
 * Each poll is its own transaction, and the repository locks the selected rows, so
 * concurrent application replicas cannot relay the same pending event simultaneously.
 * Kafka publish failures are logged and left unprocessed for the next poll to retry --
 * real at-least-once delivery, unlike this backend's previous "log and drop on
 * failure" behavior.
 *
 * Rows are kept (marked `processedAt`), not deleted, as a real audit trail of every
 * event actually published -- consistent with this backend's append-only ledger
 * discipline elsewhere. No retention/cleanup job exists yet to prune old processed
 * rows -- a real, known follow-up, not silently ignored.
 */
@Component
class OutboxRelay(
    private val outboxEventRepository: OutboxEventRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
) {
    private val log = LoggerFactory.getLogger(OutboxRelay::class.java)

    @Scheduled(fixedDelay = 2000)
    @Transactional
    fun relay() {
        val pending = outboxEventRepository.findTop100ByProcessedAtIsNullOrderByCreatedAtAsc()
        for (event in pending) {
            try {
                kafkaTemplate.send(event.topic, event.key, event.payload).get()
                event.processedAt = Instant.now()
                outboxEventRepository.save(event)
            } catch (e: Exception) {
                log.warn("Failed to relay outbox event {} (topic={}): {} -- will retry next poll", event.id, event.topic, e.message)
            }
        }
    }
}
