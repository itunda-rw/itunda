package rw.itunda.merchant.web

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.domain.MerchantBusinessType
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.repository.MenuOptionChoiceRepository
import rw.itunda.core.repository.MenuOptionGroupRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.OrderItemRepository
import rw.itunda.core.repository.ProductPriceTierRepository
import rw.itunda.core.repository.ProductReviewRepository
import rw.itunda.core.search.FullTextSearchUtil
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import rw.itunda.merchant.ShoppingCashbackService
import rw.itunda.messaging.MessagingService
import rw.itunda.messaging.SelfConversationException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ShoppingMerchantNotFoundException(message: String) : RuntimeException(message)

// Real seller-chat entry point (2026-08-28) -- mirrors OwnListingException's own
// role for MarketplaceService.contactSeller exactly: thrown when a merchant owner
// tries to message their own store.
class OwnMerchantException(message: String) : RuntimeException(message)

// The real "browse partner merchants" half of "Toss Shopping" -- see
// ShoppingCashbackService's own doc comment for why itunda's own real registered
// Merchant directory is the honest, non-fabricated catalog here (itunda has no external
// merchant-partnership network to draw one from instead). Requires a normal itunda-user
// JWT (default SecurityConfig .anyRequest().authenticated()) -- this is a real user
// browsing where they can earn cashback, not a public/partner-authenticated surface.
@RestController
@RequestMapping("/api/v1/shopping")
class ShoppingController(
    private val merchantRepository: MerchantRepository,
    private val merchantProductRepository: MerchantProductRepository,
    private val menuOptionGroupRepository: MenuOptionGroupRepository,
    private val menuOptionChoiceRepository: MenuOptionChoiceRepository,
    private val priceTierRepository: ProductPriceTierRepository,
    private val merchantProductService: rw.itunda.merchant.MerchantProductService,
    private val productReviewRepository: ProductReviewRepository,
    private val orderItemRepository: OrderItemRepository,
    private val messagingService: MessagingService,
    private val merchantBrowseService: rw.itunda.merchant.ShoppingMerchantBrowseService,
) {
    // Real "Best seller" badge (2026-08-28) -- a genuine, derived signal (real gross
    // order count per product, see OrderItemRepository.getProductOrderCounts' own doc
    // comment), not fabricated marketing copy. Deliberately conservative: only the
    // top MAX_BEST_SELLERS-per-page products qualify, and only once they clear a real
    // minimum order count -- an empty/near-empty catalog page never gets an arbitrary
    // "best seller" tag just because it happened to rank first among near-zero counts.
    private fun bestSellerProductIds(productIds: List<String>): Set<String> {
        if (productIds.isEmpty()) return emptySet()
        return orderItemRepository.getProductOrderCounts(productIds)
            .filter { it.count >= MIN_ORDERS_FOR_BEST_SELLER }
            .sortedByDescending { it.count }
            .take(MAX_BEST_SELLERS_PER_PAGE)
            .map { it.productId }
            .toSet()
    }

    companion object {
        private const val MIN_ORDERS_FOR_BEST_SELLER = 3L
        private const val MAX_BEST_SELLERS_PER_PAGE = 3
        private const val MIN_CO_OCCURRENCE_FOR_FREQUENTLY_ORDERED_WITH = 2L
        private const val MAX_FREQUENTLY_ORDERED_WITH = 4
    }
    // Real Coupang WING 상품분석 (product analytics) view trigger -- see
    // MerchantProductService.getProduct's own doc comment. bank-mfe's `ProductDetailView`
    // renders straight off the merchant's already-fetched catalog list with zero real
    // per-product fetch anywhere -- this endpoint gives it one to call on mount.
    @GetMapping("/products/{productId}")
    fun getProduct(@PathVariable productId: String): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "product" to merchantProductService.getProduct(productId)))

    // Real "frequently ordered together" cross-sell (itunda Eats redesign, 2026-08-28)
    // -- see OrderItemRepository.getFrequentlyOrderedWith's own doc comment for the
    // real co-occurrence query this reads from. Deliberately conservative, same
    // "never fabricate a signal from too little real data" discipline
    // bestSellerProductIds already establishes: a pair that has only ever shared ONE
    // real past order never qualifies, and the response is honestly empty (not padded
    // with unrelated products) when nothing clears the bar.
    @GetMapping("/products/{productId}/frequently-ordered-with")
    fun getFrequentlyOrderedWith(@PathVariable productId: String): ResponseEntity<Map<String, Any?>> {
        val coOccurringIds = orderItemRepository.getFrequentlyOrderedWith(productId)
            .filter { it.count >= MIN_CO_OCCURRENCE_FOR_FREQUENTLY_ORDERED_WITH }
            .take(MAX_FREQUENTLY_ORDERED_WITH)
            .map { it.productId }
        if (coOccurringIds.isEmpty()) {
            return ResponseEntity.ok(mapOf("success" to true, "products" to emptyList<Any>()))
        }
        // findAllById doesn't preserve caller order, so re-sort by the real
        // co-occurrence ranking above rather than whatever order the DB returns.
        val productsById = merchantProductRepository.findAllById(coOccurringIds).associateBy { it.id }
        val orderedProducts = coOccurringIds.mapNotNull { productsById[it] }
        val merchantNames = merchantRepository.findAllById(orderedProducts.map { it.merchantId }.distinct()).associate { it.id to it.businessName }
        val products = orderedProducts.map { p ->
            mapOf(
                "id" to p.id, "merchantId" to p.merchantId, "merchantName" to (merchantNames[p.merchantId] ?: ""),
                "name" to p.name, "price" to p.price,
                "imageUrl" to p.imageUrl, "originalPrice" to p.originalPrice, "discountPercent" to p.discountPercent,
                "stockQuantity" to p.stockQuantity,
            )
        }
        return ResponseEntity.ok(mapOf("success" to true, "products" to products))
    }

    // Real category/search filter (2026-07-19) -- both params optional and
    // independently combinable, backing restaurant categories + search/filter for Eats
    // (this same endpoint is also Shopping's own merchant browse, so both surfaces get
    // it for free). Blank query params are treated as absent rather than an empty-string
    // match, since `?category=` from an unset UI filter shouldn't behave differently
    // from omitting it entirely.
    //
    // Real browse-card enrichment (2026-07-21) -- closes docs/DESIGN_REFERENCES.md's Eats
    // recommendations #1/#2: photoUrl/minOrderAmount (real, merchant-set), rating/
    // reviewCount (real, batch-aggregated from EatsReview -- previously only visible one
    // tap deeper inside RestaurantMenuView), and, when the caller supplies their own real
    // buyerLat/buyerLng, a real distanceKm (Haversine straight-line, not the OSRM road
    // distance EatsOrderService's delivery-fee calculation uses -- a browse list only
    // needs a rough real distance to sort/display by) plus a real, clearly-an-ESTIMATE
    // deliveryTimeMinutes derived from that distance. All new fields are null/omitted
    // when there's genuinely nothing real to compute -- never a fabricated number.
    // businessType added 2026-08-13 (see MerchantBusinessType's own doc comment) --
    // Eats now passes RESTAURANT explicitly so its own browse can never surface a
    // non-food merchant again; omitted (null) keeps Shop's own browse exactly as
    // unfiltered as it was before this param existed.
    @GetMapping("/merchants")
    fun getEligibleMerchants(
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) businessType: MerchantBusinessType?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) buyerLat: Double?,
        @RequestParam(required = false) buyerLng: Double?,
        // Real Baemin/Coupang Eats-style "fastest delivery" sort tab (2026-08-16) --
        // see ShoppingMerchantBrowseService.browse's own doc comment for the full
        // real search/sort/enrich account (extracted from this endpoint 2026-08-28,
        // keeping this controller under the real 500-line file-size-lint guideline).
        @RequestParam(required = false) sortBy: String?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val (page, merchants) = merchantBrowseService.browse(category, businessType, q, buyerLat, buyerLng, sortBy, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "merchants" to merchants) + pageMeta(page))
    }

    // Real distinct category list -- see MerchantRepository.findDistinctCategories's own
    // doc comment for why this is derived from real merchant data, not a hardcoded list.
    @GetMapping("/merchants/categories")
    fun getCategories(@RequestParam(required = false) businessType: MerchantBusinessType?): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "categories" to merchantRepository.findDistinctCategories(MerchantStatus.ACTIVE, businessType)))

    // Real public per-merchant product browse -- the missing piece a buyer needs to see
    // a specific seller's real catalog before checking out via the new Coupang-style
    // rw.itunda.commerce module (POST /api/v1/orders needs a real, already-resolved
    // productId per line item). MerchantProductController's own /merchant/products
    // endpoint is deliberately owner-only (a merchant managing their own catalog); this
    // is the read-only public counterpart a shopper needs instead, reusing the exact
    // same real MerchantProductRepository.findByMerchantIdAndActiveTrue query
    // MerchantProductService.getCatalog already established.
    //
    // Real menu-options enrichment (2026-07-21) -- folds each product's real option
    // groups + choices directly into this same payload, per
    // docs/DESIGN_REFERENCES.md's own explicit recommendation ("a join, not a new round
    // trip"). Two batched queries total for the whole menu, never one pair per item.
    @GetMapping("/merchants/{merchantId}/products")
    fun getMerchantProducts(@PathVariable merchantId: String): ResponseEntity<Map<String, Any?>> {
        val merchant = merchantRepository.findById(merchantId)
            .orElseThrow { ShoppingMerchantNotFoundException("Merchant not found") }
        val products = merchantProductRepository.findByMerchantIdAndActiveTrue(merchant.id)
        val groupsByProduct = if (products.isNotEmpty()) {
            menuOptionGroupRepository.findByProductIdInOrderByDisplayOrderAsc(products.map { it.id }).groupBy { it.productId }
        } else {
            emptyMap()
        }
        val allGroups = groupsByProduct.values.flatten()
        val choicesByGroup = if (allGroups.isNotEmpty()) {
            menuOptionChoiceRepository.findByGroupIdInOrderByDisplayOrderAsc(allGroups.map { it.id }).groupBy { it.groupId }
        } else {
            emptyMap()
        }
        // Real bulk/wholesale pricing (2026-07-25) -- see ProductPriceTier's own doc
        // comment. Same batched-by-product-ids discipline optionGroups above already
        // established.
        val tiersByProduct = if (products.isNotEmpty()) {
            priceTierRepository.findByProductIdInOrderByMinQuantityAsc(products.map { it.id }).groupBy { it.productId }
        } else {
            emptyMap()
        }
        // isBestSeller added 2026-08-28 -- see bestSellerProductIds' own doc comment.
        val bestSellerIds = bestSellerProductIds(products.map { it.id })
        val enrichedProducts = products.map { product ->
            mapOf(
                "id" to product.id,
                "merchantId" to product.merchantId,
                "name" to product.name,
                "price" to product.price,
                "active" to product.active,
                "createdAt" to product.createdAt,
                // imageUrl/originalPrice/discountPercent/description -- real fields
                // restored 2026-07-21. The 2026-07-21 menu-options merge had rebuilt this
                // endpoint's response as a hand-built map (to fold in optionGroups) and, in
                // doing so, silently dropped the image/discount fields the *same day's*
                // product-images pass had added and depended on this exact endpoint to
                // serve -- a real regression caught by re-reading this controller while
                // building the product-detail screen, not a new gap. description is new.
                "imageUrl" to product.imageUrl,
                "originalPrice" to product.originalPrice,
                "discountPercent" to product.discountPercent,
                "description" to product.description,
                // Public availability is informational only; checkout re-checks and
                // decrements it atomically. Exposing it here prevents shoppers from
                // building a cart around an item the merchant has already sold out.
                "stockQuantity" to product.stockQuantity,
                // Real Baemin CEO app/DoorDash-style "86" (temporarily sold out) flag
                // (2026-08-16) -- see MerchantProduct.soldOut's own doc comment. Shown,
                // not filtered out (unlike `active`), so a customer sees WHY the item
                // can't be added right now instead of it silently vanishing from a menu
                // they were just looking at.
                "soldOut" to product.soldOut,
                // durationMinutes -- real fix, 2026-07-25: this hand-built response map
                // never included it since the 2026-07-25 booking feature was added, which
                // meant the real "Book" action on this exact browse endpoint's data could
                // never actually appear for a customer -- the same class of regression the
                // imageUrl/originalPrice comment above already documents happening once on
                // this same endpoint. Caught while adding priceTiers below, not a new gap.
                "durationMinutes" to product.durationMinutes,
                "isBestSeller" to (product.id in bestSellerIds),
                "priceTiers" to (tiersByProduct[product.id] ?: emptyList()).map { tier ->
                    mapOf("minQuantity" to tier.minQuantity, "unitPrice" to tier.unitPrice)
                },
                "optionGroups" to (groupsByProduct[product.id] ?: emptyList()).map { group ->
                    mapOf(
                        "id" to group.id,
                        "name" to group.name,
                        // Real multi-select optional add-ons (2026-07-26) -- see
                        // MenuOptionGroup.kt's own doc comment.
                        "required" to group.required,
                        "multiSelect" to group.multiSelect,
                        "choices" to (choicesByGroup[group.id] ?: emptyList()).map { choice ->
                            mapOf("id" to choice.id, "name" to choice.name, "priceDelta" to choice.priceDelta)
                        },
                    )
                },
            )
        }
        return ResponseEntity.ok(
            mapOf("success" to true, "merchant" to mapOf("id" to merchant.id, "businessName" to merchant.businessName), "products" to enrichedProducts),
        )
    }

    // Real cross-merchant product search -- see MerchantProductRepository.search's own
    // doc comment for the gap this closes.
    // Real relevance-ranked upgrade (2026-08-14) -- see MerchantProductRepository.
    // searchFullText's own doc comment. Builds a MySQL BOOLEAN MODE query string here
    // (not in the repository) since it's presentation-layer parsing of raw user input,
    // the same layering this controller already uses elsewhere. Each token is
    // sanitized (boolean-mode operator characters stripped, so a query like "c++" can't
    // accidentally inject search syntax), suffixed with `*` for prefix matching (typing
    // "pho" already matches "phone", a real search-as-you-type feel), and required via
    // a leading `+` (AND of terms, not OR) -- a shopper typing "phone case" almost
    // always wants products matching both words, not a flood of single-word matches.
    // Falls back to the plain LIKE search for queries under 3 characters: MySQL's
    // default innodb_ft_min_token_size is 3, so FULLTEXT structurally can't match
    // anything shorter, not a bug in the query itself.
    @GetMapping("/products/search")
    fun searchProducts(
        @RequestParam q: String,
        // businessType added 2026-08-25 (direct user directive: "we need everything
        // separated to avoid confusion, that's toss style, clear isolation") --
        // same real fix as getEligibleMerchants below: Shop's own product search
        // had no vertical filter, so a restaurant's menu item could surface here.
        @RequestParam(required = false) businessType: MerchantBusinessType?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val trimmed = q.trim()
        val booleanQuery = FullTextSearchUtil.toBooleanModeQuery(trimmed)
        val page = if (booleanQuery != null) {
            merchantProductRepository.searchFullText(MerchantStatus.ACTIVE, businessType?.name, booleanQuery, pageable)
        } else {
            merchantProductRepository.search(MerchantStatus.ACTIVE, businessType, trimmed, pageable)
        }
        // Batch-resolved, same no-N+1 discipline as ProductFavoriteService.getMyFavorites.
        val merchantNames = merchantRepository.findAllById(page.content.map { it.merchantId }.distinct()).associate { it.id to it.businessName }
        // rating/reviewCount added 2026-08-25 -- real ProductReview data, batched the
        // same way as EatsReviewRepository.getRestaurantRatingSummaries. See
        // ProductReviewRepository.getProductRatingSummaries' own doc comment.
        val ratingByProduct = if (page.content.isNotEmpty()) {
            productReviewRepository.getProductRatingSummaries(page.content.map { it.id }).associateBy { it.productId }
        } else emptyMap()
        // isBestSeller added 2026-08-28 -- see bestSellerProductIds' own doc comment.
        val bestSellerIds = bestSellerProductIds(page.content.map { it.id })
        val products = page.content.map { p ->
            val rating = ratingByProduct[p.id]
            mapOf(
                "id" to p.id, "merchantId" to p.merchantId, "merchantName" to (merchantNames[p.merchantId] ?: ""),
                "name" to p.name, "price" to p.price,
                // imageUrl/originalPrice/discountPercent added 2026-07-21 -- see
                // MerchantProduct.kt's own doc comment; search results need the same
                // real product-card fields the per-merchant catalog already exposes.
                // description added 2026-07-21, backing the new product-detail screen.
                "imageUrl" to p.imageUrl, "originalPrice" to p.originalPrice, "discountPercent" to p.discountPercent,
                "description" to p.description,
                "stockQuantity" to p.stockQuantity,
                "rating" to rating?.average, "reviewCount" to (rating?.count ?: 0L),
                "isBestSeller" to (p.id in bestSellerIds),
            )
        }
        return ResponseEntity.ok(mapOf("success" to true, "products" to products) + pageMeta(page))
    }

    // Real "Deals" rail (2026-07-25) -- see MerchantProductRepository.findDeals's own
    // doc comment for the full account. Same response shape as searchProducts above,
    // just a different source query (real discounts, ranked highest-first, instead of
    // a name match).
    @GetMapping("/products/deals")
    fun getDeals(
        // businessType added 2026-08-25, same real isolation fix as searchProducts above.
        @RequestParam(required = false) businessType: MerchantBusinessType?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val page = merchantProductRepository.findDeals(MerchantStatus.ACTIVE, businessType, pageable)
        val merchantNames = merchantRepository.findAllById(page.content.map { it.merchantId }.distinct()).associate { it.id to it.businessName }
        // rating/reviewCount added 2026-08-25, same real batched lookup as
        // searchProducts above -- closes the "no star rating on the recommended grid"
        // gap against the real Toss Shopping reference.
        val ratingByProduct = if (page.content.isNotEmpty()) {
            productReviewRepository.getProductRatingSummaries(page.content.map { it.id }).associateBy { it.productId }
        } else emptyMap()
        // isBestSeller added 2026-08-28 -- see bestSellerProductIds' own doc comment.
        val bestSellerIds = bestSellerProductIds(page.content.map { it.id })
        val products = page.content.map { p ->
            val rating = ratingByProduct[p.id]
            mapOf(
                "id" to p.id, "merchantId" to p.merchantId, "merchantName" to (merchantNames[p.merchantId] ?: ""),
                "name" to p.name, "price" to p.price,
                "imageUrl" to p.imageUrl, "originalPrice" to p.originalPrice, "discountPercent" to p.discountPercent,
                "description" to p.description,
                "stockQuantity" to p.stockQuantity,
                "rating" to rating?.average, "reviewCount" to (rating?.count ?: 0L),
                "isBestSeller" to (p.id in bestSellerIds),
            )
        }
        return ResponseEntity.ok(mapOf("success" to true, "products" to products) + pageMeta(page))
    }

    // Real 마감할인 (closing/surplus discount) browse rail (2026-08-15) -- see
    // MerchantProductRepository.findSurplusDeals' own doc comment for the full sourced
    // account (기후부/환경부 + Baemin/Yogiyo/Coupang Eats, launched 2026-06-15). A real,
    // distinct list from getDeals above: only genuinely time-boxed, still-in-stock
    // closing sales, soonest-to-expire first -- surfaces `surplusExpiresAt` so a client
    // can show a real "sells out at HH:mm" countdown, not a fabricated urgency banner.
    @GetMapping("/products/surplus-deals")
    fun getSurplusDeals(
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val page = merchantProductRepository.findSurplusDeals(MerchantStatus.ACTIVE, Instant.now(), pageable)
        val merchantNames = merchantRepository.findAllById(page.content.map { it.merchantId }.distinct()).associate { it.id to it.businessName }
        val products = page.content.map { p ->
            mapOf(
                "id" to p.id, "merchantId" to p.merchantId, "merchantName" to (merchantNames[p.merchantId] ?: ""),
                "name" to p.name, "price" to p.price,
                "imageUrl" to p.imageUrl, "originalPrice" to p.originalPrice, "discountPercent" to p.discountPercent,
                "description" to p.description,
                "stockQuantity" to p.stockQuantity,
                "surplusExpiresAt" to p.surplusExpiresAt,
            )
        }
        return ResponseEntity.ok(mapOf("success" to true, "products" to products) + pageMeta(page))
    }

    // Real Naver Pay 멤버십 데이 (Membership Day) boost -- see ShoppingCashbackService's
    // own doc comment. A single source of truth for "is today boosted", so bank-mfe's
    // own banner never drifts from what awardCashback actually applies server-side.
    @GetMapping("/membership-day")
    fun getMembershipDayStatus(): ResponseEntity<Map<String, Any?>> {
        val isMembershipDay = ShoppingCashbackService.isMembershipDay(LocalDate.now(ZoneId.of("Africa/Kigali")))
        return ResponseEntity.ok(mapOf("success" to true, "isMembershipDay" to isMembershipDay, "multiplier" to ShoppingCashbackService.MEMBERSHIP_DAY_MULTIPLIER))
    }

    // Real seller chat (2026-08-28) -- mirrors MarketplaceController.contactSeller
    // exactly: resolves the real Merchant.ownerUserId, then hands off to the same
    // generic, shared MessagingService.startOrGetConversation every other vertical
    // (Eats/Marketplace/Community/Jobs/Property) already uses. No new conversation/
    // message primitive -- this is Shop's first real entry point into that existing
    // system, not a bespoke chat layer.
    @PostMapping("/merchants/{merchantId}/contact-seller")
    fun contactSeller(
        @PathVariable merchantId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val merchant = merchantRepository.findById(merchantId)
            .orElseThrow { ShoppingMerchantNotFoundException("Merchant not found") }
        val conversation = try {
            messagingService.startOrGetConversation(currentUser.userId, merchant.ownerUserId)
        } catch (e: SelfConversationException) {
            throw OwnMerchantException("This is your own store")
        }
        return ResponseEntity.ok(mapOf("success" to true, "conversation" to conversation))
    }

    @ExceptionHandler(ShoppingMerchantNotFoundException::class)
    fun handleMerchantNotFound(ex: ShoppingMerchantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(OwnMerchantException::class)
    fun handleOwnMerchant(ex: OwnMerchantException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("OWN_MERCHANT", ex.message ?: "Bad request"))

    @ExceptionHandler(rw.itunda.merchant.MerchantProductNotFoundException::class)
    fun handleProductNotFound(ex: rw.itunda.merchant.MerchantProductNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_PRODUCT_NOT_FOUND", ex.message ?: "Not found"))
}
