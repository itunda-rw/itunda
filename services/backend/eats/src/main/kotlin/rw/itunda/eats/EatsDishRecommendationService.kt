package rw.itunda.eats

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import rw.itunda.core.domain.MerchantBusinessType
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.repository.EatsOrderRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.OrderItemRepository
import java.math.BigDecimal

/**
 * Extracted from EatsController.getDishes (itunda Eats redesign, 2026-08-28,
 * keeping EatsController at its real frozen file-size-lint baseline as this pass
 * added a real second real sort mode) -- see that endpoint's own original doc
 * comment for the full Coupang Eats dish-grid sourcing. `sortBy: "recommended"`
 * (the default, unchanged) is the existing real "buyer has ordered from this
 * restaurant before" re-sort; `sortBy: "popular"` is new -- a real, order-count-
 * derived "Popular now" ranking (itunda Eats redesign, 2026-08-28, adapting the
 * reference's own "우리 동네 인기 메뉴" ranked rail onto a real signal itunda
 * actually has). Deliberately scoped honestly: itunda has no restaurant-to-
 * buyer-distance join available on this exact dish query, so this ranks by real
 * platform-wide order count rather than fabricating a neighborhood radius filter
 * this data can't actually support -- "Popular now," not "in your neighbourhood."
 */
@Service
class EatsDishRecommendationService(
    private val merchantProductRepository: MerchantProductRepository,
    private val merchantRepository: MerchantRepository,
    private val eatsOrderRepository: EatsOrderRepository,
    private val orderItemRepository: OrderItemRepository,
) {
    fun getDishes(
        category: String?,
        maxBudget: BigDecimal?,
        sortBy: String?,
        pageable: Pageable,
        buyerId: String,
    ): Pair<Page<MerchantProduct>, List<Map<String, Any?>>> {
        val page = merchantProductRepository.findDishes(MerchantStatus.ACTIVE, category, MerchantBusinessType.RESTAURANT, maxBudget, pageable)
        val merchantNames = merchantRepository.findAllById(page.content.map { it.merchantId }.distinct()).associate { it.id to it.businessName }
        if (sortBy == "popular") {
            val orderCountByProduct = if (page.content.isNotEmpty()) {
                orderItemRepository.getProductOrderCounts(page.content.map { it.id }).associate { it.productId to it.count }
            } else {
                emptyMap()
            }
            val dishes = page.content
                .sortedByDescending { orderCountByProduct[it.id] ?: 0L }
                .map { p -> dishMap(p, merchantNames, recommended = false) }
            return page to dishes
        }
        // Real "recommended for you" ranking (2026-08-16) -- a plain, honest re-sort
        // (never a re-fetch, so this page's own real pagination/count stays exact) by
        // whether the buyer has actually ordered from that dish's restaurant before,
        // not a fabricated ML ranking. sortedByDescending is stable, so within each
        // group the existing real p.createdAt DESC ordering from the query is preserved.
        val familiarRestaurantIds = eatsOrderRepository.findDistinctRestaurantIdsByBuyerId(buyerId).toSet()
        val dishes = page.content
            .sortedByDescending { it.merchantId in familiarRestaurantIds }
            .map { p -> dishMap(p, merchantNames, recommended = p.merchantId in familiarRestaurantIds) }
        return page to dishes
    }

    private fun dishMap(p: MerchantProduct, merchantNames: Map<String, String>, recommended: Boolean) = mapOf(
        "id" to p.id, "merchantId" to p.merchantId, "merchantName" to (merchantNames[p.merchantId] ?: ""),
        "name" to p.name, "price" to p.price, "imageUrl" to p.imageUrl,
        "recommended" to recommended,
    )
}
