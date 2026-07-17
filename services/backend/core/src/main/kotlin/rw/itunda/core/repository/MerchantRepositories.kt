package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.PaymentIntent

interface MerchantRepository : JpaRepository<Merchant, String> {
    fun findByOwnerUserId(ownerUserId: String): Merchant?
    // Paginated -- see PageResponse.kt's doc comment; Toss Shopping's merchant catalog
    // grows as more merchants register and had no bound at all before this.
    fun findByStatus(status: MerchantStatus, pageable: Pageable): Page<Merchant>
}

interface PaymentIntentRepository : JpaRepository<PaymentIntent, String> {
    fun findByMerchantIdOrderByCreatedAtDesc(merchantId: String): List<PaymentIntent>
}

interface MerchantProductRepository : JpaRepository<MerchantProduct, String> {
    fun findByMerchantIdAndActiveTrue(merchantId: String): List<MerchantProduct>
}
