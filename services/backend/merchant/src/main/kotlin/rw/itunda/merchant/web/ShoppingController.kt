package rw.itunda.merchant.web

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.domain.EatsOrderStatus
import rw.itunda.core.domain.MerchantBusinessType
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.geo.DeliveryEtaEstimator
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.repository.EatsFavoriteRepository
import rw.itunda.core.repository.EatsOrderRepository
import rw.itunda.core.repository.EatsReviewRepository
import rw.itunda.core.repository.MenuOptionChoiceRepository
import rw.itunda.core.repository.MenuOptionGroupRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.ProductPriceTierRepository
import rw.itunda.core.search.FullTextSearchUtil
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import rw.itunda.merchant.ShoppingCashbackService
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ShoppingMerchantNotFoundException(message: String) : RuntimeException(message)

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
    private val eatsReviewRepository: EatsReviewRepository,
    private val eatsFavoriteRepository: EatsFavoriteRepository,
    private val eatsOrderRepository: EatsOrderRepository,
    private val menuOptionGroupRepository: MenuOptionGroupRepository,
    private val menuOptionChoiceRepository: MenuOptionChoiceRepository,
    private val priceTierRepository: ProductPriceTierRepository,
) {
    // Promoted to DeliveryEtaEstimator (2026-08-15) -- EatsOrderService now needs this
    // exact same real formula for an in-flight order's own estimated arrival, not just
    // this controller's pre-order browse-time estimate. See that object's own doc
    // comment.

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
        // every major Korean delivery app has a real 빠른배달순 (fastest-delivery-first)
        // option alongside its default/rating sort. deliveryTimeMinutes was already
        // computed per-merchant below for display, just never sortable. Only takes
        // effect when the caller also supplies real buyerLat/buyerLng -- without a
        // real distance, deliveryTimeMinutes is null for every merchant and there's
        // nothing honest to sort by. A real, in-page sort (not a DB-level ORDER BY --
        // deliveryTimeMinutes is computed from Haversine distance at request time, not
        // a stored column), same discipline `getDishes`'s own `recommended` re-sort
        // already established: never re-fetches, so this page's real pagination/count
        // stays exact.
        //
        // "favorites" added 2026-08-16 -- real Baemin 찜순 (favorite-count) sort, a
        // real, publicly-cited popularity signal on Baemin's own restaurant listings
        // (buyers can sort restaurants by how many people have favorited them).
        // itunda already tracked EatsFavorite per user, just never surfaced or sorted
        // by the aggregate count. Also purely in-page, same reasoning as delivery_time,
        // but unlike delivery_time this one needs no buyer location at all.
        @RequestParam(required = false) sortBy: String?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val page = merchantRepository.search(MerchantStatus.ACTIVE, category?.trim()?.ifBlank { null }, businessType, q?.trim()?.ifBlank { null }, pageable)
        val hasBuyerLocation = buyerLat != null && buyerLng != null && GeoUtils.isValidCoordinate(buyerLat, buyerLng)
        // Real batched rating lookup -- one GROUP BY query for the whole page, not one
        // per-merchant call. See EatsReviewRepository.getRestaurantRatingSummaries's own
        // doc comment.
        val ratingByMerchant = if (page.content.isNotEmpty()) {
            eatsReviewRepository.getRestaurantRatingSummaries(page.content.map { it.id }).associateBy { it.restaurantId }
        } else {
            emptyMap()
        }
        val favoriteCountByMerchant = if (page.content.isNotEmpty()) {
            eatsFavoriteRepository.getFavoriteCounts(page.content.map { it.id }).associate { it.restaurantId to it.count }
        } else {
            emptyMap()
        }
        // Real Uber Eats-style "busy kitchen" signal -- see
        // EatsOrderRepository.getActiveKitchenOrderCounts's own doc comment.
        val kitchenOrderCountByMerchant = if (page.content.isNotEmpty()) {
            eatsOrderRepository.getActiveKitchenOrderCounts(
                page.content.map { it.id },
                listOf(EatsOrderStatus.PLACED, EatsOrderStatus.ACCEPTED, EatsOrderStatus.PREPARING),
            ).associate { it.restaurantId to it.count }
        } else {
            emptyMap()
        }
        data class MerchantRow(val map: Map<String, Any?>, val deliveryTimeMinutes: Int?, val favoriteCount: Long)
        var rows = page.content.map { merchant ->
            val distanceKm = if (hasBuyerLocation && merchant.latitude != null && merchant.longitude != null) {
                GeoUtils.haversineKm(buyerLat!!, buyerLng!!, merchant.latitude!!, merchant.longitude!!)
            } else {
                null
            }
            val rating = ratingByMerchant[merchant.id]
            val isBusy = (kitchenOrderCountByMerchant[merchant.id] ?: 0L) >= DeliveryEtaEstimator.BUSY_ORDER_THRESHOLD
            val deliveryTimeMinutes = distanceKm?.let { DeliveryEtaEstimator.estimateDeliveryMinutes(it, merchant.avgPrepTimeMinutes, isBusy) }
            val favoriteCount = favoriteCountByMerchant[merchant.id] ?: 0L
            MerchantRow(
                mapOf(
                    "merchantId" to merchant.id,
                    "businessName" to merchant.businessName,
                    "category" to merchant.category,
                    "cashbackRate" to "1%",
                    // Real Baemin 찜 (favorites) count (2026-08-16) -- see
                    // EatsFavoriteRepository.getFavoriteCounts's own doc comment.
                    "favoriteCount" to favoriteCount,
                    // Real Baemin CEO app 영업일시중지 (temporarily pause business) --
                    // see Merchant.isAcceptingOrders's own doc comment. A buyer needs
                    // this surfaced on the browse card itself, not just discovered as a
                    // real 400 after trying to check out.
                    "isAcceptingOrders" to merchant.isAcceptingOrders,
                    // Real Uber Eats-style "busy kitchen" signal (2026-08-16) -- see
                    // EatsOrderRepository.getActiveKitchenOrderCounts's own doc comment.
                    // deliveryTimeMinutes above already includes this restaurant's real
                    // delay bump when true; this flag is what drives a real UI badge
                    // explaining WHY the estimate is longer than usual.
                    "isBusy" to isBusy,
                    // Real optional location (2026-07-19) -- lets a real map view plot real
                    // merchants, same field already set via POST /api/v1/merchant/location for
                    // Eats' distance-based delivery fee. Null for a merchant that hasn't set one.
                    "latitude" to merchant.latitude,
                    "longitude" to merchant.longitude,
                    "photoUrl" to merchant.photoUrl,
                    "minOrderAmount" to merchant.minOrderAmount,
                    "rating" to rating?.average,
                    "reviewCount" to (rating?.count ?: 0L),
                    "distanceKm" to distanceKm?.let { BigDecimal(it).setScale(2, RoundingMode.HALF_UP) },
                    "deliveryTimeMinutes" to deliveryTimeMinutes,
                    // Real 단건배달 (single-order delivery) guarantee (2026-07-26) -- see
                    // EatsOrderService.claimDelivery's own doc comment. Universally true,
                    // not a per-merchant toggle: enforced at claim time for every real
                    // itunda delivery, the same real Coupang Eats/배민1 distinction
                    // docs/DESIGN_REFERENCES.md named.
                    "singleOrderDelivery" to true,
                    // Real merchant-set phone/hours (2026-08-09) -- see Merchant.kt's own
                    // doc comment. Null unless the merchant has actually set one.
                    "phoneNumber" to merchant.phoneNumber,
                    "openingHours" to merchant.openingHours,
                ),
                deliveryTimeMinutes,
                favoriteCount,
            )
        }
        // sortedBy is stable, so ties (or every row when no buyer location was supplied,
        // deliveryTimeMinutes null for all) keep the query's own existing order.
        // nullsLast: a merchant with no real distance/prep-time data sorts after every
        // merchant this sort CAN honestly rank, never fabricated to the front or back.
        if (sortBy == "delivery_time") {
            rows = rows.sortedWith(compareBy(nullsLast()) { it.deliveryTimeMinutes })
        } else if (sortBy == "favorites") {
            rows = rows.sortedByDescending { it.favoriteCount }
        }
        val merchants = rows.map { it.map }
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
                // durationMinutes -- real fix, 2026-07-25: this hand-built response map
                // never included it since the 2026-07-25 booking feature was added, which
                // meant the real "Book" action on this exact browse endpoint's data could
                // never actually appear for a customer -- the same class of regression the
                // imageUrl/originalPrice comment above already documents happening once on
                // this same endpoint. Caught while adding priceTiers below, not a new gap.
                "durationMinutes" to product.durationMinutes,
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
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val trimmed = q.trim()
        val booleanQuery = FullTextSearchUtil.toBooleanModeQuery(trimmed)
        val page = if (booleanQuery != null) {
            merchantProductRepository.searchFullText(MerchantStatus.ACTIVE, booleanQuery, pageable)
        } else {
            merchantProductRepository.search(MerchantStatus.ACTIVE, trimmed, pageable)
        }
        // Batch-resolved, same no-N+1 discipline as ProductFavoriteService.getMyFavorites.
        val merchantNames = merchantRepository.findAllById(page.content.map { it.merchantId }.distinct()).associate { it.id to it.businessName }
        val products = page.content.map { p ->
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
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val page = merchantProductRepository.findDeals(MerchantStatus.ACTIVE, pageable)
        val merchantNames = merchantRepository.findAllById(page.content.map { it.merchantId }.distinct()).associate { it.id to it.businessName }
        val products = page.content.map { p ->
            mapOf(
                "id" to p.id, "merchantId" to p.merchantId, "merchantName" to (merchantNames[p.merchantId] ?: ""),
                "name" to p.name, "price" to p.price,
                "imageUrl" to p.imageUrl, "originalPrice" to p.originalPrice, "discountPercent" to p.discountPercent,
                "description" to p.description,
                "stockQuantity" to p.stockQuantity,
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

    @ExceptionHandler(ShoppingMerchantNotFoundException::class)
    fun handleMerchantNotFound(ex: ShoppingMerchantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_NOT_FOUND", ex.message ?: "Not found"))
}
