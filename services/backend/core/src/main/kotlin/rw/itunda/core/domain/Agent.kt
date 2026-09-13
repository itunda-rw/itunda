package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

enum class AgentStatus { ACTIVE, SUSPENDED }

/**
 * An Itunda cash-in/cash-out location. An agent has its own ledger clearing account
 * rather than sharing a global cash bucket, so every note accepted at a store remains
 * attributable to that location until physical-cash reconciliation is implemented.
 */
@Entity
@Table(name = "agents")
class Agent(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "display_name", nullable = false, length = 160)
    var displayName: String,

    @Column(name = "cash_account_id", nullable = false, unique = true, length = 64)
    val cashAccountId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: AgentStatus = AgentStatus.ACTIVE,

    @Column(name = "daily_cash_in_limit", nullable = false, precision = 18, scale = 2)
    var dailyCashInLimit: BigDecimal,

    @Column(name = "daily_cash_out_limit", nullable = false, precision = 18, scale = 2)
    var dailyCashOutLimit: BigDecimal = dailyCashInLimit,

    @Column var latitude: Double? = null,
    @Column var longitude: Double? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    // Real admin-accountability gap closed (2026-09-13) -- suspending/reactivating an
    // agent (setStatus) previously left zero record of which admin acted, the same
    // gap class already closed for fundTill (see AgentTillFunding's own doc comment)
    // in this same file, just missed here.
    @Column(name = "status_changed_by_user_id", length = 64)
    var statusChangedByUserId: String? = null,

    @Column(name = "status_changed_at")
    var statusChangedAt: Instant? = null,
) {
    protected constructor() : this("", "", "", dailyCashInLimit = BigDecimal.ZERO)
}

/** Immutable receipt audit row for cash accepted by an Itunda agent. */
@Entity
@Table(name = "agent_cash_ins")
class AgentCashIn(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "agent_id", nullable = false, length = 64)
    val agentId: String,

    @Column(name = "account_id", nullable = false, length = 64)
    val accountId: String,

    @Column(name = "receipt_number", nullable = false, unique = true, length = 80)
    val receiptNumber: String,

    @Column(name = "ledger_transaction_id", nullable = false, unique = true, length = 64)
    val ledgerTransactionId: String,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Column(name = "accepted_by_user_id", nullable = false, length = 64)
    val acceptedByUserId: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this("", "", "", "", "", BigDecimal.ZERO, "")
}

/** Immutable receipt audit row for cash paid out by an Itunda agent. */
@Entity
@Table(name = "agent_cash_outs")
class AgentCashOut(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "agent_id", nullable = false, length = 64)
    val agentId: String,

    @Column(name = "account_id", nullable = false, length = 64)
    val accountId: String,

    @Column(name = "receipt_number", nullable = false, unique = true, length = 80)
    val receiptNumber: String,

    @Column(name = "ledger_transaction_id", nullable = false, unique = true, length = 64)
    val ledgerTransactionId: String,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Column(name = "paid_by_user_id", nullable = false, length = 64)
    val paidByUserId: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this("", "", "", "", "", BigDecimal.ZERO, "")
}

/** A staff assignment, deliberately separate from a customer account or merchant role. */
@Entity
@Table(name = "agent_operators")
class AgentOperator(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "agent_id", nullable = false, length = 64)
    val agentId: String,

    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    val userId: String,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    // Real admin-accountability gap closed (2026-09-13) -- assigning a real AGENT-role
    // grant (assignOperator) and later activating/deactivating that operator's access
    // (setOperatorStatus) both previously left zero record of which admin acted --
    // assignOperator in particular grants real cash-in/cash-out authority, the same
    // stakes class this codebase's own admin-accountability precedent already treats
    // seriously elsewhere (e.g. Partner.kt's statusChangedBy for a role-adjacent grant).
    @Column(name = "assigned_by_user_id", length = 64)
    val assignedByUserId: String? = null,

    @Column(name = "status_changed_by_user_id", length = 64)
    var statusChangedByUserId: String? = null,

    @Column(name = "status_changed_at")
    var statusChangedAt: Instant? = null,
) {
    protected constructor() : this("", "", "")
}

enum class TillReconciliationStatus { MATCHED, PENDING_REVIEW, RESOLVED }

@Entity
@Table(name = "agent_till_reconciliations")
class AgentTillReconciliation(
    @Id @Column(length = 64) val id: String,
    @Column(name = "agent_id", nullable = false, length = 64) val agentId: String,
    @Column(name = "business_date", nullable = false) val businessDate: LocalDate,
    @Column(name = "expected_cash", nullable = false, precision = 18, scale = 2) val expectedCash: BigDecimal,
    @Column(name = "counted_cash", nullable = false, precision = 18, scale = 2) val countedCash: BigDecimal,
    @Column(nullable = false, precision = 18, scale = 2) val variance: BigDecimal,
    @Column(name = "submitted_by_user_id", nullable = false, length = 64) val submittedByUserId: String,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) var status: TillReconciliationStatus,
    @Column(name = "reviewed_by_user_id", length = 64) var reviewedByUserId: String? = null,
    @Column(name = "review_note", length = 255) var reviewNote: String? = null,
    @Column(name = "reviewed_at") var reviewedAt: Instant? = null,
    @Column(name = "created_at", nullable = false) val createdAt: Instant = Instant.now(),
) { protected constructor() : this("", "", LocalDate.MIN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "", TillReconciliationStatus.MATCHED) }

/**
 * Immutable audit row for real cash float sent to an agent's till -- real admin-
 * accountability gap closed (2026-09-12): unlike cashIn/cashOut (which record who
 * accepted/paid, albeit primarily for real commission routing) and till reconciliation
 * (reviewedByUserId), funding a till previously left zero record of which admin
 * created that real spendable balance, the single highest-stakes action in this whole
 * admin surface. Same immutable-receipt-row shape as AgentCashIn/AgentCashOut.
 */
@Entity
@Table(name = "agent_till_fundings")
class AgentTillFunding(
    @Id @Column(length = 64) val id: String,
    @Column(name = "agent_id", nullable = false, length = 64) val agentId: String,
    @Column(name = "ledger_transaction_id", nullable = false, unique = true, length = 64) val ledgerTransactionId: String,
    @Column(nullable = false, precision = 18, scale = 2) val amount: BigDecimal,
    @Column(nullable = false, length = 80) val reference: String,
    @Column(name = "funded_by_user_id", nullable = false, length = 64) val fundedByUserId: String,
    @Column(name = "created_at", nullable = false) val createdAt: Instant = Instant.now(),
) { protected constructor() : this("", "", "", BigDecimal.ZERO, "", "") }
