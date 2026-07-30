package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

enum class FraudRule { HIGH_VALUE, VELOCITY, NEW_RECIPIENT }
enum class FraudFlagDecision { CLEARED, CONFIRMED }

/**
 * A real, review-only flag -- never blocks a transaction (see FraudRuleEngine's own comment
 * for why: shipping a hard block on a freshly-built heuristic engine without a human review
 * step first is the riskier choice, not the safer one). Matches the row name itself,
 * "Fraud/review", not "Fraud/block".
 */
@Entity
@Table(name = "fraud_flags")
class FraudFlag(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "transaction_id", nullable = false, length = 64)
    val transactionId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val rule: FraudRule,

    @Column(nullable = false)
    val description: String,

    /** Immutable, machine-readable rule settings used when this flag was created. */
    @Column(name = "rule_parameters", length = 1000)
    val ruleParameters: String? = null,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Column(nullable = false)
    var reviewed: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    var decision: FraudFlagDecision? = null,

    @Column(name = "reviewed_by", length = 64)
    var reviewedBy: String? = null,

    @Column(name = "reviewed_at")
    var reviewedAt: Instant? = null,

    @Column(name = "review_note", length = 2000)
    var reviewNote: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", transactionId = "", rule = FraudRule.HIGH_VALUE, description = "", amount = BigDecimal.ZERO)
}
