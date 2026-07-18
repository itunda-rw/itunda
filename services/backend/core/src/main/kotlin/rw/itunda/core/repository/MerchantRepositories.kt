package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.PaymentIntent

interface MerchantRepository : JpaRepository<Merchant, String> {
    fun findByOwnerUserId(ownerUserId: String): Merchant?
    // Paginated -- see PageResponse.kt's doc comment; Toss Shopping's merchant catalog
    // grows as more merchants register and had no bound at all before this.
    fun findByStatus(status: MerchantStatus, pageable: Pageable): Page<Merchant>

    // Real category/search filter (2026-07-19) for restaurant/merchant browse -- both
    // params optional and independently combinable, matching how a real Coupang
    // Eats-style filter bar works (category chip + free-text search, either or both).
    @Query(
        "SELECT m FROM Merchant m WHERE m.status = :status " +
            "AND (:category IS NULL OR m.category = :category) " +
            "AND (:q IS NULL OR LOWER(m.businessName) LIKE LOWER(CONCAT('%', :q, '%')))",
    )
    fun search(
        @Param("status") status: MerchantStatus,
        @Param("category") category: String?,
        @Param("q") q: String?,
        pageable: Pageable,
    ): Page<Merchant>

    // Real distinct category list -- powers a category chip row without hardcoding a
    // fixed taxonomy client-side (a merchant's own real, self-set categories are the
    // source of truth, same "no fabricated data" discipline as everywhere else).
    @Query("SELECT DISTINCT m.category FROM Merchant m WHERE m.status = :status AND m.category IS NOT NULL ORDER BY m.category")
    fun findDistinctCategories(@Param("status") status: MerchantStatus): List<String>
}

interface PaymentIntentRepository : JpaRepository<PaymentIntent, String> {
    fun findByMerchantIdOrderByCreatedAtDesc(merchantId: String): List<PaymentIntent>
}

interface MerchantProductRepository : JpaRepository<MerchantProduct, String> {
    fun findByMerchantIdAndActiveTrue(merchantId: String): List<MerchantProduct>
}
