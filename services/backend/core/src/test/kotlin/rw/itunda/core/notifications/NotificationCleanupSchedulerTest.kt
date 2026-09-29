package rw.itunda.core.notifications

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.repository.NotificationRepository
import java.time.Duration
import java.time.Instant

class NotificationCleanupSchedulerTest : BehaviorSpec({
    Given("the notification cleanup scheduler") {
        val repository = mockk<NotificationRepository>()
        val scheduler = NotificationCleanupScheduler(repository)

        When("it runs") {
            val cutoff = slot<Instant>()
            every { repository.deleteByIsReadTrueAndCreatedAtBefore(capture(cutoff)) } returns 7

            scheduler.run()

            Then("it removes only read notifications beyond the retention window, never unread ones") {
                verify(exactly = 1) { repository.deleteByIsReadTrueAndCreatedAtBefore(any()) }
                Duration.between(cutoff.captured, Instant.now()).seconds shouldBe NOTIFICATION_READ_RETENTION.seconds
            }
        }

        Then("it runs transactionally on a bounded cadence") {
            val run = NotificationCleanupScheduler::class.java.getMethod("run")
            val scheduled = run.getAnnotation(Scheduled::class.java)

            (run.getAnnotation(Transactional::class.java) != null) shouldBe true
            scheduled.fixedDelayString shouldBe "\${itunda.notifications.cleanup-interval-ms:3600000}"
        }
    }
})
