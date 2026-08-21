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
