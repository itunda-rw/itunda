package rw.itunda.merchant

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
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
        }
    }
})
