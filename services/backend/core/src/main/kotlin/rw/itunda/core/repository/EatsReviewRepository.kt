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

interface RestaurantRatingSummaryProjection {
    val restaurantId: String
    val average: Double?
    val count: Long
}

interface EatsReviewRepository : JpaRepository<EatsReview, String> {
    fun findByOrderId(orderId: String): EatsReview?

    fun existsByBuyerIdAndPhotoUrlIsNotNull(buyerId: String): Boolean

    // Renamed (2026-08-17) to exclude reports-hidden reviews -- see EatsReviewReport.kt's
    // own doc comment. Every existing caller already only ever wanted visible reviews;
    // this is a real behavior fix, not a widening of the method's contract.
    fun findByRestaurantIdAndHiddenFalseOrderByCreatedAtDesc(restaurantId: String, pageable: Pageable): Page<EatsReview>

    @Query("SELECT AVG(r.restaurantRating) as average, COUNT(r) as count FROM EatsReview r WHERE r.restaurantId = :restaurantId AND r.hidden = false")
    fun getRestaurantRatingSummary(@Param("restaurantId") restaurantId: String): RatingSummaryProjection

    // Real batched rating lookup (2026-07-21) -- closes docs/DESIGN_REFERENCES.md's Eats
    // recommendation #2: "rating exists but sits behind an extra tap ... fold
    // rating/reviewCount into the existing list payload (a join, not a new round trip)".
    // One GROUP BY query for a whole restaurant browse page, not one
    // getRestaurantRatingSummary call per restaurant -- same real N+1 discipline this
    // project's own sweeps already established elsewhere.
    @Query(
        "SELECT r.restaurantId as restaurantId, AVG(r.restaurantRating) as average, COUNT(r) as count " +
            "FROM EatsReview r WHERE r.restaurantId IN :restaurantIds AND r.hidden = false GROUP BY r.restaurantId",
    )
    fun getRestaurantRatingSummaries(@Param("restaurantIds") restaurantIds: List<String>): List<RestaurantRatingSummaryProjection>

    @Query("SELECT AVG(r.riderRating) as average, COUNT(r) as count FROM EatsReview r WHERE r.riderId = :riderId AND r.hidden = false")
    fun getRiderRatingSummary(@Param("riderId") riderId: String): RatingSummaryProjection
}
