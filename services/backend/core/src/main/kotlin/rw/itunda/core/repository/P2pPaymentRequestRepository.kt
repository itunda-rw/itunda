package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.P2pPaymentRequest

interface P2pPaymentRequestRepository : JpaRepository<P2pPaymentRequest, String> {
    fun findByRequesterUserIdOrderByCreatedAtDesc(requesterUserId: String): List<P2pPaymentRequest>
}
