package rw.itunda.core.repository

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import rw.itunda.core.domain.FraudFlag
import rw.itunda.core.domain.FraudRule
import java.time.Instant
import java.util.Optional

interface FraudQueueRuleSummaryRow {
    val rule: FraudRule
    val unreviewed: Long
    val oldestUnreviewedAt: Instant
}

interface FraudFlagRepository : JpaRepository<FraudFlag, String> {
    /** Reviewer decisions are state transitions; only one reviewer may claim a flag. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByIdForUpdate(id: String): Optional<FraudFlag>

    // Paginated and risk-prioritized: a large ordinary NEW_RECIPIENT backlog must not
    // delay HIGH_VALUE or VELOCITY review. Within a tier, oldest flags remain first.
    @Query(
        value = """
            SELECT * FROM fraud_flags
            WHERE reviewed = FALSE
            ORDER BY CASE rule
                WHEN 'HIGH_VALUE' THEN 0
                WHEN 'VELOCITY' THEN 1
                ELSE 2
            END, created_at ASC
        """,
        countQuery = "SELECT COUNT(*) FROM fraud_flags WHERE reviewed = FALSE",
        nativeQuery = true,
    )
    fun findUnreviewedPrioritized(pageable: Pageable): Page<FraudFlag>

    @Query(
        """
            SELECT f.rule AS rule, COUNT(f) AS unreviewed, MIN(f.createdAt) AS oldestUnreviewedAt
            FROM FraudFlag f
            WHERE f.reviewed = FALSE
            GROUP BY f.rule
        """,
    )
    fun summarizeUnreviewedByRule(): List<FraudQueueRuleSummaryRow>
}
