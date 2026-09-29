package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.P2pDelayedTransfer
import rw.itunda.core.domain.P2pDelayedTransferStatus

interface P2pDelayedTransferRepository : JpaRepository<P2pDelayedTransfer, String> {
    // Real IDOR guard, same shape RideTrustedContactRepository.findByIdAndUserId
    // already establishes -- a sender can only ever cancel their OWN delayed
    // transfer, never one they merely know the id of.
    fun findByIdAndSenderUserId(id: String, senderUserId: String): P2pDelayedTransfer?
    fun findBySenderUserIdOrderByCreatedAtDesc(senderUserId: String): List<P2pDelayedTransfer>

    // Real scheduled auto-release sweep -- see P2pDelayedTransferReleaseScheduler's own
    // doc comment. Coarse repo filter (every real PENDING transfer), exact due-or-not
    // logic lives in the service, same discipline MarketplaceEscrowRepository.
    // findByStatus already establishes for its own auto-release sweep.
    fun findByStatus(status: P2pDelayedTransferStatus): List<P2pDelayedTransfer>

    // Real pre-release reminder sweep (2026-09-13) -- see
    // P2pDelayedTransfer.REMINDER_WINDOW's own doc comment. Same coarse-filter-then-
    // exact-check-in-service split as findByStatus above.
    fun findByStatusAndRemindedAtIsNull(status: P2pDelayedTransferStatus): List<P2pDelayedTransfer>
}
