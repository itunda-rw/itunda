package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant

enum class FloatListingStatus { OPEN, FULFILLED, CANCELLED }
enum class FloatTransferRequestStatus { REQUESTED, ACCEPTED, DECLINED }

/**
 * Real peer-to-peer mobile money agent float rebalancing -- Rwanda-native, sourced
 * beyond this session's usual Toss/Kakao/Naver/Coupang reference ecosystems. Running
 * out of e-float or physical cash is a documented top-2 operational challenge for
 * mobile money agents across Africa (Rwanda specifically named among affected
 * countries, Financial Inclusion Insights Survey); the real existing rebalancing path
 * is traveling to a central point, often impossible on a weekend when banks are
 * closed. Rebalance (IDEO.org-backed) is a real existing product proving genuine
 * demand for exactly this peer-to-peer marketplace shape.
 *
 * Deliberately distinct from `AgentService.fundTill`: that flow is ADMIN-to-agent
 * (a central operator funds a till from `cash_vault`). This is AGENT-to-agent --
 * one agent with surplus float lists it, another agent short on float claims it,
 * no admin or cash_vault involved at either step.
 *
 * Money moves directly between the two real agents' own `Agent.cashAccountId`
 * `LedgerAccount` rows (the same real AGENT_CASH account `fundTill`/`cashIn`/
 * `cashOut` already use), never a pooled/shared account -- this session's SACCO
 * dividend bug (draining a shared wallet instead of a dedicated expense account)
 * is a different shape, but the same discipline applies: know exactly which real
 * account each leg touches.
 *
 * `claimedAmount` tracks the running total already accepted against this listing so
 * a request can never cause the listing to hand out more float than it actually
 * offered -- this session's Cooperative `repayAdvance` bug (an arbitrary
 * client-supplied amount silently closing out real debt) is the direct lesson this
 * field is designed against: the authoritative "is there still enough available"
 * check happens again at accept time against this field, not just once at request
 * time, since sibling pending requests can consume availability in between.
 *
 * `@Version`: accepting a request is a real check-then-act race -- two different
 * requesting agents' accept attempts (or the owner accepting two different requests)
 * touching the same listing's `claimedAmount` concurrently. This session already
 * needed this exact fix for Bike/Parking/Knowledge/SupportTicket/Incident/
 * HoodReport/PartnerMiniApp; applied here from day one, not as a follow-up fix.
 */
@Entity
@Table(name = "float_listings")
class FloatListing(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "agent_id", nullable = false, length = 64)
    val agentId: String,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Column(name = "claimed_amount", nullable = false, precision = 18, scale = 2)
    var claimedAmount: BigDecimal = BigDecimal.ZERO,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: FloatListingStatus = FloatListingStatus.OPEN,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(id = "", agentId = "", amount = BigDecimal.ZERO)
}

/**
 * One real request against a real `FloatListing` -- see FloatListing.kt's own doc
 * comment. `transactionId` is set only once the request is real-ACCEPTED and the
 * real ledger transfer has posted; a REQUESTED or DECLINED request never has one,
 * matching this backend's own established discipline (e.g. `HarvestAdvance`'s own
 * nullable `disbursementTransactionId`) of a transaction id meaning "this real
 * money movement actually happened," never a placeholder.
 */
@Entity
@Table(name = "float_transfer_requests")
class FloatTransferRequest(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "listing_id", nullable = false, length = 64)
    val listingId: String,

    @Column(name = "requesting_agent_id", nullable = false, length = 64)
    val requestingAgentId: String,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: FloatTransferRequestStatus = FloatTransferRequestStatus.REQUESTED,

    @Column(name = "transaction_id", length = 64)
    var transactionId: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", listingId = "", requestingAgentId = "", amount = BigDecimal.ZERO)
}
