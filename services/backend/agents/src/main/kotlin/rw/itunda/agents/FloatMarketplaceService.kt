package rw.itunda.agents

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.FloatListing
import rw.itunda.core.domain.FloatListingStatus
import rw.itunda.core.domain.FloatTransferRequest
import rw.itunda.core.domain.FloatTransferRequestStatus
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.AgentOperatorRepository
import rw.itunda.core.repository.AgentRepository
import rw.itunda.core.repository.FloatListingRepository
import rw.itunda.core.repository.FloatTransferRequestRepository
import rw.itunda.core.repository.LedgerAccountRepository
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID

class FloatListingNotFoundException(message: String) : RuntimeException(message)
class FloatTransferRequestNotFoundException(message: String) : RuntimeException(message)
class FloatListingNotOpenException(message: String) : RuntimeException(message)
class FloatListingInsufficientRemainingException(message: String) : RuntimeException(message)
class FloatTransferRequestNotPendingException(message: String) : RuntimeException(message)
class FloatSelfTransferException(message: String) : RuntimeException(message)
class FloatListingInsufficientCashException(message: String) : RuntimeException(message)

data class NearbyFloatListing(
    val listing: FloatListing,
    val agentDisplayName: String,
    val distanceKm: Double,
    val remainingAmount: BigDecimal,
)

/**
 * Real peer-to-peer agent float rebalancing marketplace -- see FloatListing.kt's own
 * doc comment for the full sourced account (Rwanda-native, beyond this session's usual
 * Toss/Kakao/Naver/Coupang reference ecosystems). A sibling service to AgentService in
 * the same module, not a method added onto it: this is a genuinely distinct
 * peer-marketplace sub-feature (list/browse/request/accept), not a till operation,
 * matching how Ikimina/SaccoService/CooperativeService each got their own dedicated
 * service class this session rather than being bolted onto an existing one.
 *
 * Reuses AgentService's own established primitives directly rather than reimplementing
 * them: `GeoUtils.haversineKm` for nearby discovery (same pattern as
 * `AgentService.nearby`), and the same real `AgentOperator` "is this account an active
 * operator of a real, ACTIVE agent" check `AgentService.activeOperator` already
 * performs -- duplicated here as a small private helper (not centralized) since
 * `activeOperator` is private to AgentService; this matches the established pattern of
 * each sibling service in this backend owning its own small local ownership-check
 * helper (e.g. Cooperative's own membership check, DesignatedDriver's own trip-owner
 * check) rather than always centralizing every check into one shared class.
 */
@Service
class FloatMarketplaceService(
    private val agentRepository: AgentRepository,
    private val agentOperatorRepository: AgentOperatorRepository,
    private val floatListingRepository: FloatListingRepository,
    private val floatTransferRequestRepository: FloatTransferRequestRepository,
    private val ledgerAccountRepository: LedgerAccountRepository,
    private val ledgerService: LedgerService,
    private val rateLimiter: RateLimiter,
) {

    @Transactional
    fun postListing(userId: String, amount: BigDecimal): FloatListing {
        require(amount > BigDecimal.ZERO) { "Listing amount must be greater than zero" }
        val operator = activeOperator(userId)
        // Real per-agent spam guard on a caller-writable, otherwise-unbounded action --
        // this session's own established lesson (AffiliateController.resolveLink,
        // ScamReportService.checkScamStatus) applied here from the first draft.
        rateLimiter.checkLimit("float:listing:create:${operator.agentId}", limit = 20, window = Duration.ofHours(1))
        return floatListingRepository.save(FloatListing(id = "floatlisting_${UUID.randomUUID()}", agentId = operator.agentId, amount = amount))
    }

    @Transactional(readOnly = true)
    fun getNearbyListings(userId: String, latitude: Double, longitude: Double, radiusKm: Double): List<NearbyFloatListing> {
        val operator = activeOperator(userId)
        require(GeoUtils.isValidCoordinate(latitude, longitude) && GeoUtils.isWithinRwanda(latitude, longitude)) { "Search location must be within Rwanda" }
        require(radiusKm in 0.1..100.0) { "radiusKm must be between 0.1 and 100" }
        return floatListingRepository.findByStatus(FloatListingStatus.OPEN).asSequence()
            .filter { it.agentId != operator.agentId && it.remainingAmount() > BigDecimal.ZERO }
            .mapNotNull { listing ->
                val agent = agentRepository.findById(listing.agentId).orElse(null) ?: return@mapNotNull null
                if (agent.latitude == null || agent.longitude == null) return@mapNotNull null
                val distanceKm = GeoUtils.haversineKm(latitude, longitude, agent.latitude!!, agent.longitude!!)
                if (distanceKm > radiusKm) return@mapNotNull null
                NearbyFloatListing(listing, agent.displayName, distanceKm, listing.remainingAmount())
            }
            .sortedBy { it.distanceKm }
            .take(50)
            .toList()
    }

    @Transactional
    fun requestFloat(userId: String, listingId: String, amount: BigDecimal): FloatTransferRequest {
        require(amount > BigDecimal.ZERO) { "Requested amount must be greater than zero" }
        val operator = activeOperator(userId)
        val listing = floatListingRepository.findById(listingId).orElseThrow { FloatListingNotFoundException("Float listing not found") }
        if (listing.agentId == operator.agentId) throw FloatSelfTransferException("An agent cannot request float from their own listing")
        if (listing.status != FloatListingStatus.OPEN) throw FloatListingNotOpenException("This float listing is no longer open")
        // A fast, honest first check against the amount available right now. Not the
        // authoritative check -- other pending requests can consume availability
        // between this moment and whenever the listing owner actually accepts, so
        // acceptRequest re-validates against the real, current claimedAmount under a
        // real row lock at that point. Mirrors this session's own Cooperative
        // repayAdvance lesson: never trust a caller-supplied amount without a real,
        // current server-side check against what's actually still available.
        if (amount > listing.remainingAmount()) throw FloatListingInsufficientRemainingException("Requested amount exceeds what remains available on this listing")
        return floatTransferRequestRepository.save(
            FloatTransferRequest(id = "floattransferreq_${UUID.randomUUID()}", listingId = listingId, requestingAgentId = operator.agentId, amount = amount),
        )
    }

    /**
     * Real, single real ledger movement at accept time -- the simpler, more honest v1
     * per this feature's own directive: a separate ACCEPTED-but-not-yet-transferred
     * step would itself be a new half-finished-state bug class to guard against, for
     * no real benefit here (unlike, say, a physical cash-in that genuinely needs a
     * distinct "confirmed received" step). Accepting IS the transfer.
     */
    @Transactional
    fun acceptRequest(userId: String, requestId: String): FloatTransferRequest {
        val operator = activeOperator(userId)
        val request = floatTransferRequestRepository.findById(requestId).orElseThrow { FloatTransferRequestNotFoundException("Float transfer request not found") }
        // Real 404-not-403 IDOR discipline: findByIdForUpdate below re-derives the
        // listing anyway, so look it up once first purely to check ownership before
        // taking any lock, and treat "not this operator's listing" identically to
        // "no such request" rather than leaking whether it exists to a non-owner.
        val listingCheck = floatListingRepository.findById(request.listingId).orElseThrow { FloatTransferRequestNotFoundException("Float transfer request not found") }
        if (listingCheck.agentId != operator.agentId) throw FloatTransferRequestNotFoundException("Float transfer request not found")
        if (request.status != FloatTransferRequestStatus.REQUESTED) throw FloatTransferRequestNotPendingException("This request has already been resolved")

        // Real row lock -- see FloatListingRepository.findByIdForUpdate's own doc
        // comment. Serializes concurrent accepts against the same listing so
        // claimedAmount is always checked against the real, current value.
        val listing = floatListingRepository.findByIdForUpdate(request.listingId).orElseThrow { FloatListingNotFoundException("Float listing not found") }
        if (listing.status != FloatListingStatus.OPEN) throw FloatListingNotOpenException("This float listing is no longer open")
        if (request.amount > listing.remainingAmount()) throw FloatListingInsufficientRemainingException("This request can no longer be fulfilled -- the listing's remaining amount has already been claimed")

        val listingAgent = agentRepository.findById(listing.agentId).orElseThrow { AgentNotFoundException("Agent not found") }
        val requestingAgent = agentRepository.findById(request.requestingAgentId).orElseThrow { AgentNotFoundException("Agent not found") }

        // Lock both real agent cash accounts, in a stable id order, exactly matching
        // LedgerService.postLedgerTransaction's own "lock every account touched, in a
        // stable order" discipline -- avoids deadlocking against another concurrent
        // transfer touching the same two accounts in reverse order.
        val accountIds = listOf(listingAgent.cashAccountId, requestingAgent.cashAccountId).sorted()
        val lockedAccounts = accountIds.associateWith { id -> ledgerAccountRepository.findByIdForUpdate(id).orElseThrow { IllegalStateException("Agent cash account is missing") } }

        // postLedgerTransaction only enforces sufficiency for WALLET-typed legs (see
        // its own loop) -- AGENT_CASH gets no automatic protection, so this mirrors
        // AgentService.cashOut's own explicit availableCash check before posting.
        val listingAvailableCash = lockedAccounts.getValue(listingAgent.cashAccountId).balance.negate()
        if (listingAvailableCash < request.amount) {
            throw FloatListingInsufficientCashException("This agent's real till no longer has enough cash on hand to fulfill this request")
        }

        val ledger = ledgerService.postLedgerTransaction(
            "RWF",
            listOf(
                LedgerLeg(requestingAgent.cashAccountId, LedgerAccountType.AGENT_CASH, LedgerDirection.DEBIT, request.amount, "Float received from ${listingAgent.displayName} (float marketplace)"),
                LedgerLeg(listingAgent.cashAccountId, LedgerAccountType.AGENT_CASH, LedgerDirection.CREDIT, request.amount, "Float sent to ${requestingAgent.displayName} (float marketplace)"),
            ),
        )

        request.status = FloatTransferRequestStatus.ACCEPTED
        request.transactionId = ledger.transactionId
        floatTransferRequestRepository.save(request)

        listing.claimedAmount = listing.claimedAmount.add(request.amount)
        if (listing.claimedAmount >= listing.amount) listing.status = FloatListingStatus.FULFILLED
        floatListingRepository.save(listing)

        return request
    }

    @Transactional
    fun declineRequest(userId: String, requestId: String): FloatTransferRequest {
        val operator = activeOperator(userId)
        val request = floatTransferRequestRepository.findById(requestId).orElseThrow { FloatTransferRequestNotFoundException("Float transfer request not found") }
        val listing = floatListingRepository.findById(request.listingId).orElseThrow { FloatTransferRequestNotFoundException("Float transfer request not found") }
        if (listing.agentId != operator.agentId) throw FloatTransferRequestNotFoundException("Float transfer request not found")
        if (request.status != FloatTransferRequestStatus.REQUESTED) throw FloatTransferRequestNotPendingException("This request has already been resolved")
        request.status = FloatTransferRequestStatus.DECLINED
        return floatTransferRequestRepository.save(request)
    }

    @Transactional
    fun cancelListing(userId: String, listingId: String): FloatListing {
        val operator = activeOperator(userId)
        val listing = floatListingRepository.findByIdForUpdate(listingId).orElseThrow { FloatListingNotFoundException("Float listing not found") }
        if (listing.agentId != operator.agentId) throw FloatListingNotFoundException("Float listing not found")
        if (listing.status != FloatListingStatus.OPEN) throw FloatListingNotOpenException("Only an open listing can be cancelled")
        listing.status = FloatListingStatus.CANCELLED
        return floatListingRepository.save(listing)
    }

    @Transactional(readOnly = true)
    fun myListings(userId: String): List<FloatListing> = floatListingRepository.findByAgentIdOrderByCreatedAtDesc(activeOperator(userId).agentId)

    @Transactional(readOnly = true)
    fun myRequests(userId: String): List<FloatTransferRequest> = floatTransferRequestRepository.findByRequestingAgentIdOrderByCreatedAtDesc(activeOperator(userId).agentId)

    /** Requests other agents sent against MY listings -- the accept/decline surface. */
    @Transactional(readOnly = true)
    fun myIncomingRequests(userId: String): List<FloatTransferRequest> {
        val agentId = activeOperator(userId).agentId
        val listingIds = floatListingRepository.findByAgentIdOrderByCreatedAtDesc(agentId).map { it.id }
        if (listingIds.isEmpty()) return emptyList()
        return floatTransferRequestRepository.findByListingIdInOrderByCreatedAtDesc(listingIds)
    }

    private fun FloatListing.remainingAmount(): BigDecimal = amount.subtract(claimedAmount)

    /** Duplicated from AgentService.activeOperator (private there) -- see this class's
     * own doc comment for why. */
    private fun activeOperator(userId: String): rw.itunda.core.domain.AgentOperator {
        val operator = agentOperatorRepository.findByUserId(userId)
            ?.takeIf { it.isActive }
            ?: throw AgentOperatorNotAuthorizedException("This account is not an active agent operator")
        val agent = agentRepository.findById(operator.agentId).orElseThrow { AgentNotFoundException("Agent not found") }
        if (agent.status != rw.itunda.core.domain.AgentStatus.ACTIVE) throw AgentSuspendedException("This agent is suspended")
        return operator
    }
}
