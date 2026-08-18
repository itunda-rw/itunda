package rw.itunda.merchant

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.WebhookDelivery
import rw.itunda.core.domain.WebhookDeliveryStatus
import rw.itunda.core.repository.WebhookDeliveryRepository
import java.time.Instant

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

    // Section 180's own audit: run()'s @Transactional wraps the WHOLE batch (needed to
    // hold the real pessimistic lock across every due row, not something that can be
    // safely split out into per-item transactions the way this codebase's other
    // scheduler transaction-poisoning fixes do) -- so unlike those, defense here is a
    // real per-row try/catch inside the same transaction rather than a structural
    // split. This proves that guard actually works: a real, unexpected throw from one
    // delivery's own retry() call must not stop the SAME poll from still attempting
    // every other due delivery.
    Given("two due webhook deliveries where the first throws unexpectedly") {
        val webhookDeliveryRepository = mockk<WebhookDeliveryRepository>()
        val webhookDeliveryService = mockk<WebhookDeliveryService>()
        val scheduler = WebhookRetryScheduler(webhookDeliveryRepository, webhookDeliveryService)

        val failingDelivery = WebhookDelivery(
            id = "webhook_delivery_1", merchantId = "merchant_1", eventType = "ORDER_CREATED",
            webhookUrl = "https://example.com/hook1", payload = "{}", nextAttemptAt = Instant.now(),
        )
        val healthyDelivery = WebhookDelivery(
            id = "webhook_delivery_2", merchantId = "merchant_2", eventType = "ORDER_CREATED",
            webhookUrl = "https://example.com/hook2", payload = "{}", nextAttemptAt = Instant.now(),
        )
        every { webhookDeliveryRepository.findTop100ByStatusAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(any(), any()) } returns
            listOf(failingDelivery, healthyDelivery)
        every { webhookDeliveryService.retry(failingDelivery) } throws RuntimeException("real unexpected failure")
        every { webhookDeliveryService.retry(healthyDelivery) } returns Unit

        When("the scheduler runs") {
            scheduler.run()

            Then("it still attempts the second, healthy delivery in the same poll") {
                verify(exactly = 1) { webhookDeliveryService.retry(healthyDelivery) }
            }
        }
    }
})
