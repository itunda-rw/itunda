package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.PaymentIntent

interface MerchantRepository : JpaRepository<Merchant, String> {
    fun findByOwnerUserId(ownerUserId: String): Merchant?
    fun findByStatus(status: MerchantStatus): List<Merchant>
}

interface PaymentIntentRepository : JpaRepository<PaymentIntent, String> {
    fun findByMerchantIdOrderByCreatedAtDesc(merchantId: String): List<PaymentIntent>
}

interface MerchantProductRepository : JpaRepository<MerchantProduct, String> {
    fun findByMerchantIdAndActiveTrue(merchantId: String): List<MerchantProduct>
}
