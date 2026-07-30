package rw.itunda.core.events

import io.kotest.core.spec.style.BehaviorSpec
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
