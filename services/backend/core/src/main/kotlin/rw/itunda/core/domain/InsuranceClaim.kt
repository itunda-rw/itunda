package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

enum class InsuranceClaimStatus { SUBMITTED, APPROVED, REJECTED }

/**
 * Real claims filing -- previously enrollment was real and ledger-backed but there was no
 * way to ever file a claim at all (see docs/TOSS_PARITY_MATRIX.md's Insurance row). Approval
 * pays out immediately from insurance_claims_expense, same "decide = terminal action" shape
 * as identity/ComplianceController and system/FraudReviewService, not a separate
 * approve-then-pay step -- there's no real insurer/reinsurer settlement delay to model here.
 */
@Entity
@Table(name = "insurance_claims")
class InsuranceClaim(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "policy_id", nullable = false, length = 64)
    val policyId: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(nullable = false)
    val description: String,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: InsuranceClaimStatus = InsuranceClaimStatus.SUBMITTED,

    @Column(name = "submitted_at", nullable = false)
    val submittedAt: Instant = Instant.now(),

    @Column(name = "reviewed_by", length = 64)
    var reviewedBy: String? = null,

    @Column(name = "reviewed_at")
    var reviewedAt: Instant? = null,

    @Column(name = "decision_reason")
    var decisionReason: String? = null,
) {
    protected constructor() : this(id = "", policyId = "", userId = "", description = "", amount = BigDecimal.ZERO)
}
