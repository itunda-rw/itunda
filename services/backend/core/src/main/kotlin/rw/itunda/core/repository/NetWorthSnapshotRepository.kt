package rw.itunda.core.repository

import java.time.Instant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import rw.itunda.core.domain.NetWorthSnapshot

interface NetWorthSnapshotRepository : JpaRepository<NetWorthSnapshot, String> {
    fun findByUserIdAndCapturedAtGreaterThanEqualOrderByCapturedAtAsc(userId: String, since: Instant): List<NetWorthSnapshot>
    fun existsByUserIdAndCapturedAtGreaterThanEqual(userId: String, since: Instant): Boolean

    // Real N+1 fix (2026-09-12) -- NetWorthSnapshotScheduler.captureAll used to call
    // existsByUserIdAndCapturedAtGreaterThanEqual once per real user, every night, an
    // N-query cost scaling with the user base -- same real hot-frequency shape (not
    // table size) DiscoverService's own impression-recording fix already established
    // as the real signal for this bug class. One batched DISTINCT query instead.
    @Query("SELECT DISTINCT s.userId FROM NetWorthSnapshot s WHERE s.capturedAt >= :since")
    fun findDistinctUserIdsCapturedSince(since: Instant): List<String>
}
