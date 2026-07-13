package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.ProviderAttemptLog
import java.time.LocalDate

interface RailDayAggregate {
    fun getRailId(): String
    fun getRailDisplayName(): String
    fun getTotalAttempts(): Long
    fun getSuccessCount(): Long
    fun getAvgLatencyMs(): Double
}

interface ProviderAttemptLogRepository : JpaRepository<ProviderAttemptLog, String> {
    @Query(
        """
        SELECT p.railId as railId, p.railDisplayName as railDisplayName,
               COUNT(p) as totalAttempts,
               SUM(CASE WHEN p.success = true THEN 1L ELSE 0L END) as successCount,
               AVG(p.latencyMs) as avgLatencyMs
        FROM ProviderAttemptLog p
        WHERE p.occurredDate = :date
        GROUP BY p.railId, p.railDisplayName
        """,
    )
    fun aggregateByDate(@Param("date") date: LocalDate): List<RailDayAggregate>
}
