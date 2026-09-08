package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.P2pPaymentRequest
import rw.itunda.core.domain.P2pPaymentRequestStatus
import java.time.Instant

interface P2pPaymentRequestRepository : JpaRepository<P2pPaymentRequest, String> {
    fun findByRequesterUserIdOrderByCreatedAtDesc(requesterUserId: String): List<P2pPaymentRequest>

    // Real proactive-expiry gap (Bank product-completeness pass, cycle 2, 2026-09-09):
    // status only ever flipped PENDING -> EXPIRED lazily, inside payRequest, when someone
    // actually attempted to pay an already-expired request. A request nobody ever attempts
    // to pay stayed PENDING forever, including in the requester's own "My Requests" list,
    // long after its real expiresAt had passed -- see P2pPaymentRequestExpiryScheduler.
    fun findByStatusAndExpiresAtBefore(status: P2pPaymentRequestStatus, cutoff: Instant): List<P2pPaymentRequest>
}
