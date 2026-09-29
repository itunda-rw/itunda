package rw.itunda.core.idempotency

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

class IdempotencyCleanupSchedulerTest : BehaviorSpec({
    Given("the idempotency cleanup scheduler") {
        val repository = mockk<IdempotencyRecordRepository>()
        val scheduler = IdempotencyCleanupScheduler(repository)

        When("it runs") {
            val cutoff = slot<Instant>()
            every { repository.deleteExpiredBefore(capture(cutoff)) } returns 4

            scheduler.run()

            Then("it removes only records beyond the replay-retention window") {
                verify(exactly = 1) { repository.deleteExpiredBefore(any()) }
                Duration.between(cutoff.captured, Instant.now()).seconds shouldBe IDEMPOTENCY_RETENTION.seconds
            }
        }

        Then("it runs transactionally on a bounded cadence") {
            val run = IdempotencyCleanupScheduler::class.java.getMethod("run")
            val scheduled = run.getAnnotation(Scheduled::class.java)

            (run.getAnnotation(Transactional::class.java) != null) shouldBe true
            scheduled.fixedDelayString shouldBe "\${itunda.idempotency.cleanup-interval-ms:3600000}"
        }
    }
})
