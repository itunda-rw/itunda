package rw.itunda.merchant

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID

class InvalidProductPriceException(message: String) : RuntimeException(message)
class MerchantProductNotFoundException(message: String) : RuntimeException(message)

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

    @Transactional
    fun addProduct(ownerUserId: String, name: String, price: BigDecimal): MerchantProduct {
        // Real anti-spam limit -- found missing in a 2026-07-19 security sweep. Not
        // money-moving (deliberately no Idempotency-Key, per the controller's own doc
        // comment), but that decision left free, unbounded product-row creation once a
        // caller is merchant-registered with zero protection of any kind.
        rateLimiter.checkLimit("merchant:product:$ownerUserId", limit = 30, window = Duration.ofHours(1))
        val merchant = getMyMerchant(ownerUserId)
        if (price <= BigDecimal.ZERO) {
            throw InvalidProductPriceException("Price must be greater than zero")
        }
        val product = MerchantProduct(
            id = "merchant_product_${UUID.randomUUID()}",
            merchantId = merchant.id,
            name = name,
            price = price,
        )
        return merchantProductRepository.save(product)
    }

    fun getCatalog(ownerUserId: String): List<MerchantProduct> {
        val merchant = getMyMerchant(ownerUserId)
        return merchantProductRepository.findByMerchantIdAndActiveTrue(merchant.id)
    }

    @Transactional
    fun updateProduct(ownerUserId: String, productId: String, name: String, price: BigDecimal): MerchantProduct {
        val merchant = getMyMerchant(ownerUserId)
        if (price <= BigDecimal.ZERO) {
            throw InvalidProductPriceException("Price must be greater than zero")
        }
        val product = merchantProductRepository.findById(productId)
            .orElseThrow { MerchantProductNotFoundException("Product not found") }
        if (product.merchantId != merchant.id) {
            throw MerchantProductNotFoundException("Product not found")
        }
        product.name = name
        product.price = price
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
