package rw.itunda.core.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.FloatListing
import rw.itunda.core.domain.FloatListingStatus
import rw.itunda.core.domain.FloatTransferRequest
import rw.itunda.core.domain.FloatTransferRequestStatus
import java.util.Optional

interface FloatListingRepository : JpaRepository<FloatListing, String> {
    fun findByAgentIdOrderByCreatedAtDesc(agentId: String): List<FloatListing>
    fun findByStatus(status: FloatListingStatus): List<FloatListing>

    // Real row lock -- accepting a request is a check-then-act mutation of
    // claimedAmount, but @Version alone only detects a lost update after the fact
    // (a real 409 on the loser's own save). Locking here means a losing concurrent
    // acceptRequest call blocks and re-reads the current claimedAmount instead of
    // racing to a guaranteed-stale check, matching LedgerAccountRepository's own
    // "findByIdForUpdate" convention for a check-then-act row.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from FloatListing l where l.id = :id")
    fun findByIdForUpdate(@Param("id") id: String): Optional<FloatListing>
}

interface FloatTransferRequestRepository : JpaRepository<FloatTransferRequest, String> {
    fun findByListingIdOrderByCreatedAtDesc(listingId: String): List<FloatTransferRequest>
    fun findByRequestingAgentIdOrderByCreatedAtDesc(requestingAgentId: String): List<FloatTransferRequest>
    fun findByListingIdAndStatus(listingId: String, status: FloatTransferRequestStatus): List<FloatTransferRequest>

    // Real "requests other agents sent against MY listings" view -- the listing
    // owner's own accept/decline surface, distinct from findByRequestingAgentId
    // (the requester's own "requests I sent" view).
    fun findByListingIdInOrderByCreatedAtDesc(listingIds: List<String>): List<FloatTransferRequest>
}
