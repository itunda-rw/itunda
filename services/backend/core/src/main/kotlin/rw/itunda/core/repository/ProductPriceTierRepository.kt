package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.ProductPriceTier

interface ProductPriceTierRepository : JpaRepository<ProductPriceTier, String> {
    fun findByProductIdOrderByMinQuantityAsc(productId: String): List<ProductPriceTier>

    // Real batch fetch (2026-07-25) -- same N+1-avoidance discipline
    // EatsOrderService's own menu-options resolution already established: one query for
    // every distinct product in a real multi-item order, not one per item.
    fun findByProductIdInOrderByMinQuantityAsc(productIds: List<String>): List<ProductPriceTier>

    fun deleteByProductId(productId: String)
}
