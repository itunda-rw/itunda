package rw.itunda.merchant

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.net.URISyntaxException
import java.time.Duration
import java.util.UUID

class InvalidProductPriceException(message: String) : RuntimeException(message)
class MerchantProductNotFoundException(message: String) : RuntimeException(message)
class InvalidProductImageUrlException(message: String) : RuntimeException(message)
class InvalidProductDiscountException(message: String) : RuntimeException(message)
class InvalidProductDurationException(message: String) : RuntimeException(message)

/**
 * A real merchant product catalog -- the register-software half of the "Toss Place"
 * gap (see MerchantProduct.kt's own doc comment for the full account). Deliberately
 * simple: a name and a price, exactly what a real cash-register catalog needs to build
 * a cart total; the actual checkout still goes through MerchantService's already-real
 * `generateQr`/`chargeCard`, unmodified -- this service never touches money movement.
 */
@Service
class MerchantProductService(
    private val merchantRepository: MerchantRepository,
    private val merchantProductRepository: MerchantProductRepository,
    private val rateLimiter: RateLimiter,
) {
    private fun getMyMerchant(ownerUserId: String) =
        merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw MerchantNotFoundException("This account is not registered as a merchant")

    // Real, minimal image-URL validation (2026-07-21) -- this backend has no
    // file-upload/storage layer anywhere (confirmed by repo-wide search before adding
    // this field), so a merchant provides a real, already-publicly-hosted image URL
    // rather than uploading a file. This is an honest "bring your own URL" v1, not a
    // fake upload pipeline dressed up to look real. Blank/absent is allowed (no image is
    // a valid, real state); a non-blank value must at least parse as a real http(s) URL.
    private fun validateImageUrl(imageUrl: String?): String? {
        val trimmed = imageUrl?.trim()?.ifBlank { null } ?: return null
        if (trimmed.length > 2048) {
            throw InvalidProductImageUrlException("Image URL is too long")
        }
        val uri = try {
            URI(trimmed)
        } catch (e: URISyntaxException) {
            throw InvalidProductImageUrlException("Image URL is not a valid URL")
        }
        if (uri.scheme != "http" && uri.scheme != "https") {
            throw InvalidProductImageUrlException("Image URL must start with http:// or https://")
        }
        return trimmed
    }

    // Real discount-percent computation (2026-07-21) -- deliberately never accepted
    // directly from the client (see MerchantProduct.kt's own doc comment for why):
    // computed here, server-side, from price/originalPrice at write time so it can never
    // drift from the two real numbers it's derived from. Null originalPrice means "no
    // discount," a real, valid state, not an error.
    private fun computeDiscountPercent(price: BigDecimal, originalPrice: BigDecimal?): Int? {
        if (originalPrice == null) return null
        if (originalPrice <= price) {
            throw InvalidProductDiscountException("Original price must be greater than the current price")
        }
        return originalPrice.subtract(price)
            .multiply(BigDecimal(100))
            .divide(originalPrice, 0, RoundingMode.HALF_UP)
            .toInt()
    }

    // Real, minimal description bounding (2026-07-21) -- this DB genuinely runs
    // STRICT_TRANS_TABLES (confirmed live, see ProductReviewService.submitReview's own
    // doc comment for the exact same real crash risk on an over-length insert), so this
    // trims and hard-caps at the column's own 2000-char limit rather than letting an
    // over-long value throw a raw DataIntegrityViolationException. Blank/absent is a
    // valid, real "no description" state, not an error.
    private fun validateDescription(description: String?): String? =
        description?.trim()?.ifBlank { null }?.take(2000)

    // Real bookable-service duration validation (2026-07-25) -- see MerchantProduct.kt's
    // own doc comment. Bounded to a real, sane appointment length (5 min .. 8 hours);
    // null stays null, a real, valid "not bookable" state, not an error.
    private fun validateDuration(durationMinutes: Int?): Int? {
        if (durationMinutes == null) return null
        if (durationMinutes < 5 || durationMinutes > 480) {
            throw InvalidProductDurationException("Duration must be between 5 and 480 minutes")
        }
        return durationMinutes
    }

    @Transactional
    fun addProduct(
        ownerUserId: String,
        name: String,
        price: BigDecimal,
        imageUrl: String? = null,
        originalPrice: BigDecimal? = null,
        description: String? = null,
        durationMinutes: Int? = null,
    ): MerchantProduct {
        // Real anti-spam limit -- found missing in a 2026-07-19 security sweep. Not
        // money-moving (deliberately no Idempotency-Key, per the controller's own doc
        // comment), but that decision left free, unbounded product-row creation once a
        // caller is merchant-registered with zero protection of any kind.
        rateLimiter.checkLimit("merchant:product:$ownerUserId", limit = 30, window = Duration.ofHours(1))
        val merchant = getMyMerchant(ownerUserId)
        if (price <= BigDecimal.ZERO) {
            throw InvalidProductPriceException("Price must be greater than zero")
        }
        val validatedImageUrl = validateImageUrl(imageUrl)
        val discountPercent = computeDiscountPercent(price, originalPrice)
        val product = MerchantProduct(
            id = "merchant_product_${UUID.randomUUID()}",
            merchantId = merchant.id,
            name = name,
            price = price,
            imageUrl = validatedImageUrl,
            originalPrice = originalPrice,
            discountPercent = discountPercent,
            description = validateDescription(description),
            durationMinutes = validateDuration(durationMinutes),
        )
        return merchantProductRepository.save(product)
    }

    fun getCatalog(ownerUserId: String): List<MerchantProduct> {
        val merchant = getMyMerchant(ownerUserId)
        return merchantProductRepository.findByMerchantIdAndActiveTrue(merchant.id)
    }

    @Transactional
    fun updateProduct(
        ownerUserId: String,
        productId: String,
        name: String,
        price: BigDecimal,
        imageUrl: String? = null,
        originalPrice: BigDecimal? = null,
        description: String? = null,
        durationMinutes: Int? = null,
    ): MerchantProduct {
        val merchant = getMyMerchant(ownerUserId)
        if (price <= BigDecimal.ZERO) {
            throw InvalidProductPriceException("Price must be greater than zero")
        }
        val validatedImageUrl = validateImageUrl(imageUrl)
        val discountPercent = computeDiscountPercent(price, originalPrice)
        val product = merchantProductRepository.findById(productId)
            .orElseThrow { MerchantProductNotFoundException("Product not found") }
        if (product.merchantId != merchant.id) {
            throw MerchantProductNotFoundException("Product not found")
        }
        product.name = name
        product.price = price
        product.imageUrl = validatedImageUrl
        product.originalPrice = originalPrice
        product.discountPercent = discountPercent
        product.description = validateDescription(description)
        product.durationMinutes = validateDuration(durationMinutes)
        return merchantProductRepository.save(product)
    }

    @Transactional
    fun removeProduct(ownerUserId: String, productId: String): MerchantProduct {
        val merchant = getMyMerchant(ownerUserId)
        val product = merchantProductRepository.findById(productId)
            .orElseThrow { MerchantProductNotFoundException("Product not found") }
        if (product.merchantId != merchant.id) {
            throw MerchantProductNotFoundException("Product not found")
        }
        product.active = false
        return merchantProductRepository.save(product)
    }
}
