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

    // Real external-checkout API-key resolution (2026-07-21) -- see
    // Merchant.apiKeyHash's own doc comment. Mirrors PartnerRepository.findByApiKeyHash
    // exactly.
    fun findByApiKeyHash(apiKeyHash: String): Merchant?
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

    // Real cross-merchant product search (2026-07-20) -- found missing while
    // researching Coupang/Naver/Toss Shopping for parity: a shopper could only search
    // MERCHANT names (Merchant.search above) then browse one seller's catalog at a
    // time. There was no way to search for a PRODUCT across every seller at once, the
    // single most basic real feature every one of those apps has. No JPA relationship
    // exists between MerchantProduct and Merchant (both just carry a raw id string,
    // same convention as GroupAccount.walletId/GroupAccountMember.groupAccountId), so
    // this is the same theta-style `JOIN ... ON` GroupMessagingRepositories already
    // established for exactly that situation, not a broken relationship mapping.
    @Query(
        "SELECT p FROM MerchantProduct p JOIN Merchant m ON m.id = p.merchantId " +
            "WHERE p.active = true AND m.status = :status " +
            "AND LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))",
    )
    fun search(@Param("status") status: MerchantStatus, @Param("q") q: String, pageable: Pageable): Page<MerchantProduct>

    // Real "Deals" rail (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 5
    // recommendation #8: a curated deal rail on the Shop landing surface, above the
    // raw merchant list. Never a fabricated/hardcoded promo -- every row here is a
    // real merchant-set discount (discountPercent is always server-computed at write
    // time from price/originalPrice, see MerchantProduct.kt's own doc comment, so this
    // can never surface a fake or manipulated "deal").
    @Query(
        "SELECT p FROM MerchantProduct p JOIN Merchant m ON m.id = p.merchantId " +
            "WHERE p.active = true AND m.status = :status AND p.discountPercent IS NOT NULL AND p.discountPercent > 0 " +
            "ORDER BY p.discountPercent DESC",
    )
    fun findDeals(@Param("status") status: MerchantStatus, pageable: Pageable): Page<MerchantProduct>
}
