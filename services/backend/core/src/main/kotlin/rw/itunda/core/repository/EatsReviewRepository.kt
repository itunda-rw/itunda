package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.EatsReview

interface RatingSummaryProjection {
    val average: Double?
    val count: Long
}

interface EatsReviewRepository : JpaRepository<EatsReview, String> {
    fun findByOrderId(orderId: String): EatsReview?

    fun findByRestaurantIdOrderByCreatedAtDesc(restaurantId: String, pageable: Pageable): Page<EatsReview>

    @Query("SELECT AVG(r.restaurantRating) as average, COUNT(r) as count FROM EatsReview r WHERE r.restaurantId = :restaurantId")
    fun getRestaurantRatingSummary(@Param("restaurantId") restaurantId: String): RatingSummaryProjection

    @Query("SELECT AVG(r.riderRating) as average, COUNT(r) as count FROM EatsReview r WHERE r.riderId = :riderId")
    fun getRiderRatingSummary(@Param("riderId") riderId: String): RatingSummaryProjection
}
