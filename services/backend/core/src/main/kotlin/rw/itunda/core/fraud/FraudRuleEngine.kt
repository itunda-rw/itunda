package rw.itunda.core.fraud

import org.springframework.stereotype.Service
import rw.itunda.core.domain.FraudFlag
import rw.itunda.core.domain.FraudRule
import rw.itunda.core.repository.FraudFlagRepository
import rw.itunda.core.repository.TransactionRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private val HIGH_VALUE_THRESHOLD = BigDecimal("100000")
private const val VELOCITY_WINDOW_MINUTES = 5L
private const val VELOCITY_THRESHOLD = 3

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
) {

    fun evaluate(userId: String, recipientUserId: String?, amount: BigDecimal, transactionId: String): List<FraudFlag> {
        val history = transactionRepository.findBySenderIdOrRecipientIdOrderByCreatedAtDesc(userId, userId)
            .filter { it.senderId == userId }

        val flags = mutableListOf<FraudFlag>()

        if (amount >= HIGH_VALUE_THRESHOLD) {
            flags += flag(userId, transactionId, FraudRule.HIGH_VALUE, "Amount $amount RWF meets or exceeds the $HIGH_VALUE_THRESHOLD RWF high-value threshold", amount)
        }

        val windowStart = Instant.now().minus(VELOCITY_WINDOW_MINUTES, ChronoUnit.MINUTES)
        val recentCount = history.count { it.createdAt.isAfter(windowStart) }
        if (recentCount >= VELOCITY_THRESHOLD) {
            flags += flag(userId, transactionId, FraudRule.VELOCITY, "$recentCount outgoing transactions in the last $VELOCITY_WINDOW_MINUTES minutes (threshold $VELOCITY_THRESHOLD)", amount)
        }

        if (recipientUserId != null && history.none { it.recipientId == recipientUserId }) {
            flags += flag(userId, transactionId, FraudRule.NEW_RECIPIENT, "First time this account has ever sent to recipient $recipientUserId", amount)
        }

        return flags.map { fraudFlagRepository.save(it) }
    }

    private fun flag(userId: String, transactionId: String, rule: FraudRule, description: String, amount: BigDecimal) = FraudFlag(
        id = "flag_${UUID.randomUUID()}", userId = userId, transactionId = transactionId,
        rule = rule, description = description, amount = amount,
    )
}
