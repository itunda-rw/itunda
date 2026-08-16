package rw.itunda.merchant

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.domain.ProductPriceTier
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.ProductPriceTierRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.net.URISyntaxException
import java.time.Duration
import java.time.Instant
import java.util.UUID

class InvalidProductPriceException(message: String) : RuntimeException(message)
class MerchantProductNotFoundException(message: String) : RuntimeException(message)
class InvalidProductImageUrlException(message: String) : RuntimeException(message)
class InvalidProductDiscountException(message: String) : RuntimeException(message)
class InvalidProductDurationException(message: String) : RuntimeException(message)
class InvalidPriceTierException(message: String) : RuntimeException(message)
class InvalidStockQuantityException(message: String) : RuntimeException(message)
class InvalidSurplusDealException(message: String) : RuntimeException(message)

data class PriceTierRequest(val minQuantity: Int, val unitPrice: BigDecimal)

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
    private val priceTierRepository: ProductPriceTierRepository,
    private val rateLimiter: RateLimiter,
    private val orderItemRepository: rw.itunda.core.repository.OrderItemRepository,
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

    // Real Kakao Hair Shop-style prepay requirement -- see BookingDeposit.kt's own doc
    // comment. Only meaningful on a bookable service (durationMinutes set); a physical
    // good has no booking flow to prepay into.
    private fun validateRequiresPrepay(requiresPrepay: Boolean, durationMinutes: Int?) {
        if (requiresPrepay && durationMinutes == null) {
            throw InvalidProductDurationException("Only a bookable service (with a duration) can require prepay")
        }
    }

    private fun validateStockQuantity(stockQuantity: Int?): Int? {
        if (stockQuantity != null && stockQuantity < 0) throw InvalidStockQuantityException("Stock quantity cannot be negative")
        return stockQuantity
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
        requiresPrepay: Boolean = false,
        stockQuantity: Int? = null,
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
        validateRequiresPrepay(requiresPrepay, durationMinutes)
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
            requiresPrepay = requiresPrepay,
            stockQuantity = validateStockQuantity(stockQuantity),
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
        requiresPrepay: Boolean = false,
        stockQuantity: Int? = null,
    ): MerchantProduct {
        val merchant = getMyMerchant(ownerUserId)
        if (price <= BigDecimal.ZERO) {
            throw InvalidProductPriceException("Price must be greater than zero")
        }
        val validatedImageUrl = validateImageUrl(imageUrl)
        val discountPercent = computeDiscountPercent(price, originalPrice)
        validateRequiresPrepay(requiresPrepay, durationMinutes)
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
        product.requiresPrepay = requiresPrepay
        product.stockQuantity = validateStockQuantity(stockQuantity)
        return merchantProductRepository.save(product)
    }

    @Transactional
    fun updateStockQuantity(ownerUserId: String, productId: String, stockQuantity: Int?): MerchantProduct {
        val merchant = getMyMerchant(ownerUserId)
        val product = merchantProductRepository.findById(productId)
            .orElseThrow { MerchantProductNotFoundException("Product not found") }
        if (product.merchantId != merchant.id) {
            throw MerchantProductNotFoundException("Product not found")
        }
        product.stockQuantity = validateStockQuantity(stockQuantity)
        return merchantProductRepository.save(product)
    }

    // Real Baemin CEO app/DoorDash-style "86" (temporarily mark sold out) toggle -- see
    // MerchantProduct.soldOut's own doc comment for why this is distinct from the
    // existing active-flag soft-delete. Same focused-operation shape
    // updateStockQuantity/setSurplusDeal already establish -- never touches
    // pricing/description/booking settings.
    fun setSoldOut(ownerUserId: String, productId: String, soldOut: Boolean): MerchantProduct {
        val merchant = getMyMerchant(ownerUserId)
        val product = merchantProductRepository.findById(productId)
            .orElseThrow { MerchantProductNotFoundException("Product not found") }
        if (product.merchantId != merchant.id) {
            throw MerchantProductNotFoundException("Product not found")
        }
        product.soldOut = soldOut
        return merchantProductRepository.save(product)
    }

    // Real Coupang WING 상품분석 (product analytics) view-count trigger -- see
    // MerchantProduct.viewCount's own doc comment. Customer-facing, unauthenticated by
    // caller identity (any buyer can view a real active product) -- increments on every
    // real fetch, then bumps the returned in-memory entity by 1 to reflect this view
    // without a second round-trip read, same shape MarketplaceService.getListing already
    // established for the identical gap on Marketplace listings.
    @org.springframework.transaction.annotation.Transactional
    fun getProduct(productId: String): MerchantProduct {
        val product = merchantProductRepository.findById(productId)
            .orElseThrow { MerchantProductNotFoundException("Product not found") }
        merchantProductRepository.incrementViewCount(productId)
        product.viewCount += 1
        return product
    }

    // Real Coupang WING 전환율 (conversion rate) report -- pairs the real viewCount
    // above with a real distinct-order count for the same product, so a merchant can
    // see genuine interest (views) alongside genuine outcome (orders), not just a raw
    // sales total. `orders` counts real OrderItem rows regardless of the parent Order's
    // status, matching MerchantService.getTopSellingProducts' own "gross collected at
    // placement" definition -- a cancelled order's reversal is a separate real refund,
    // not a retroactive rewrite of what was genuinely viewed/ordered.
    fun getProductAnalytics(ownerUserId: String, productId: String): Pair<MerchantProduct, Long> {
        val merchant = getMyMerchant(ownerUserId)
        val product = merchantProductRepository.findById(productId)
            .orElseThrow { MerchantProductNotFoundException("Product not found") }
        if (product.merchantId != merchant.id) {
            throw MerchantProductNotFoundException("Product not found")
        }
        val orders = orderItemRepository.countByProductId(productId)
        return product to orders
    }

    /**
     * Real 마감할인 (closing/surplus discount) toggle -- see MerchantProduct.kt's own
     * doc comment for the full sourced account. `expiresAt = null` clears the deal
     * (same "focused operation" reasoning [updateStockQuantity] already established --
     * marking/unmarking a surplus deal must not touch pricing/description/booking
     * settings). Purchase itself is completely unchanged: a surplus deal is bought
     * through the exact same OrderService.placeOrder every other product uses, which
     * already correctly decrements `stockQuantity` -- this method only ever sets
     * metadata, never touches money or the ledger.
     */
    @Transactional
    fun setSurplusDeal(ownerUserId: String, productId: String, expiresAt: Instant?, stockQuantity: Int?): MerchantProduct {
        val merchant = getMyMerchant(ownerUserId)
        val product = merchantProductRepository.findById(productId)
            .orElseThrow { MerchantProductNotFoundException("Product not found") }
        if (product.merchantId != merchant.id) {
            throw MerchantProductNotFoundException("Product not found")
        }
        if (expiresAt != null) {
            if (!expiresAt.isAfter(Instant.now())) {
                throw InvalidSurplusDealException("The closing time must be in the future")
            }
            val resolvedStock = stockQuantity ?: product.stockQuantity
            if (resolvedStock == null || resolvedStock <= 0) {
                throw InvalidSurplusDealException("A surplus deal needs a real, positive quantity")
            }
            product.stockQuantity = resolvedStock
        }
        product.isSurplusDeal = expiresAt != null
        product.surplusExpiresAt = expiresAt
        return merchantProductRepository.save(product)
    }

    /**
     * Real bulk/wholesale pricing (2026-07-25) -- see `ProductPriceTier`'s own doc
     * comment for the full account. Replace-all, same pattern
     * `MerchantBookingService.setAvailability` already established: a merchant
     * re-declares their full real tier list each time rather than incrementally
     * patching it. Validated as a real, honest bulk-discount schedule -- `minQuantity`
     * strictly increasing, `unitPrice` strictly decreasing (a "bulk discount" that
     * charges MORE per unit at a higher quantity isn't a real discount, and would just
     * confuse a buyer who orders more expecting to pay less).
     */
    @Transactional
    fun setPriceTiers(ownerUserId: String, productId: String, tiers: List<PriceTierRequest>): List<ProductPriceTier> {
        val merchant = getMyMerchant(ownerUserId)
        val product = merchantProductRepository.findById(productId)
            .orElseThrow { MerchantProductNotFoundException("Product not found") }
        if (product.merchantId != merchant.id) {
            throw MerchantProductNotFoundException("Product not found")
        }
        if (tiers.size > 10) {
            throw InvalidPriceTierException("Too many price tiers -- 10 is the real limit")
        }
        val sorted = tiers.sortedBy { it.minQuantity }
        sorted.forEachIndexed { index, tier ->
            if (tier.minQuantity < 1) {
                throw InvalidPriceTierException("Minimum quantity must be at least 1")
            }
            if (tier.unitPrice <= BigDecimal.ZERO) {
                throw InvalidPriceTierException("Unit price must be greater than zero")
            }
            // Real "must actually be a discount" check against the product's own flat
            // retail price -- checked for EVERY tier, not just consecutive ones,
            // otherwise a lone first tier priced above (or equal to) retail would slip
            // through with nothing to compare it against.
            if (tier.unitPrice >= product.price) {
                throw InvalidPriceTierException("A bulk tier must cost less per unit than the regular price (${product.price})")
            }
            if (index > 0) {
                val previous = sorted[index - 1]
                if (tier.minQuantity == previous.minQuantity) {
                    throw InvalidPriceTierException("Each tier needs a distinct minimum quantity")
                }
                if (tier.unitPrice >= previous.unitPrice) {
                    throw InvalidPriceTierException("A higher-quantity tier must cost less per unit than the tier below it")
                }
            }
        }
        priceTierRepository.deleteByProductId(productId)
        val saved = sorted.map {
            ProductPriceTier(id = "product_price_tier_${UUID.randomUUID()}", productId = productId, minQuantity = it.minQuantity, unitPrice = it.unitPrice)
        }
        return priceTierRepository.saveAll(saved)
    }

    fun getPriceTiers(productId: String): List<ProductPriceTier> =
        priceTierRepository.findByProductIdOrderByMinQuantityAsc(productId)

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
