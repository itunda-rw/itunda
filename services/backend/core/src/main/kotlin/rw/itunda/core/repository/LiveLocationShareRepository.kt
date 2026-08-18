package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.LiveLocationShare

interface LiveLocationShareRepository : JpaRepository<LiveLocationShare, String> {
    // Real IDOR guards, same shape P2pDelayedTransferRepository.findByIdAndSenderUserId
    // already establishes -- a sharer can only ever act on their OWN share, a
    // recipient can only ever read a share genuinely addressed to them.
    fun findByIdAndSharerUserId(id: String, sharerUserId: String): LiveLocationShare?
    fun findByIdAndRecipientUserId(id: String, recipientUserId: String): LiveLocationShare?

    // Real "push my current position to every share I'm sharer on" feed -- coarse
    // repo filter (every non-revoked row for this sharer), exact isActive()
    // expiry-vs-now check lives in the service, same "coarse repo filter, exact logic
    // in the service" discipline MerchantLoyaltyPointsService.getExpirableAccounts
    // already establishes.
    fun findBySharerUserIdAndRevokedFalse(sharerUserId: String): List<LiveLocationShare>
    fun findBySharerUserIdOrderByCreatedAtDesc(sharerUserId: String): List<LiveLocationShare>
    fun findByRecipientUserIdOrderByCreatedAtDesc(recipientUserId: String): List<LiveLocationShare>
    fun countBySharerUserIdAndRevokedFalse(sharerUserId: String): Long
}
