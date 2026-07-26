package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.ProductSubscription
import rw.itunda.core.domain.ProductSubscriptionStatus
import java.time.Instant

interface ProductSubscriptionRepository : JpaRepository<ProductSubscription, String> {
    fun findByCustomerIdOrderByCreatedAtDesc(customerId: String): List<ProductSubscription>
    fun findByIdAndCustomerId(id: String, customerId: String): ProductSubscription?
    fun findByStatusAndNextDeliveryAtLessThanEqual(status: ProductSubscriptionStatus, nextDeliveryAt: Instant): List<ProductSubscription>
}
