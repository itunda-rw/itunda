package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantBusinessType
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.PaymentIntent
import java.math.BigDecimal

interface MerchantRepository : JpaRepository<Merchant, String> {
    fun findByOwnerUserId(ownerUserId: String): Merchant?

    // Real external-checkout API-key resolution (2026-07-21) -- see
    // Merchant.apiKeyHash's own doc comment. Mirrors PartnerRepository.findByApiKeyHash
    // exactly.
    fun findByApiKeyHash(apiKeyHash: String): Merchant?

    // Real Toss Payments-style grace-period key reissue (2026-07-28) -- see
    // MerchantService.generateApiKey's own doc comment.
    fun findByPreviousApiKeyHash(previousApiKeyHash: String): Merchant?
    // Paginated -- see PageResponse.kt's doc comment; Toss Shopping's merchant catalog
    // grows as more merchants register and had no bound at all before this.
    fun findByStatus(status: MerchantStatus, pageable: Pageable): Page<Merchant>

    // Real admin moderation queue (2026-08-04) -- see MerchantService.suspendMerchant's
    // own doc comment. An ACTIVE merchant with no category is either an incomplete
    // registration or, as found live, a leftover QA fixture -- either way it doesn't
    // belong in public browse (setCategory's own doc comment: category powers both
    // Shop's and Eats' real filter chips, so a merchant that can never appear under any
    // real chip already can't be found the intended way).
    fun findByStatusAndCategoryIsNull(status: MerchantStatus, pageable: Pageable): Page<Merchant>

    // Real category/search filter (2026-07-19) for restaurant/merchant browse -- both
    // params optional and independently combinable, matching how a real Coupang
    // Eats-style filter bar works (category chip + free-text search, either or both).
    // businessType added 2026-08-13 (see MerchantBusinessType's own doc comment) --
    // null (the default every existing call site keeps) means "no vertical filter,"
    // preserving Shop's own unfiltered browse exactly as before; Eats now passes
    // RESTAURANT explicitly.
    @Query(
        "SELECT m FROM Merchant m WHERE m.status = :status " +
            "AND (:category IS NULL OR m.category = :category) " +
            "AND (:businessType IS NULL OR m.businessType = :businessType) " +
            "AND (:q IS NULL OR LOWER(m.businessName) LIKE LOWER(CONCAT('%', :q, '%')))",
    )
    fun search(
        @Param("status") status: MerchantStatus,
        @Param("category") category: String?,
        @Param("businessType") businessType: MerchantBusinessType?,
        @Param("q") q: String?,
        pageable: Pageable,
    ): Page<Merchant>

    // Real distinct category list -- powers a category chip row without hardcoding a
    // fixed taxonomy client-side (a merchant's own real, self-set categories are the
    // source of truth, same "no fabricated data" discipline as everywhere else).
    // businessType added 2026-08-13 -- Eats scopes this to RESTAURANT so its own chip
    // row can never surface a non-food category like "Electronics" again.
    @Query(
        "SELECT DISTINCT m.category FROM Merchant m WHERE m.status = :status AND m.category IS NOT NULL " +
            "AND (:businessType IS NULL OR m.businessType = :businessType) ORDER BY m.category",
    )
    fun findDistinctCategories(@Param("status") status: MerchantStatus, @Param("businessType") businessType: MerchantBusinessType?): List<String>
}

interface PaymentIntentRepository : JpaRepository<PaymentIntent, String> {
    fun findByMerchantIdOrderByCreatedAtDesc(merchantId: String): List<PaymentIntent>

    // Real Toss Payments ARS결제-style USSD payment completion -- see
    // PaymentIntent.ussdCode's own doc comment. Existence check used at generation
    // time to avoid a real code collision across concurrently-live intents.
    fun existsByUssdCode(ussdCode: String): Boolean
    fun findByUssdCode(ussdCode: String): PaymentIntent?
}

interface MerchantProductRepository : JpaRepository<MerchantProduct, String> {
    fun findByMerchantIdAndActiveTrue(merchantId: String): List<MerchantProduct>

    // Real cross-merchant product search (2026-07-20) -- found missing while
    // researching Coupang/Naver/Toss Shopping for parity: a shopper could only search
    // MERCHANT names (Merchant.search above) then browse one seller's catalog at a
    // time. There was no way to search for a PRODUCT across every seller at once, the
    // single most basic real feature every one of those apps has. No JPA relationship
    // exists between MerchantProduct and Merchant (both just carry a raw id string,
    // same convention as GroupAccount.accountId/GroupAccountMember.groupAccountId), so
    // this is the same theta-style `JOIN ... ON` GroupMessagingRepositories already
    // established for exactly that situation, not a broken relationship mapping.
    @Query(
        "SELECT p FROM MerchantProduct p JOIN Merchant m ON m.id = p.merchantId " +
            "WHERE p.active = true AND m.status = :status " +
            "AND LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))",
    )
    fun search(@Param("status") status: MerchantStatus, @Param("q") q: String, pageable: Pageable): Page<MerchantProduct>

    // Real relevance-ranked full-text search (2026-08-14) -- see V242's own migration
    // comment for the "why" (the LIKE search above never ranked results and only ever
    // matched one exact substring). MySQL's FULLTEXT + MATCH...AGAINST is a native
    // query, not JPQL -- Hibernate has no portable JPQL equivalent -- so this returns
    // Page<MerchantProduct> via nativeQuery=true with an explicit countQuery, the
    // standard Spring Data pattern for a paginated native query. `:#{#status.name()}`
    // is required (not `:status` directly): unlike the JPQL query above, a native
    // query has no entity-mapping context to know MerchantStatus is @Enumerated(STRING)
    // on its own, so this SpEL binding does that conversion explicitly.
    // ShoppingController.searchProducts builds `booleanQuery` (MySQL BOOLEAN MODE
    // syntax, e.g. "+phone* +case*") and falls back to the plain `search` above for
    // queries shorter than MySQL's minimum indexed token length, where FULLTEXT
    // structurally can't match anything.
    @Query(
        value = "SELECT p.* FROM merchant_products p JOIN merchants m ON m.id = p.merchant_id " +
            "WHERE p.active = true AND m.status = :#{#status.name()} " +
            "AND MATCH(p.name, p.description) AGAINST (:booleanQuery IN BOOLEAN MODE) " +
            "ORDER BY MATCH(p.name, p.description) AGAINST (:booleanQuery IN BOOLEAN MODE) DESC",
        countQuery = "SELECT COUNT(*) FROM merchant_products p JOIN merchants m ON m.id = p.merchant_id " +
            "WHERE p.active = true AND m.status = :#{#status.name()} " +
            "AND MATCH(p.name, p.description) AGAINST (:booleanQuery IN BOOLEAN MODE)",
        nativeQuery = true,
    )
    fun searchFullText(@Param("status") status: MerchantStatus, @Param("booleanQuery") booleanQuery: String, pageable: Pageable): Page<MerchantProduct>

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

    // Real 마감할인 (closing/surplus discount) browse query (2026-08-15) -- distinct
    // from findDeals above: only real, merchant-flagged, still-genuinely-in-stock,
    // not-yet-expired surplus listings, ranked soonest-to-expire first (the real
    // Baemin/Yogiyo/Coupang Eats urgency framing this feature is sourced from --
    // "sells out tonight," not just "on sale"). `stockQuantity IS NULL` is
    // deliberately excluded from "in stock" here (unlike a normal product) -- an
    // unbounded surplus deal would be a contradiction in terms, so a merchant that
    // never sets a real quantity simply never appears in this list.
    @Query(
        "SELECT p FROM MerchantProduct p JOIN Merchant m ON m.id = p.merchantId " +
            "WHERE p.active = true AND m.status = :status AND p.isSurplusDeal = true " +
            "AND p.surplusExpiresAt > :now AND p.stockQuantity IS NOT NULL AND p.stockQuantity > 0 " +
            "ORDER BY p.surplusExpiresAt ASC",
    )
    fun findSurplusDeals(@Param("status") status: MerchantStatus, @Param("now") now: java.time.Instant, pageable: Pageable): Page<MerchantProduct>

    // Real Coupang Eats-style dish grid (2026-08-03) -- see EatsController.dishes' own
    // doc comment for the full 100%-UI/UX-parity account. category filters by the
    // same Merchant.category chip Eats' own restaurant-list already filters on
    // (search's own doc comment) -- a real photo requirement, not a fabricated
    // placeholder image, is what makes this genuinely a dish *grid* rather than the
    // pre-existing restaurant list with a different name.
    // businessType added 2026-08-13 (see MerchantBusinessType's own doc comment) --
    // without this, any non-restaurant merchant's photographed product (the Fashion
    // seed merchant's t-shirt/sneakers, the Electronics merchant's phone -- all real,
    // all have imageUrl set) qualified as a "dish" purely by having a photo.
    // Real Coupang Eats-style budget filter (2026-08-16, "AI 개인화 메뉴 추천" -- see
    // EatsController.getDishes' own doc comment for the full sourced account).
    // Deliberately just the price cap, not Coupang's own real delivery-fee-aware total
    // budget (which would need a per-merchant distance/fee computation at browse time,
    // a bigger v2, not this pass) -- itunda's own honest, smaller v1 slice.
    @Query(
        "SELECT p FROM MerchantProduct p JOIN Merchant m ON m.id = p.merchantId " +
            "WHERE p.active = true AND m.status = :status AND p.imageUrl IS NOT NULL " +
            "AND (:category IS NULL OR m.category = :category) " +
            "AND (:businessType IS NULL OR m.businessType = :businessType) " +
            "AND (:maxBudget IS NULL OR p.price <= :maxBudget) " +
            "ORDER BY p.createdAt DESC",
    )
    fun findDishes(
        @Param("status") status: MerchantStatus,
        @Param("category") category: String?,
        @Param("businessType") businessType: MerchantBusinessType?,
        @Param("maxBudget") maxBudget: BigDecimal?,
        pageable: Pageable,
    ): Page<MerchantProduct>

    // Real Coupang WING 상품분석 view-count increment -- see
    // MerchantProduct.viewCount's own doc comment. Atomic UPDATE, same real "avoid the
    // lost-update race a read-modify-write risks under concurrent viewers" discipline
    // ListingRepository.incrementViewCount already established.
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE MerchantProduct p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    fun incrementViewCount(@Param("id") id: String): Int
}
