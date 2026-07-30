package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.RideTripReview

interface RideTripReviewRepository : JpaRepository<RideTripReview, String> {
    fun findByTripId(tripId: String): RideTripReview?

    fun findByDriverIdOrderByCreatedAtDesc(driverId: String, pageable: Pageable): Page<RideTripReview>

    // Reuses RatingSummaryProjection (defined in EatsReviewRepository.kt, same package) --
    // identical average/count shape, no new projection type needed.
    @Query("SELECT AVG(r.rating) as average, COUNT(r) as count FROM RideTripReview r WHERE r.driverId = :driverId")
    fun getDriverRatingSummary(@Param("driverId") driverId: String): RatingSummaryProjection
}
