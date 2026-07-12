package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.FraudFlag

interface FraudFlagRepository : JpaRepository<FraudFlag, String> {
    fun findByReviewedFalseOrderByCreatedAtAsc(): List<FraudFlag>
}
