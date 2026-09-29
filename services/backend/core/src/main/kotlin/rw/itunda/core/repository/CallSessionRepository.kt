package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.CallSession

interface CallSessionRepository : JpaRepository<CallSession, String> {
    // Real call-history tab -- either side of the call, most recent first.
    @Query("SELECT c FROM CallSession c WHERE c.callerId = :userId OR c.calleeId = :userId ORDER BY c.startedAt DESC")
    fun findByParticipant(@Param("userId") userId: String, pageable: Pageable): Page<CallSession>
}
