package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.WebhookDelivery
import rw.itunda.core.domain.WebhookDeliveryStatus
import java.time.Instant

interface WebhookDeliveryRepository : JpaRepository<WebhookDelivery, String> {
    fun findTop100ByStatusAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(
        status: WebhookDeliveryStatus,
        now: Instant,
    ): List<WebhookDelivery>
}
