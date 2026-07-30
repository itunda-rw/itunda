package rw.itunda.core.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.Optional

class IdempotencyServiceTest : BehaviorSpec({
    Given("a malformed idempotency key") {
        val claimStore = mockk<IdempotencyClaimStore>()
        val service = IdempotencyService(claimStore, mockk(), ObjectMapper())

        When("it is blank or longer than the documented limit") {
            Then("it is rejected before it can claim or persist a record") {
                shouldThrow<InvalidIdempotencyKeyException> {
                    service.replayOrExecute("POST /api/v1/payments", " ", emptyMap<String, Any?>()) { 200 to emptyMap() }
                }
                shouldThrow<InvalidIdempotencyKeyException> {
                    service.replayOrExecute("POST /api/v1/payments", "a".repeat(301), emptyMap<String, Any?>()) { 200 to emptyMap() }
                }
                verify(exactly = 0) { claimStore.claim(any(), any()) }
            }
        }
    }

    Given("a completed withdrawal-code request with the same idempotency key") {
        val claimStore = mockk<IdempotencyClaimStore>()
        val repository = mockk<IdempotencyRecordRepository>()
        val service = IdempotencyService(claimStore, repository, ObjectMapper())
        every { claimStore.claim(any(), any()) } returns ClaimOutcome.Replay(
            IdempotentReplay(201, mapOf("success" to true, "authorization" to mapOf("code" to "A1B2C3D4E5F6"))),
        )

        When("the mobile client retries the request") {
            val result = service.replayOrExecute(
                "POST /api/v1/wallet/agent-withdrawal-authorizations", "retry-key", mapOf("amount" to 5000),
            ) { error("a replay must not create a second authorization") }

            Then("it returns the original response without executing the money action") {
                result.first shouldBe 201
                result.second["success"] shouldBe true
                verify(exactly = 0) { repository.save(any()) }
            }
        }
    }

    Given("a key already associated with a different withdrawal request") {
        val claimStore = mockk<IdempotencyClaimStore>()
        val service = IdempotencyService(claimStore, mockk(), ObjectMapper())
        every { claimStore.claim(any(), any()) } returns ClaimOutcome.Conflict

        Then("it rejects the request before a second code can be created") {
            shouldThrow<IdempotencyConflictException> {
                service.replayOrExecute(
                    "POST /api/v1/wallet/agent-withdrawal-authorizations", "retry-key", mapOf("amount" to 7000),
                ) { error("a conflicted key must not execute") }
            }
        }
    }

    Given("a claimed request whose final flush fails") {
        val claimStore = mockk<IdempotencyClaimStore>()
        val repository = mockk<IdempotencyRecordRepository>()
        val service = IdempotencyService(claimStore, repository, ObjectMapper())
        val record = IdempotencyRecordEntity("POST /api/v1/payments::retry-key", "{}", -1, "{}", Instant.now())
        every { claimStore.claim(any(), any()) } returns ClaimOutcome.Claimed
        every { repository.findById(record.recordKey) } returns Optional.of(record)
        every { repository.saveAndFlush(record) } throws RuntimeException("optimistic lock")
        every { claimStore.release(record.recordKey) } returns Unit

        Then("it releases the independent processing claim so a genuine retry is not blocked") {
            shouldThrow<RuntimeException> {
                service.replayOrExecute("POST /api/v1/payments", "retry-key", emptyMap<String, Any?>()) { 200 to mapOf("success" to true) }
            }
            verify(exactly = 1) { claimStore.release(record.recordKey) }
        }
    }
})
