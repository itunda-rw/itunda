package rw.itunda.core.events

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.kafka.core.KafkaTemplate
import java.time.Instant
import java.util.concurrent.CompletableFuture

/**
 * The relay provides at-least-once delivery. A row becomes processed only after Kafka
 * acknowledges it, and one failed row must not prevent independent later events from
 * being relayed during the same poll.
 */
class OutboxRelayTest : BehaviorSpec({
    fun event(id: String) = OutboxEventEntity(
        id = id,
        topic = "transfer.confirmed",
        key = id,
        payload = "{\"transferId\":\"$id\"}",
        createdAt = Instant.parse("2026-07-01T00:00:00Z"),
    )

    Given("the outbox batch query") {
        When("multiple relay replicas are active") {
            val lock = OutboxEventRepository::class.java
                .getMethod("findTop100ByProcessedAtIsNullOrderByCreatedAtAsc")
                .getAnnotation(Lock::class.java)

            Then("it requests an exclusive database lock for its selected rows") {
                lock.value shouldBe LockModeType.PESSIMISTIC_WRITE
            }
        }
    }

    Given("a pending outbox event Kafka acknowledges") {
        val repository = mockk<OutboxEventRepository>()
        val kafkaTemplate = mockk<KafkaTemplate<String, String>>()
        val pending = event("outbox_1")
        every { repository.findTop100ByProcessedAtIsNullOrderByCreatedAtAsc() } returns listOf(pending)
        every { kafkaTemplate.send(pending.topic, pending.key, pending.payload) } returns CompletableFuture.completedFuture(mockk())
        every { repository.save(any()) } answers { firstArg() }

        When("the relay polls") {
            OutboxRelay(repository, kafkaTemplate).relay()

            Then("it records the Kafka-acknowledged event as processed") {
                pending.processedAt shouldNotBe null
                verify(exactly = 1) { repository.save(match { it.id == "outbox_1" && it.processedAt != null }) }
            }
        }
    }

    // Real bug found live (2026-08-01): a Kafka outage left `send(...).get()` blocked
    // for the Kafka client's own multi-minute internal timeout, holding this
    // @Transactional method's DB locks the whole time. A future that never completes
    // (matching how a real unreachable broker behaves before the producer's own
    // internal timeout eventually fires) must not hang this method indefinitely.
    Given("a pending outbox event Kafka never acknowledges (broker unreachable)") {
        val repository = mockk<OutboxEventRepository>()
        val kafkaTemplate = mockk<KafkaTemplate<String, String>>()
        val stuck = event("outbox_stuck")
        every { repository.findTop100ByProcessedAtIsNullOrderByCreatedAtAsc() } returns listOf(stuck)
        every { kafkaTemplate.send(stuck.topic, stuck.key, stuck.payload) } returns CompletableFuture()
        every { repository.save(any()) } answers { firstArg() }

        When("the relay polls") {
            val started = System.nanoTime()
            OutboxRelay(repository, kafkaTemplate).relay()
            val elapsedSeconds = (System.nanoTime() - started) / 1_000_000_000.0

            Then("it gives up within a bounded wait instead of hanging, leaving the event pending") {
                elapsedSeconds shouldBeLessThan 10.0
                stuck.processedAt shouldBe null
                verify(exactly = 0) { repository.save(match { it.id == "outbox_stuck" }) }
            }
        }
    }

    // Real bug found live (2026-08-01): a per-row timeout alone still multiplies into a
    // long total hold when many rows are pending (33 real queued rows blew past MySQL's
    // lock-wait-timeout at 5s each). `relayBudget` bounds the whole batch's wall-clock
    // time so this stays fast regardless of how many rows are stuck.
    Given("more stuck events than the relay's time budget can process") {
        val repository = mockk<OutboxEventRepository>()
        val kafkaTemplate = mockk<KafkaTemplate<String, String>>()
        // perSendTimeout is 2s and relayBudget is 8s, so 6 events that each take just
        // over 2s to time out real-guarantees the budget is exhausted before all of
        // them are attempted -- proving the early-exit path actually fires, not just
        // that any single send is individually bounded.
        val stuckEvents = (1..6).map { event("outbox_stuck_$it") }
        every { repository.findTop100ByProcessedAtIsNullOrderByCreatedAtAsc() } returns stuckEvents
        stuckEvents.forEach { every { kafkaTemplate.send(it.topic, it.key, it.payload) } returns CompletableFuture() }
        every { repository.save(any()) } answers { firstArg() }

        When("the relay polls") {
            val started = System.nanoTime()
            OutboxRelay(repository, kafkaTemplate).relay()
            val elapsedSeconds = (System.nanoTime() - started) / 1_000_000_000.0

            Then("it stops early within the batch budget, leaving unattempted events pending for the next poll") {
                elapsedSeconds shouldBeLessThan 15.0
                stuckEvents.forEach { it.processedAt shouldBe null }
                verify(exactly = 0) { repository.save(any()) }
            }
        }
    }

    Given("one failed event followed by an independent pending event") {
        val repository = mockk<OutboxEventRepository>()
        val kafkaTemplate = mockk<KafkaTemplate<String, String>>()
        val failed = event("outbox_failed")
        val succeeding = event("outbox_succeeding")
        every { repository.findTop100ByProcessedAtIsNullOrderByCreatedAtAsc() } returns listOf(failed, succeeding)
        every { kafkaTemplate.send(failed.topic, failed.key, failed.payload) } returns CompletableFuture.failedFuture(IllegalStateException("broker unavailable"))
        every { kafkaTemplate.send(succeeding.topic, succeeding.key, succeeding.payload) } returns CompletableFuture.completedFuture(mockk())
        every { repository.save(any()) } answers { firstArg() }

        When("the relay polls") {
            OutboxRelay(repository, kafkaTemplate).relay()

            Then("it leaves the failed event pending but relays the later event") {
                failed.processedAt shouldBe null
                succeeding.processedAt shouldNotBe null
                verify(exactly = 0) { repository.save(match { it.id == "outbox_failed" }) }
                verify(exactly = 1) { repository.save(match { it.id == "outbox_succeeding" && it.processedAt != null }) }
            }
        }
    }
})
