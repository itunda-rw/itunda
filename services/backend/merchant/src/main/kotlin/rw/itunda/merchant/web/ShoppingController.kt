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
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta

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
) {

    // Real category/search filter (2026-07-19) -- both params optional and
    // independently combinable, backing restaurant categories + search/filter for Eats
    // (this same endpoint is also Shopping's own merchant browse, so both surfaces get
    // it for free). Blank query params are treated as absent rather than an empty-string
    // match, since `?category=` from an unset UI filter shouldn't behave differently
    // from omitting it entirely.
    @GetMapping("/merchants")
    fun getEligibleMerchants(
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) q: String?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Map<String, Any?>> {
        val page = merchantRepository.search(MerchantStatus.ACTIVE, category?.trim()?.ifBlank { null }, q?.trim()?.ifBlank { null }, pageable)
        val merchants = page.content.map { merchant ->
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
    @GetMapping("/merchants/{merchantId}/products")
    fun getMerchantProducts(@PathVariable merchantId: String): ResponseEntity<Map<String, Any?>> {
        val merchant = merchantRepository.findById(merchantId)
            .orElseThrow { ShoppingMerchantNotFoundException("Merchant not found") }
        val products = merchantProductRepository.findByMerchantIdAndActiveTrue(merchant.id)
        return ResponseEntity.ok(mapOf("success" to true, "merchant" to mapOf("id" to merchant.id, "businessName" to merchant.businessName), "products" to products))
    }

    @ExceptionHandler(ShoppingMerchantNotFoundException::class)
    fun handleMerchantNotFound(ex: ShoppingMerchantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_NOT_FOUND", ex.message ?: "Not found"))
}
