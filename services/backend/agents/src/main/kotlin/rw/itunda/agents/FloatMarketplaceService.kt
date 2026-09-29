package rw.itunda.agents

import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Notification
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.domain.FloatListing
import rw.itunda.core.domain.FloatListingStatus
import rw.itunda.core.domain.FloatTransferRequest
import rw.itunda.core.domain.FloatTransferRequestStatus
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.AgentOperatorRepository
import rw.itunda.core.repository.AgentRepository
import rw.itunda.core.repository.FloatListingRepository
import rw.itunda.core.repository.FloatTransferRequestRepository
import rw.itunda.core.repository.LedgerAccountRepository
import rw.itunda.core.repository.NotificationRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
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
    private val fraudRuleEngine: FraudRuleEngine,
    private val entityManager: EntityManager,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
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
        val openListings = floatListingRepository.findByStatus(FloatListingStatus.OPEN)
            .filter { it.agentId != operator.agentId && it.remainingAmount() > BigDecimal.ZERO }
        // Real N+1 fix (2026-09-13, structural N+1 re-sweep): batch-resolve every
        // listing's agent in one query instead of one findById per listing, same
        // findAllById-then-associateBy idiom MerchantFeeWaiverService.getRevocationCandidates
        // and the other list-of-records-referencing-an-entity-by-id services already use.
        val agentsById = agentRepository.findAllById(openListings.map { it.agentId }.distinct()).associateBy { it.id }
        return openListings.asSequence()
            .mapNotNull { listing ->
                val agent = agentsById[listing.agentId] ?: return@mapNotNull null
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
        // Real gap found (2026-09-07, Agents product-completeness pass): only
        // postListing had a rate limit -- requestFloat/acceptRequest/declineRequest
        // below were all unbounded. Same 20/hour per-agent sizing postListing already
        // established for this same peer-marketplace feature.
        rateLimiter.checkLimit("float:request:create:${operator.agentId}", limit = 20, window = Duration.ofHours(1))
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
        val saved = floatTransferRequestRepository.save(
            FloatTransferRequest(id = "floattransferreq_${UUID.randomUUID()}", listingId = listingId, requestingAgentId = operator.agentId, amount = amount),
        )
        // Real sibling-asymmetry fix (2026-09-13) -- P2pService notifies both parties
        // of a request/resolution on the identical "request against you" shape
        // (markExpired/payRequest); this sibling service sent zero notifications
        // anywhere in its lifecycle until now.
        notifyAgentOperators(
            listing.agentId, "New float request",
            "You have a new ${amount.toPlainString()} RWF float request against your listing.",
            "{\"listingId\":\"${listing.id}\",\"requestId\":\"${saved.id}\"}",
        )
        return saved
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
        // Same real gap, same 20/hour per-agent sizing as requestFloat above.
        rateLimiter.checkLimit("float:request:accept:${operator.agentId}", limit = 20, window = Duration.ofHours(1))
        val request = floatTransferRequestRepository.findById(requestId).orElseThrow { FloatTransferRequestNotFoundException("Float transfer request not found") }
        // Real 404-not-403 IDOR discipline: look the listing up once first purely to
        // check ownership before taking any lock, and treat "not this operator's
        // listing" identically to "no such request" rather than leaking whether it
        // exists to a non-owner.
        val listingCheck = floatListingRepository.findById(request.listingId).orElseThrow { FloatTransferRequestNotFoundException("Float transfer request not found") }
        if (listingCheck.agentId != operator.agentId) throw FloatTransferRequestNotFoundException("Float transfer request not found")

        // Real row lock on the request itself, re-checked under the lock -- without
        // this, two concurrent accept calls on the SAME request (a double-click, or an
        // accept racing a decline) could both pass a status check taken from a stale,
        // unlocked read before either one reaches the listing lock below, and both go
        // on to post a real, separate ledger transfer for the same request.
        //
        // Real lost-update bug found live (2026-08-09), same class as LedgerService's
        // own fix the same day: `request` above is an UNLOCKED read of this exact same
        // entity id, done purely for the ownership pre-check. Because it's already
        // managed in this transaction's persistence context, the locked fetch below
        // would return that SAME cached instance with its stale `status` field instead
        // of the row it just locked -- a real double-accept risk (two concurrent
        // accepts on different requests against the same listing, or an accept racing a
        // decline, could both read REQUESTED). `entityManager.refresh(...,
        // PESSIMISTIC_WRITE)` forces the true current row state, matching
        // LedgerService.postLedgerTransaction's own fix and its own doc comment for why
        // a bare refresh() (no lock mode) is NOT enough under MySQL's REPEATABLE READ.
        val lockedRequest = floatTransferRequestRepository.findByIdForUpdate(requestId).orElseThrow { FloatTransferRequestNotFoundException("Float transfer request not found") }
        entityManager.refresh(lockedRequest, LockModeType.PESSIMISTIC_WRITE)
        if (lockedRequest.status != FloatTransferRequestStatus.REQUESTED) throw FloatTransferRequestNotPendingException("This request has already been resolved")

        // Real row lock -- see FloatListingRepository.findByIdForUpdate's own doc
        // comment. Serializes concurrent accepts against the same listing so
        // claimedAmount is always checked against the real, current value. Same
        // staleness risk as lockedRequest above -- `listingCheck` earlier in this
        // method already loaded this exact listing id unlocked -- so this also needs
        // an explicit locking refresh, not just the locked fetch alone.
        val listing = floatListingRepository.findByIdForUpdate(lockedRequest.listingId).orElseThrow { FloatListingNotFoundException("Float listing not found") }
        entityManager.refresh(listing, LockModeType.PESSIMISTIC_WRITE)
        if (listing.status != FloatListingStatus.OPEN) throw FloatListingNotOpenException("This float listing is no longer open")
        if (lockedRequest.amount > listing.remainingAmount()) throw FloatListingInsufficientRemainingException("This request can no longer be fulfilled -- the listing's remaining amount has already been claimed")

        val listingAgent = agentRepository.findById(listing.agentId).orElseThrow { AgentNotFoundException("Agent not found") }
        val requestingAgent = agentRepository.findById(lockedRequest.requestingAgentId).orElseThrow { AgentNotFoundException("Agent not found") }

        // Lock both real agent cash accounts, in a stable id order, exactly matching
        // LedgerService.postLedgerTransaction's own "lock every account touched, in a
        // stable order" discipline -- avoids deadlocking against another concurrent
        // transfer touching the same two accounts in reverse order.
        val accountIds = listOf(listingAgent.cashAccountId, requestingAgent.cashAccountId).sorted()
        val lockedAccounts = accountIds.associateWith { id -> ledgerAccountRepository.findByIdForUpdate(id).orElseThrow { IllegalStateException("Agent cash account is missing") } }

        // postLedgerTransaction only enforces sufficiency for ACCOUNT-typed legs (see
        // its own loop) -- AGENT_CASH gets no automatic protection, so this mirrors
        // AgentService.cashOut's own explicit availableCash check before posting.
        val listingAvailableCash = lockedAccounts.getValue(listingAgent.cashAccountId).balance.negate()
        if (listingAvailableCash < lockedRequest.amount) {
            throw FloatListingInsufficientCashException("This agent's real till no longer has enough cash on hand to fulfill this request")
        }

        val ledger = ledgerService.postLedgerTransaction(
            "RWF",
            listOf(
                LedgerLeg(requestingAgent.cashAccountId, LedgerAccountType.AGENT_CASH, LedgerDirection.DEBIT, lockedRequest.amount, "Float received from ${listingAgent.displayName} (float marketplace)"),
                LedgerLeg(listingAgent.cashAccountId, LedgerAccountType.AGENT_CASH, LedgerDirection.CREDIT, lockedRequest.amount, "Float sent to ${requestingAgent.displayName} (float marketplace)"),
            ),
        )
        // Real gap found (2026-09-07, Agents product-completeness pass): real money
        // movement between two agents' own cash accounts with zero FraudRuleEngine
        // coverage -- AgentService.cashIn/cashOut already got this exact fix in an
        // earlier pass, this sibling service never did. Evaluated against the
        // accepting operator's own userId (the real platform user executing this),
        // same recipientUserId=null reasoning AgentService.cashIn/cashOut already
        // establish -- the counterparty here is another agent, not a recurring itunda
        // user counterparty the NEW_RECIPIENT rule's shape fits.
        fraudRuleEngine.evaluate(userId, null, lockedRequest.amount, ledger.transactionId)

        lockedRequest.status = FloatTransferRequestStatus.ACCEPTED
        lockedRequest.transactionId = ledger.transactionId
        floatTransferRequestRepository.save(lockedRequest)

        listing.claimedAmount = listing.claimedAmount.add(lockedRequest.amount)
        if (listing.claimedAmount >= listing.amount) listing.status = FloatListingStatus.FULFILLED
        floatListingRepository.save(listing)

        // Real sibling-asymmetry fix (2026-09-13) -- real money just moved between two
        // agents' own cash accounts with zero notification to either side. Only the
        // REQUESTING agent is notified, not the listing agent -- the accepting operator
        // performed this action themselves and needs no push telling them what they
        // just did, same "who's absent from this action" reasoning AgentService's own
        // single-party cashIn/cashOut notifications already follow.
        notifyAgentOperators(
            requestingAgent.id, "Float received",
            "${listingAgent.displayName} sent you ${lockedRequest.amount.toPlainString()} RWF via the float marketplace.",
            "{\"requestId\":\"${lockedRequest.id}\",\"transactionId\":\"${ledger.transactionId}\"}",
        )

        return lockedRequest
    }

    @Transactional
    fun declineRequest(userId: String, requestId: String): FloatTransferRequest {
        val operator = activeOperator(userId)
        // Same real gap, same 20/hour per-agent sizing as requestFloat/acceptRequest
        // above.
        rateLimiter.checkLimit("float:request:decline:${operator.agentId}", limit = 20, window = Duration.ofHours(1))
        val request = floatTransferRequestRepository.findById(requestId).orElseThrow { FloatTransferRequestNotFoundException("Float transfer request not found") }
        val listing = floatListingRepository.findById(request.listingId).orElseThrow { FloatTransferRequestNotFoundException("Float transfer request not found") }
        if (listing.agentId != operator.agentId) throw FloatTransferRequestNotFoundException("Float transfer request not found")

        // Same real row lock as acceptRequest -- see its own doc comment. Guards a
        // decline racing a concurrent accept on the same request.
        val lockedRequest = floatTransferRequestRepository.findByIdForUpdate(requestId).orElseThrow { FloatTransferRequestNotFoundException("Float transfer request not found") }
        if (lockedRequest.status != FloatTransferRequestStatus.REQUESTED) throw FloatTransferRequestNotPendingException("This request has already been resolved")
        lockedRequest.status = FloatTransferRequestStatus.DECLINED
        val saved = floatTransferRequestRepository.save(lockedRequest)
        notifyRequestDeclined(saved, "was declined")
        return saved
    }

    /**
     * Real gap closed 2026-09-13: a cancelled listing left any still-REQUESTED
     * request against it permanently stuck -- acceptRequest rejects a non-OPEN
     * listing forever (FloatListingNotOpenException), but nothing ever auto-declined
     * the orphaned request or told its requester the listing was gone. Now mirrors
     * declineRequest's own real resolution + notification for every pending request
     * this cancellation orphans.
     */
    @Transactional
    fun cancelListing(userId: String, listingId: String): FloatListing {
        val operator = activeOperator(userId)
        val listing = floatListingRepository.findByIdForUpdate(listingId).orElseThrow { FloatListingNotFoundException("Float listing not found") }
        if (listing.agentId != operator.agentId) throw FloatListingNotFoundException("Float listing not found")
        if (listing.status != FloatListingStatus.OPEN) throw FloatListingNotOpenException("Only an open listing can be cancelled")
        listing.status = FloatListingStatus.CANCELLED
        val saved = floatListingRepository.save(listing)
        val orphanedRequests = floatTransferRequestRepository.findByListingIdAndStatus(listingId, FloatTransferRequestStatus.REQUESTED)
        for (orphaned in orphanedRequests) {
            orphaned.status = FloatTransferRequestStatus.DECLINED
            val savedOrphan = floatTransferRequestRepository.save(orphaned)
            notifyRequestDeclined(savedOrphan, "was declined because the listing was cancelled")
        }
        return saved
    }

    private fun notifyRequestDeclined(request: FloatTransferRequest, reasonSuffix: String) {
        notifyAgentOperators(
            request.requestingAgentId, "Float request declined",
            "Your float request for ${request.amount.toPlainString()} RWF $reasonSuffix.",
            "{\"requestId\":\"${request.id}\"}",
        )
    }

    /** Notifies every active operator of an agent -- an agent can have more than one
     * real operator (AgentOperatorRepository.findByAgentId), so "the agent" is told by
     * telling every currently-active person who actually runs its till. */
    private fun notifyAgentOperators(agentId: String, title: String, body: String, dataJson: String?) {
        val operators = agentOperatorRepository.findByAgentId(agentId).filter { it.isActive }
        for (op in operators) {
            notificationRepository.save(
                Notification(
                    id = "notification_${UUID.randomUUID()}", userId = op.userId, type = "float_marketplace",
                    title = title, body = body, isRead = false, createdAt = Instant.now(), dataJson = dataJson,
                ),
            )
            pushNotificationService.sendToUser(op.userId, title, body)
        }
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
