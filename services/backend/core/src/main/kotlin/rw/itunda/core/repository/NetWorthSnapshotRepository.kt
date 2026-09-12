package rw.itunda.core.repository

import java.time.Instant
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.NetWorthSnapshot

interface NetWorthSnapshotRepository : JpaRepository<NetWorthSnapshot, String> {
    fun findByUserIdAndCapturedAtGreaterThanEqualOrderByCapturedAtAsc(userId: String, since: Instant): List<NetWorthSnapshot>
    fun existsByUserIdAndCapturedAtGreaterThanEqual(userId: String, since: Instant): Boolean
}
