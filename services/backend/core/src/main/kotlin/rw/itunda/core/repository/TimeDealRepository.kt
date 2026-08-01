package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.TimeDeal
import java.time.Instant

interface TimeDealRepository : JpaRepository<TimeDeal, String> {
    fun findByMerchantIdOrderByCreatedAtDesc(merchantId: String, pageable: Pageable): Page<TimeDeal>

    @Query("SELECT t FROM TimeDeal t WHERE t.startsAt <= :now AND t.endsAt >= :now AND t.remainingQuantity > 0 ORDER BY t.endsAt ASC")
    fun findActiveDeals(@Param("now") now: Instant, pageable: Pageable): Page<TimeDeal>

    @Query("SELECT t FROM TimeDeal t WHERE t.productId = :productId AND t.startsAt <= :now AND t.endsAt >= :now AND t.remainingQuantity > 0")
    fun findActiveDealForProduct(@Param("productId") productId: String, @Param("now") now: Instant): TimeDeal?
}
