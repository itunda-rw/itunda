package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.MerchantBillingPlan
import rw.itunda.core.domain.MerchantBillingSubscription
import rw.itunda.core.domain.MerchantBillingSubscriptionStatus
import java.time.Instant

interface MerchantBillingPlanRepository : JpaRepository<MerchantBillingPlan, String> {
    fun findByMerchantIdAndActiveTrue(merchantId: String): List<MerchantBillingPlan>
    fun findByMerchantIdOrderByCreatedAtDesc(merchantId: String): List<MerchantBillingPlan>
}

interface MerchantBillingSubscriptionRepository : JpaRepository<MerchantBillingSubscription, String> {
    fun findByCustomerIdOrderByCreatedAtDesc(customerId: String): List<MerchantBillingSubscription>
    fun findByIdAndCustomerId(id: String, customerId: String): MerchantBillingSubscription?
    fun findByStatusAndNextChargeAtLessThanEqual(status: MerchantBillingSubscriptionStatus, nextChargeAt: Instant): List<MerchantBillingSubscription>
}
