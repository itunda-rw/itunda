package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.ProductReview

// Reuses RatingSummaryProjection (defined in EatsReviewRepository.kt, same package) --
// identical average/count shape, no need for a second projection interface.
interface ProductRatingSummaryProjection {
    val productId: String
    val average: Double?
    val count: Long
}

interface ProductReviewRepository : JpaRepository<ProductReview, String> {
    fun findByOrderItemId(orderItemId: String): ProductReview?

    fun findByProductIdOrderByCreatedAtDesc(productId: String, pageable: Pageable): Page<ProductReview>

    @Query("SELECT AVG(r.rating) as average, COUNT(r) as count FROM ProductReview r WHERE r.productId = :productId")
    fun getProductRatingSummary(@Param("productId") productId: String): RatingSummaryProjection

    // Real batched rating lookup (2026-08-25) -- same no-N+1 discipline as
    // EatsReviewRepository.getRestaurantRatingSummaries: one GROUP BY query for a whole
    // deals/search page of products, not one getProductRatingSummary call per product.
    // Closes the "no star rating on the Toss-Shopping-style recommended grid" gap --
    // real reviews already existed (ProductReview), they just weren't folded into the
    // deals/search list payload yet.
    @Query(
        "SELECT r.productId as productId, AVG(r.rating) as average, COUNT(r) as count " +
            "FROM ProductReview r WHERE r.productId IN :productIds GROUP BY r.productId",
    )
    fun getProductRatingSummaries(@Param("productIds") productIds: List<String>): List<ProductRatingSummaryProjection>
}
