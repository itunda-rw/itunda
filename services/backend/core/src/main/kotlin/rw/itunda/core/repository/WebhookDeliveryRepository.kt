package rw.itunda.core.repository

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import rw.itunda.core.domain.WebhookDelivery
import rw.itunda.core.domain.WebhookDeliveryStatus
import java.time.Instant

interface WebhookDeliveryRepository : JpaRepository<WebhookDelivery, String> {
    fun findTop100ByMerchantIdOrderByCreatedAtDesc(merchantId: String): List<WebhookDelivery>

    // Real ops visibility (Merchant product-completeness pass) -- see
    // WebhookDeliveryService.getExhaustedQueue's own doc comment: EXHAUSTED
    // deliveries had zero admin surface across merchants (a merchant's own
    // self-service deliveryHistory is scoped to just that merchant, useless for
    // spotting a systemic downstream problem shared across several).
    fun findByStatusOrderByCreatedAtDesc(status: WebhookDeliveryStatus, pageable: Pageable): Page<WebhookDelivery>

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
