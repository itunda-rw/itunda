package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.ProductInquiry

interface ProductInquiryRepository : JpaRepository<ProductInquiry, String> {
    fun findByProductIdOrderByCreatedAtDesc(productId: String, pageable: Pageable): Page<ProductInquiry>
    fun findByBuyerIdOrderByCreatedAtDesc(buyerId: String, pageable: Pageable): Page<ProductInquiry>
}
