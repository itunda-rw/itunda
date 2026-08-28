package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.MerchantProfileView
import java.time.LocalDate

interface MerchantProfileViewRepository : JpaRepository<MerchantProfileView, String> {
    fun findByMerchantIdAndViewDateBetweenOrderByViewDateAsc(merchantId: String, from: LocalDate, to: LocalDate): List<MerchantProfileView>

    // Real atomic per-day upsert -- a deterministic id (merchantId + viewDate) means a
    // real DB-level "insert 1, or increment if today's row already exists" in one
    // statement, same atomic-increment discipline ListingRepository.incrementViewCount
    // already established (never a read-modify-write on a fetched entity).
    @Modifying
    @Query(
        value = "INSERT INTO merchant_profile_views (id, merchant_id, view_date, view_count) VALUES (:id, :merchantId, :viewDate, 1) " +
            "ON DUPLICATE KEY UPDATE view_count = view_count + 1",
        nativeQuery = true,
    )
    fun upsertView(@Param("id") id: String, @Param("merchantId") merchantId: String, @Param("viewDate") viewDate: LocalDate)
}
