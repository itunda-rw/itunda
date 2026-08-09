package rw.itunda.merchant

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.WebhookDeliveryStatus
import rw.itunda.core.repository.WebhookDeliveryRepository

class WebhookRetrySchedulerTest : BehaviorSpec({
    Given("the webhook retry scheduler") {
        When("it scans due deliveries") {
            val repositoryLock = WebhookDeliveryRepository::class.java
                .getMethod(
                    "findTop100ByStatusAndNextAttemptAtBeforeOrderByNextAttemptAtAsc",
                    WebhookDeliveryStatus::class.java,
                    java.time.Instant::class.java,
                )
                .getAnnotation(Lock::class.java)
            val transaction = WebhookRetryScheduler::class.java
                .getMethod("run")
                .getAnnotation(Transactional::class.java)

            Then("it claims rows exclusively within a transaction") {
                repositoryLock.value shouldBe LockModeType.PESSIMISTIC_WRITE
                (transaction != null) shouldBe true
            }

            // Real bug found live (2026-08-09), same class as OutboxRelay's own fix the
            // same day: PESSIMISTIC_WRITE over an unbounded `status = PENDING` range
            // takes real InnoDB gap locks under MySQL's default REPEATABLE READ
            // isolation, which can block an unrelated new webhook_deliveries insert
            // elsewhere in the backend if enough merchant endpoints are slow/down at
            // once. READ_COMMITTED keeps the real per-row lock without the gap lock.
            Then("it runs under READ_COMMITTED so it cannot gap-lock unrelated inserts") {
                transaction.isolation shouldBe Isolation.READ_COMMITTED
            }
        }
    }
})
