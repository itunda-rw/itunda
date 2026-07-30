package rw.itunda.core.fraud

import org.springframework.stereotype.Service
import org.springframework.beans.factory.annotation.Value
import rw.itunda.core.domain.FraudFlag
import rw.itunda.core.domain.FraudRule
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.repository.FraudFlagRepository
import rw.itunda.core.repository.TransactionRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private val DEFAULT_HIGH_VALUE_THRESHOLD = BigDecimal("100000")
private const val DEFAULT_VELOCITY_WINDOW_MINUTES = 5L
private const val DEFAULT_VELOCITY_THRESHOLD = 3
private val DEFAULT_NEW_RECIPIENT_MINIMUM_AMOUNT = BigDecimal.ZERO

data class FraudPolicySnapshot(
    val highValueThreshold: BigDecimal,
    val velocityWindowMinutes: Long,
    val velocityThreshold: Int,
    val newRecipientMinimumAmount: BigDecimal,
)

/**
 * Real fraud/velocity rules -- previously nothing existed here at all (see
 * docs/TOSS_PARITY_MATRIX.md's Operations/Fraud row: "No fraud-rule engine exists yet").
 * Matches the three heuristics the row itself names: high-value, velocity, new recipient.
 *
 * Deliberately review-only, never blocking: a freshly-built heuristic engine with no track
 * record and a fixed threshold is a worse failure mode as a hard block (real money movement
 * silently rejected on a false positive) than as a flag a human reviews after the fact. A
 * real production system would tune thresholds against real transaction volume before ever
 * considering a block; this system doesn't have that data yet.
 *
 * Lives in :core (same convention as LedgerService, rw.itunda.core.ledger) rather than a
 * single feature module, since it needs to evaluate transactions originating from more than
 * one money-moving flow -- correction: an earlier version of this comment named merchant
 * collection and wallet transfer as "not yet wired"; both (plus Commerce/Eats/Dine-in
 * checkout and Payroll) were wired the same session, this comment just never got updated.
 * Real current callers: P2pService (send + payment requests), WalletService (currency
 * conversion), OrderService/EatsOrderService/DineInOrderService (checkout),
 * MerchantService (in-person collection), PayrollService (salary disbursement).
 */
@Service
class FraudRuleEngine(
    private val fraudFlagRepository: FraudFlagRepository,
    private val transactionRepository: TransactionRepository,
    @Value("\${itunda.fraud.high-value-threshold:100000}")
    private val highValueThreshold: BigDecimal = DEFAULT_HIGH_VALUE_THRESHOLD,
    @Value("\${itunda.fraud.velocity-window-minutes:5}")
    private val velocityWindowMinutes: Long = DEFAULT_VELOCITY_WINDOW_MINUTES,
    @Value("\${itunda.fraud.velocity-threshold:3}")
    private val velocityThreshold: Int = DEFAULT_VELOCITY_THRESHOLD,
    @Value("\${itunda.fraud.new-recipient-minimum-amount:0}")
    private val newRecipientMinimumAmount: BigDecimal = DEFAULT_NEW_RECIPIENT_MINIMUM_AMOUNT,
) {

    init {
        require(highValueThreshold > BigDecimal.ZERO) { "itunda.fraud.high-value-threshold must be positive" }
        require(velocityWindowMinutes > 0) { "itunda.fraud.velocity-window-minutes must be positive" }
        require(velocityThreshold > 0) { "itunda.fraud.velocity-threshold must be positive" }
        require(newRecipientMinimumAmount >= BigDecimal.ZERO) { "itunda.fraud.new-recipient-minimum-amount must not be negative" }
    }

    fun activePolicy(): FraudPolicySnapshot = FraudPolicySnapshot(
        highValueThreshold = highValueThreshold,
        velocityWindowMinutes = velocityWindowMinutes,
        velocityThreshold = velocityThreshold,
        newRecipientMinimumAmount = newRecipientMinimumAmount,
    )

    fun evaluate(userId: String, recipientUserId: String?, amount: BigDecimal, transactionId: String): List<FraudFlag> {
        val history = transactionRepository.findBySenderIdOrRecipientIdOrderByCreatedAtDesc(userId, userId)
            // Fraud signals are based on settled money movement. Failed/cancelled
            // attempts must not inflate velocity or make a recipient look familiar.
            .filter { it.senderId == userId && it.status == TransactionStatus.COMPLETED }

        val flags = mutableListOf<FraudFlag>()

        if (amount >= highValueThreshold) {
            flags += flag(userId, transactionId, FraudRule.HIGH_VALUE, "Amount $amount RWF meets or exceeds the $highValueThreshold RWF high-value threshold", amount, "{\"highValueThreshold\":\"$highValueThreshold\"}")
        }

        val windowStart = Instant.now().minus(velocityWindowMinutes, ChronoUnit.MINUTES)
        val recentCount = history.count { it.createdAt.isAfter(windowStart) }
        if (recentCount >= velocityThreshold) {
            flags += flag(userId, transactionId, FraudRule.VELOCITY, "$recentCount outgoing transactions in the last $velocityWindowMinutes minutes (threshold $velocityThreshold)", amount, "{\"windowMinutes\":$velocityWindowMinutes,\"transactionThreshold\":$velocityThreshold,\"observedTransactions\":$recentCount}")
        }

        if (recipientUserId != null && amount >= newRecipientMinimumAmount && history.none { it.recipientId == recipientUserId }) {
            flags += flag(userId, transactionId, FraudRule.NEW_RECIPIENT, "First time this account has ever sent to recipient $recipientUserId (minimum amount threshold $newRecipientMinimumAmount RWF)", amount, "{\"minimumAmount\":\"$newRecipientMinimumAmount\"}")
        }

        return flags.map { fraudFlagRepository.save(it) }
    }

    private fun flag(userId: String, transactionId: String, rule: FraudRule, description: String, amount: BigDecimal, ruleParameters: String) = FraudFlag(
        id = "flag_${UUID.randomUUID()}", userId = userId, transactionId = transactionId,
        rule = rule, description = description, ruleParameters = ruleParameters, amount = amount,
    )
}
