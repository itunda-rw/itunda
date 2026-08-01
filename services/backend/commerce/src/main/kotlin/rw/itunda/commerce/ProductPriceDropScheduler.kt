package rw.itunda.commerce

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real Naver Shopping 가격 변동 알림 (price-drop alert, item 227) -- see
 * `ProductFavorite.kt`'s own doc comment for the full sourced account: a user
 * favorites a product, and gets notified when its real price drops below what it was
 * the last time this scheduler checked. Same "poll for due rows, act, done" shape as
 * `GiftExpiryScheduler`/`MarketplaceEscrowAutoReleaseScheduler` -- the poll interval
 * below is demo-speed on purpose, matching every other scheduler in this codebase.
 * Resilient per-favorite: one bad row never blocks the sweep for every other real due
 * favorite.
 */
@Component
class ProductPriceDropScheduler(private val productFavoriteService: ProductFavoriteService) {
    private val log = LoggerFactory.getLogger(ProductPriceDropScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        val due = productFavoriteService.getFavoritesWithPriceDrops()
        for (favorite in due) {
            try {
                productFavoriteService.notifyPriceDrop(favorite.id)
                log.info("Notified user {} of a price drop on product {}", favorite.userId, favorite.productId)
            } catch (e: Exception) {
                log.error("Price-drop notification failed for favorite {}", favorite.id, e)
            }
        }
    }
}
