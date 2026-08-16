package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

// RIDE_ISSUE added 2026-08-16 -- real Uber "trip issue report" pattern (Uber's own
// real post-trip support flow lets a rider report a problem -- unsafe driving,
// overcharge, lost item -- directly from a specific completed trip). itunda's generic
// ticket system already let a user pick ANY transaction including a ride's own
// payment, so this isn't a new capability -- it's a real, distinct category (own SLA,
// own framing) plus a real trip-contextual entry point, not a bespoke second ticket
// system.
enum class SupportTicketCategory { GENERAL, PAYMENT_DISPUTE, ACCOUNT_TAKEOVER, RIDE_ISSUE }
enum class SupportTicketStatus { OPEN, RESOLVED }
enum class SupportTicketResolution { REFUNDED, REJECTED }

/**
 * Real customer support ticket, tied to a specific transaction -- closes a false claim
 * this repo's own docs/TOSS_PARITY_MATRIX.md "Non-Negotiable Gates" section made
 * (corrected 2026-07-13): it described "real ticket creation/listing... and a real
 * refund action" as an already-met gate, but a repo-wide grep found no support/ticket
 * module anywhere in services/backend. This is that real implementation.
 *
 * `dueBy` is a real, itunda-defined SLA -- Toss's own public documentation does not
 * publish an exact numeric customer-support SLA to source, so this is itunda's own
 * policy (tighter for ACCOUNT_TAKEOVER, reflecting real urgency prioritization), not a
 * claimed Toss number. See [rw.itunda.core.support.SupportService] for the exact
 * per-category values.
 */
@Entity
@Table(name = "support_tickets")
class SupportTicket(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "transaction_id", nullable = false, length = 64)
    val transactionId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    val category: SupportTicketCategory,

    @Column(nullable = false, columnDefinition = "TEXT")
    val description: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: SupportTicketStatus = SupportTicketStatus.OPEN,

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    var resolution: SupportTicketResolution? = null,

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    var resolutionNotes: String? = null,

    @Column(name = "refund_transaction_id", length = 64)
    var refundTransactionId: String? = null,

    // Set true when this ticket's category caused the transaction's source wallet to be
    // frozen (Wallet.isActive = false) as the real account-takeover response; resolve()
    // unfreezes it regardless of decision, since a ticket is always the end of the review.
    @Column(name = "froze_wallet_id", length = 64)
    var frozeWalletId: String? = null,

    @Column(name = "due_by", nullable = false)
    val dueBy: Instant,

    @Column(name = "reviewed_by", length = 64)
    var reviewedBy: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null,

    // Real bug found live (2026-08-02): SupportService.resolve reads this exact entity,
    // checks `status == RESOLVED`, then -- for a REFUNDED resolution -- reverses the
    // original transaction (real money movement) and writes status back to RESOLVED,
    // the same check-then-act shape RideTripService.acceptTrip/KnowledgeService.
    // adoptAnswer already establish as needing @Version. With none here, two reviewers
    // concurrently resolving the same still-OPEN ticket as REFUNDED could both pass the
    // status check before either committed, both call reverseTransaction, and post a
    // real DOUBLE refund -- money created from nothing, a more severe instance of the
    // same bug class than Bike/ParkingSpot/KnowledgeQuestion's own data-integrity-only
    // versions of it.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(
        id = "", userId = "", transactionId = "", category = SupportTicketCategory.GENERAL,
        description = "", dueBy = Instant.now(),
    )
}
