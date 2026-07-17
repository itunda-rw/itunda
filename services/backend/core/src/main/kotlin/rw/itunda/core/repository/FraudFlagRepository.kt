package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.FraudFlag

interface FraudFlagRepository : JpaRepository<FraudFlag, String> {
    // Paginated -- see PageResponse.kt's doc comment; an unreviewed-fraud-flag queue is
    // an unbounded admin list with the same shape as the Partner SDK/KYC/merchant ones
    // already paginated, just missed in that pass.
    fun findByReviewedFalseOrderByCreatedAtAsc(pageable: Pageable): Page<FraudFlag>
}
