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
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.repository.EatsReviewRepository
import rw.itunda.core.repository.MenuOptionChoiceRepository
import rw.itunda.core.repository.MenuOptionGroupRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import java.math.BigDecimal
import java.math.RoundingMode

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
    private val menuOptionGroupRepository: MenuOptionGroupRepository,
    private val menuOptionChoiceRepository: MenuOptionChoiceRepository,
) {
    companion object {
        // Real, labeled ESTIMATE (2026-07-21) -- not measured historical delivery time
        // (this backend has never recorded one), same "computed from real distance, never
        // fabricated" discipline EatsOrderService.computeDeliveryFee already established
        // for the delivery fee itself. A real kitchen-prep floor plus a real
        // distance/speed travel estimate, rounded to the nearest 5 minutes the way every
        // real delivery app's ETA badge is displayed.
        private const val BASE_PREP_MINUTES = 15.0
        private const val ASSUMED_AVG_SPEED_KMH = 20.0
        private const val MIN_DELIVERY_MINUTES = 15
        private const val MAX_DELIVERY_MINUTES = 90
    }

    private fun estimateDeliveryMinutes(distanceKm: Double): Int {
        val travelMinutes = (distanceKm / ASSUMED_AVG_SPEED_KMH) * 60.0
        val total = BASE_PREP_MINUTES + travelMinutes
        val rounded = (Math.round(total / 5.0) * 5).toInt()
        return rounded.coerceIn(MIN_DELIVERY_MINUTES, MAX_DELIVERY_MINUTES)
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
    @GetMapping("/merchants")
    fun getEligibleMerchants(
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) buyerLat: Double?,
        @RequestParam(required = false) buyerLng: Double?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val page = merchantRepository.search(MerchantStatus.ACTIVE, category?.trim()?.ifBlank { null }, q?.trim()?.ifBlank { null }, pageable)
        val hasBuyerLocation = buyerLat != null && buyerLng != null && GeoUtils.isValidCoordinate(buyerLat, buyerLng)
        // Real batched rating lookup -- one GROUP BY query for the whole page, not one
        // per-merchant call. See EatsReviewRepository.getRestaurantRatingSummaries's own
        // doc comment.
        val ratingByMerchant = if (page.content.isNotEmpty()) {
            eatsReviewRepository.getRestaurantRatingSummaries(page.content.map { it.id }).associateBy { it.restaurantId }
        } else {
            emptyMap()
        }
        val merchants = page.content.map { merchant ->
            val distanceKm = if (hasBuyerLocation && merchant.latitude != null && merchant.longitude != null) {
                GeoUtils.haversineKm(buyerLat!!, buyerLng!!, merchant.latitude!!, merchant.longitude!!)
            } else {
                null
            }
            val rating = ratingByMerchant[merchant.id]
            mapOf(
                "merchantId" to merchant.id,
                "businessName" to merchant.businessName,
                "category" to merchant.category,
                "cashbackRate" to "1%",
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
                "deliveryTimeMinutes" to distanceKm?.let { estimateDeliveryMinutes(it) },
            )
        }
        return ResponseEntity.ok(mapOf("success" to true, "merchants" to merchants) + pageMeta(page))
    }

    // Real distinct category list -- see MerchantRepository.findDistinctCategories's own
    // doc comment for why this is derived from real merchant data, not a hardcoded list.
    @GetMapping("/merchants/categories")
    fun getCategories(): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "categories" to merchantRepository.findDistinctCategories(MerchantStatus.ACTIVE)))

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
        val enrichedProducts = products.map { product ->
            mapOf(
                "id" to product.id,
                "merchantId" to product.merchantId,
                "name" to product.name,
                "price" to product.price,
                "active" to product.active,
                "createdAt" to product.createdAt,
                "optionGroups" to (groupsByProduct[product.id] ?: emptyList()).map { group ->
                    mapOf(
                        "id" to group.id,
                        "name" to group.name,
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
    @GetMapping("/products/search")
    fun searchProducts(
        @RequestParam q: String,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val page = merchantProductRepository.search(MerchantStatus.ACTIVE, q.trim(), pageable)
        // Batch-resolved, same no-N+1 discipline as ProductFavoriteService.getMyFavorites.
        val merchantNames = merchantRepository.findAllById(page.content.map { it.merchantId }.distinct()).associate { it.id to it.businessName }
        val products = page.content.map { p ->
            mapOf(
                "id" to p.id, "merchantId" to p.merchantId, "merchantName" to (merchantNames[p.merchantId] ?: ""),
                "name" to p.name, "price" to p.price,
                // imageUrl/originalPrice/discountPercent added 2026-07-21 -- see
                // MerchantProduct.kt's own doc comment; search results need the same
                // real product-card fields the per-merchant catalog already exposes.
                "imageUrl" to p.imageUrl, "originalPrice" to p.originalPrice, "discountPercent" to p.discountPercent,
            )
        }
        return ResponseEntity.ok(mapOf("success" to true, "products" to products) + pageMeta(page))
    }

    @ExceptionHandler(ShoppingMerchantNotFoundException::class)
    fun handleMerchantNotFound(ex: ShoppingMerchantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_NOT_FOUND", ex.message ?: "Not found"))
}
