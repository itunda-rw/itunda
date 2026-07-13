package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.SupportTicket
import rw.itunda.core.domain.SupportTicketStatus

interface SupportTicketRepository : JpaRepository<SupportTicket, String> {
    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<SupportTicket>
    fun findByStatusOrderByDueByAsc(status: SupportTicketStatus): List<SupportTicket>
}
