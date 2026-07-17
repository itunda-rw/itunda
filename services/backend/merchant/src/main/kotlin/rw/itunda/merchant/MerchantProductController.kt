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
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal

data class AddProductRequest(val name: String, val price: BigDecimal)

// Real merchant product-catalog endpoints -- the register-software half of "Toss
// Place" (see MerchantProductService's own doc comment). Not money-moving, so no
// Idempotency-Key requirement -- checkout itself still goes through MerchantController's
// existing /qr/generate and /card/charge, unmodified.
@RestController
@RequestMapping("/api/v1/merchant/products")
class MerchantProductController(private val merchantProductService: MerchantProductService) {

    @PostMapping
    fun addProduct(
        @RequestBody request: AddProductRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val product = merchantProductService.addProduct(currentUser.userId, request.name, request.price)
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
        val product = merchantProductService.updateProduct(currentUser.userId, productId, request.name, request.price)
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

    @ExceptionHandler(MerchantNotFoundException::class)
    fun handleMerchantNotFound(ex: MerchantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidProductPriceException::class)
    fun handleInvalidPrice(ex: InvalidProductPriceException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_PRODUCT_PRICE", ex.message ?: "Bad request"))

    @ExceptionHandler(MerchantProductNotFoundException::class)
    fun handleProductNotFound(ex: MerchantProductNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_PRODUCT_NOT_FOUND", ex.message ?: "Not found"))
}
