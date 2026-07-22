package rw.itunda.merchant

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal

// imageUrl/originalPrice added 2026-07-21 (see MerchantProduct.kt's own doc comment) --
// both optional; discountPercent is deliberately NOT part of this request, it's always
// server-computed from price/originalPrice, never trusted from the client. description
// added 2026-07-21, also optional, backing the new product-detail screen.
data class AddProductRequest(
    val name: String,
    val price: BigDecimal,
    val imageUrl: String? = null,
    val originalPrice: BigDecimal? = null,
    val description: String? = null,
)
data class AddMenuOptionGroupRequest(val name: String, val choices: List<MenuOptionChoiceRequest>)

// Real merchant product-catalog endpoints -- the register-software half of "Toss
// Place" (see MerchantProductService's own doc comment). Not money-moving, so no
// Idempotency-Key requirement -- checkout itself still goes through MerchantController's
// existing /qr/generate and /card/charge, unmodified.
@RestController
@RequestMapping("/api/v1/merchant/products")
class MerchantProductController(
    private val merchantProductService: MerchantProductService,
    private val menuOptionService: MenuOptionService,
) {

    @PostMapping
    fun addProduct(
        @RequestBody request: AddProductRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val product = merchantProductService.addProduct(
            currentUser.userId, request.name, request.price, request.imageUrl, request.originalPrice, request.description,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "product" to product))
    }

    @GetMapping
    fun getCatalog(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "products" to merchantProductService.getCatalog(currentUser.userId)))

    @PutMapping("/{productId}")
    fun updateProduct(
        @PathVariable productId: String,
        @RequestBody request: AddProductRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val product = merchantProductService.updateProduct(
            currentUser.userId, productId, request.name, request.price, request.imageUrl, request.originalPrice, request.description,
        )
        return ResponseEntity.ok(mapOf("success" to true, "product" to product))
    }

    @DeleteMapping("/{productId}")
    fun removeProduct(
        @PathVariable productId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val product = merchantProductService.removeProduct(currentUser.userId, productId)
        return ResponseEntity.ok(mapOf("success" to true, "product" to product))
    }

    // Real menu-item option groups (2026-07-21, v1: required single-select only) -- see
    // MenuOptionService.addOptionGroup's own doc comment for the full account.
    @PostMapping("/{productId}/option-groups")
    fun addOptionGroup(
        @PathVariable productId: String,
        @RequestBody request: AddMenuOptionGroupRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val view = menuOptionService.addOptionGroup(currentUser.userId, productId, request.name, request.choices)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            mapOf("success" to true, "optionGroup" to mapOf("id" to view.group.id, "name" to view.group.name, "choices" to view.choices)),
        )
    }

    // Real public read (owner or buyer -- no ownership gate) -- also folded directly
    // into ShoppingController.getMerchantProducts' own menu-browse payload so a buyer
    // never needs a second round trip per item; this endpoint exists for the owner's own
    // management UI and any direct lookup.
    @GetMapping("/{productId}/option-groups")
    fun getOptionGroups(@PathVariable productId: String): ResponseEntity<Map<String, Any?>> {
        val views = menuOptionService.getOptionGroups(productId)
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "optionGroups" to views.map { mapOf("id" to it.group.id, "name" to it.group.name, "choices" to it.choices) },
            ),
        )
    }

    @DeleteMapping("/{productId}/option-groups/{groupId}")
    fun removeOptionGroup(
        @PathVariable productId: String,
        @PathVariable groupId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        menuOptionService.removeOptionGroup(currentUser.userId, productId, groupId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @ExceptionHandler(MerchantNotFoundException::class)
    fun handleMerchantNotFound(ex: MerchantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidProductPriceException::class)
    fun handleInvalidPrice(ex: InvalidProductPriceException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_PRODUCT_PRICE", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidProductImageUrlException::class)
    fun handleInvalidImageUrl(ex: InvalidProductImageUrlException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_PRODUCT_IMAGE_URL", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidProductDiscountException::class)
    fun handleInvalidDiscount(ex: InvalidProductDiscountException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_PRODUCT_DISCOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(MerchantProductNotFoundException::class)
    fun handleProductNotFound(ex: MerchantProductNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_PRODUCT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidMenuOptionGroupException::class)
    fun handleInvalidMenuOptionGroup(ex: InvalidMenuOptionGroupException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_MENU_OPTION_GROUP", ex.message ?: "Bad request"))

    @ExceptionHandler(MenuOptionGroupNotFoundException::class)
    fun handleMenuOptionGroupNotFound(ex: MenuOptionGroupNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MENU_OPTION_GROUP_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMIT_EXCEEDED", ex.message ?: "Too many requests"))
}
