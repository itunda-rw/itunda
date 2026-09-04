package rw.itunda.core.events

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

/**
 * First direct test coverage for EventPublisher -- the write half of the real
 * transactional outbox pattern (see this class's own doc comment; OutboxRelay, the
 * read/relay half, already has OutboxRelayTest.kt). A real ObjectMapper is used
 * (pure, deterministic, no reason to mock it) so the actual JSON this class writes to
 * the outbox table is verified, not assumed correct.
 */
class EventPublisherTest : BehaviorSpec({

    data class TestPayload(val orderId: String, val amount: Int)

    Given("a real event to publish") {
        val outboxEventRepository = mockk<OutboxEventRepository>()
        val savedSlot = slot<OutboxEventEntity>()
        every { outboxEventRepository.save(capture(savedSlot)) } answers { firstArg() }
        val publisher = EventPublisher(outboxEventRepository, ObjectMapper())

        When("publishAfterCommit is called with a real data class payload") {
            publisher.publishAfterCommit("orders.placed", "order-1", TestPayload("order-1", 5000))

            Then("the outbox row is saved with the right topic/key, a real prefixed id, and the payload actually JSON-serialized") {
                verify(exactly = 1) { outboxEventRepository.save(any()) }
                savedSlot.captured.topic shouldBe "orders.placed"
                savedSlot.captured.key shouldBe "order-1"
                savedSlot.captured.id shouldStartWith "outbox_"
                savedSlot.captured.payload shouldBe """{"orderId":"order-1","amount":5000}"""
            }
        }
    }

    Given("a real event published via publishImmediately") {
        val outboxEventRepository = mockk<OutboxEventRepository>()
        val savedSlot = slot<OutboxEventEntity>()
        every { outboxEventRepository.save(capture(savedSlot)) } answers { firstArg() }
        val publisher = EventPublisher(outboxEventRepository, ObjectMapper())

        When("it's called with a real payload") {
            publisher.publishImmediately("payments.declined", "payment-1", TestPayload("payment-1", 1200))

            Then("it writes the exact same outbox row shape as publishAfterCommit -- only the transaction propagation differs") {
                savedSlot.captured.topic shouldBe "payments.declined"
                savedSlot.captured.key shouldBe "payment-1"
                savedSlot.captured.payload shouldBe """{"orderId":"payment-1","amount":1200}"""
            }
        }
    }

    Given("two events published back to back") {
        val outboxEventRepository = mockk<OutboxEventRepository>()
        val savedSlot = slot<OutboxEventEntity>()
        every { outboxEventRepository.save(capture(savedSlot)) } answers { firstArg() }
        val publisher = EventPublisher(outboxEventRepository, ObjectMapper())

        When("both are published") {
            publisher.publishAfterCommit("topic-a", "key-a", TestPayload("a", 1))
            val firstId = savedSlot.captured.id
            publisher.publishAfterCommit("topic-b", "key-b", TestPayload("b", 2))
            val secondId = savedSlot.captured.id

            Then("each gets a distinct id") {
                (firstId != secondId) shouldBe true
            }
        }
    }
})
