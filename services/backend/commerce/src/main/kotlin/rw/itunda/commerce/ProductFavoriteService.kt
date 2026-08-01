package rw.itunda.commerce

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.ProductFavorite
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.ProductFavoriteRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class FavoriteProductNotFoundException(message: String) : RuntimeException(message)

data class FavoriteProduct(
    val productId: String,
    val merchantId: String,
    val name: String,
    val price: BigDecimal,
    val businessName: String,
    val favoritedAt: Instant,
    // imageUrl/originalPrice/discountPercent added 2026-07-21 -- see
    // MerchantProduct.kt's own doc comment. The wishlist card needs the same real
    // product-card fields the catalog/search views already carry.
    val imageUrl: String? = null,
    val originalPrice: BigDecimal? = null,
    val discountPercent: Int? = null,
    // description added 2026-07-21, backing the new product-detail screen -- see
    // MerchantProduct.kt's own doc comment.
    val description: String? = null,
    // Real Naver Shopping 가격 변동 알림 (price-drop alert, item 227) -- true once the
    // product's real current price has dropped below `ProductFavorite.priceAtLastCheck`
    // and a real push notification has already fired for it. See
    // ProductPriceDropScheduler's own doc comment.
    val priceDropped: Boolean = false,
)

/**
 * Real product wishlist -- the real "찜하기"/wishlist every real Coupang/Naver/Kakao/
 * Toss Shopping-style app has, built at the user's direct request for "real full
 * shopping systems like Coupang, Toss Shopping, Kakao Shopping, Naver Shopping."
 * Mirrors `EatsFavoriteService`'s exact shape and idempotency discipline (see
 * `ProductFavorite.kt`'s own doc comment).
 *
 * `addFavorite` is deliberately idempotent (favoriting an already-favorited product
 * just returns the existing row rather than a 409); `removeFavorite` is a silent no-op
 * for something that was never favorited -- same reasoning `EatsFavoriteService`
 * already established: the end state ("not favorited") is what the caller actually
 * wants, regardless of what state it started in.
 */
@Service
class ProductFavoriteService(
    private val productFavoriteRepository: ProductFavoriteRepository,
    private val merchantProductRepository: MerchantProductRepository,
    private val merchantRepository: MerchantRepository,
    private val pushNotificationService: PushNotificationService,
) {
    @Transactional
    fun addFavorite(userId: String, productId: String): ProductFavorite {
        val product = merchantProductRepository.findById(productId).orElseThrow { FavoriteProductNotFoundException("Product not found") }
        productFavoriteRepository.findByUserIdAndProductId(userId, productId)?.let { return it }
        return productFavoriteRepository.save(
            ProductFavorite(id = "product_favorite_${UUID.randomUUID()}", userId = userId, productId = productId, priceAtLastCheck = product.price),
        )
    }

    @Transactional
    fun removeFavorite(userId: String, productId: String) {
        productFavoriteRepository.deleteByUserIdAndProductId(userId, productId)
    }

    // Real batch-resolve of product + merchant info via two findAllById calls, the same
    // N+1-avoiding shape EatsFavoriteService.getMyFavorites already established.
    fun getMyFavorites(userId: String, pageable: Pageable): Page<FavoriteProduct> {
        val page = productFavoriteRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
        val productsById = merchantProductRepository.findAllById(page.content.map { it.productId }).associateBy { it.id }
        val merchantsById = merchantRepository.findAllById(productsById.values.map { it.merchantId }.distinct()).associateBy { it.id }
        return page.map { favorite ->
            val product = productsById[favorite.productId]
            FavoriteProduct(
                productId = favorite.productId,
                merchantId = product?.merchantId ?: "",
                name = product?.name ?: "Product no longer available",
                price = product?.price ?: BigDecimal.ZERO,
                businessName = product?.let { merchantsById[it.merchantId]?.businessName } ?: "Merchant no longer available",
                favoritedAt = favorite.createdAt,
                imageUrl = product?.imageUrl,
                originalPrice = product?.originalPrice,
                discountPercent = product?.discountPercent,
                description = product?.description,
                // Real, not the raw stored flag -- reflects the CURRENT price against
                // the last-checked baseline, so it stays correct even between
                // scheduler ticks (a drop that hasn't been notified yet still shows
                // here) and clears itself once the price recovers back up.
                priceDropped = product != null && product.price < favorite.priceAtLastCheck,
            )
        }
    }

    /**
     * Real Naver Shopping 가격 변동 알림 (price-drop alert, item 227) -- the real
     * candidate rows `ProductPriceDropScheduler`'s own "poll for due rows, act, done"
     * tick works through, same shape `MarketplaceService.getEscrowsDueForAutoRelease`
     * already establishes for its own scheduler. Real demo-scale `findAll()` sweep,
     * same honest simplification `DesignatedDriverService.getAvailableTrips()`'s own
     * doc comment already establishes for a small open-list scan at this codebase's
     * current data volume.
     */
    fun getFavoritesWithPriceDrops(): List<ProductFavorite> {
        val favorites = productFavoriteRepository.findAll()
        if (favorites.isEmpty()) return emptyList()
        val productsById = merchantProductRepository.findAllById(favorites.map { it.productId }.distinct()).associateBy { it.id }
        return favorites.filter { favorite ->
            val product = productsById[favorite.productId]
            product != null && product.price < favorite.priceAtLastCheck
        }
    }

    /** One real drop notification, called per-row by the scheduler so one bad row
     * (e.g. a since-deleted product) never blocks the sweep for every other real due
     * favorite -- same resilience `MarketplaceEscrowAutoReleaseScheduler`'s own doc
     * comment already establishes. */
    @Transactional
    fun notifyPriceDrop(favoriteId: String) {
        val favorite = productFavoriteRepository.findById(favoriteId).orElseThrow { FavoriteProductNotFoundException("Favorite not found") }
        val product = merchantProductRepository.findById(favorite.productId).orElseThrow { FavoriteProductNotFoundException("Product not found") }
        if (product.price >= favorite.priceAtLastCheck) return
        pushNotificationService.sendToUser(
            favorite.userId,
            "Price dropped!",
            "${product.name} is now ${product.price} (was ${favorite.priceAtLastCheck})",
            mapOf("productId" to product.id),
        )
        favorite.priceAtLastCheck = product.price
        productFavoriteRepository.save(favorite)
    }
}
