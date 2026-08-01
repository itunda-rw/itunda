package rw.itunda.core.events

import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

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
 *
 * Real bug found live (2026-08-01) while live-verifying new-feature money flows on a
 * local backend with no Kafka running: `kafkaTemplate.send(...).get()` had no timeout,
 * so a real Kafka outage left this `@Transactional` method blocked inside the Kafka
 * client's own internal retry/timeout window (up to `delivery.timeout.ms`, ~2 minutes
 * by default) -- the WHOLE time holding row locks on `outbox_events` (and, via the
 * same transaction, whatever else `EventPublisher`'s callers touch). With this method
 * firing every 2 seconds (`fixedDelay`), each new poll piled another long-blocked
 * transaction on top, and any unrelated write that also needed to insert into
 * `outbox_events` (i.e. any ledger-posting transaction anywhere in this backend) real-
 * failed with `Lock wait timeout exceeded`.
 *
 * A per-send timeout alone isn't enough: with up to 100 rows fetched per poll, even a
 * short per-row bound multiplies into a long total hold during a real, sustained
 * outage (confirmed live: 33 real pending rows queued from this exact incident still
 * blew past MySQL's lock-wait-timeout at 5s/row). `relayBudget` bounds the WHOLE
 * batch's wall-clock time instead, so this method's transaction -- and the row locks
 * it holds -- release quickly regardless of how many rows are pending; whatever this
 * poll doesn't get to is picked up by the next one 2 seconds later, unchanged from the
 * existing at-least-once semantics.
 */
@Component
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
    @Transactional
    fun relay() {
        val pending = outboxEventRepository.findTop100ByProcessedAtIsNullOrderByCreatedAtAsc()
        val deadline = Instant.now().plus(relayBudget)
        for (event in pending) {
            if (Instant.now().isAfter(deadline)) {
                log.warn("Outbox relay budget exceeded with events still pending -- deferring the rest to the next poll")
                break
            }
            try {
                kafkaTemplate.send(event.topic, event.key, event.payload).get(perSendTimeout.first, perSendTimeout.second)
                event.processedAt = Instant.now()
                outboxEventRepository.save(event)
            } catch (e: Exception) {
                log.warn("Failed to relay outbox event {} (topic={}): {} -- will retry next poll", event.id, event.topic, e.message)
            }
        }
    }
}
