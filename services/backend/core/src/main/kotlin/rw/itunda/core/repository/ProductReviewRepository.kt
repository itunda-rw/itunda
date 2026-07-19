package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.ProductReview

// Reuses RatingSummaryProjection (defined in EatsReviewRepository.kt, same package) --
// identical average/count shape, no need for a second projection interface.
interface ProductReviewRepository : JpaRepository<ProductReview, String> {
    fun findByOrderItemId(orderItemId: String): ProductReview?

    fun findByProductIdOrderByCreatedAtDesc(productId: String, pageable: Pageable): Page<ProductReview>

    @Query("SELECT AVG(r.rating) as average, COUNT(r) as count FROM ProductReview r WHERE r.productId = :productId")
    fun getProductRatingSummary(@Param("productId") productId: String): RatingSummaryProjection
}
