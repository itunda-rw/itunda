package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.MerchantBookingReview

// Reuses RatingSummaryProjection (defined in EatsReviewRepository.kt, same package) --
// identical average/count shape, no need for a second projection interface.
interface MerchantBookingReviewRepository : JpaRepository<MerchantBookingReview, String> {
    fun findByBookingId(bookingId: String): MerchantBookingReview?

    fun findByMerchantIdOrderByCreatedAtDesc(merchantId: String, pageable: Pageable): Page<MerchantBookingReview>

    fun findByCustomerIdOrderByCreatedAtDesc(customerId: String, pageable: Pageable): Page<MerchantBookingReview>

    @Query("SELECT AVG(r.rating) as average, COUNT(r) as count FROM MerchantBookingReview r WHERE r.merchantId = :merchantId")
    fun getMerchantRatingSummary(@Param("merchantId") merchantId: String): RatingSummaryProjection
}
