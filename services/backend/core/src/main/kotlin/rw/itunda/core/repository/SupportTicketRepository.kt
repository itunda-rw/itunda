package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.SupportTicket
import rw.itunda.core.domain.SupportTicketStatus

interface SupportTicketRepository : JpaRepository<SupportTicket, String> {
    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<SupportTicket>
    // Paginated -- see PageResponse.kt's doc comment; the open-tickets admin queue had
    // the same unbounded-List gap already fixed for Partner SDK/KYC/merchant, just
    // missed in that pass.
    fun findByStatusOrderByDueByAsc(status: SupportTicketStatus, pageable: Pageable): Page<SupportTicket>
}
