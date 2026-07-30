package rw.itunda.core.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import rw.itunda.core.domain.WebhookDelivery
import rw.itunda.core.domain.WebhookDeliveryStatus
import java.time.Instant

interface WebhookDeliveryRepository : JpaRepository<WebhookDelivery, String> {
    fun findTop100ByMerchantIdOrderByCreatedAtDesc(merchantId: String): List<WebhookDelivery>

    /**
     * Multiple backend replicas may run the retry scheduler. Claiming the selected
     * rows prevents concurrent attempts of the same webhook delivery.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findTop100ByStatusAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(
        status: WebhookDeliveryStatus,
        now: Instant,
    ): List<WebhookDelivery>
}
