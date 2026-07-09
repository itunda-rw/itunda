package rw.itunda.core.ledger.domain.model

import java.math.BigDecimal

/**
 * An immutable record of a money movement in the double-entry ledger.
 */
data class LedgerTransaction(
    val id: String,
    val idempotencyKey: String,
    val description: String,
    val timestamp: Long,
    val status: TransactionStatus,
    val postings: List<Posting>
) {
    init {
        // Enforce the double-entry principle locally: Debits must equal Credits
        val totalDebits = postings.filter { it.type == PostingType.DEBIT }.sumOf { it.amount }
        val totalCredits = postings.filter { it.type == PostingType.CREDIT }.sumOf { it.amount }
        require(totalDebits.compareTo(totalCredits) == 0) {
            "Double-entry violation: Debits ($totalDebits) do not equal Credits ($totalCredits) for transaction $id"
        }
    }
}

data class Posting(
    val id: String,
    val accountId: String,
    val amount: BigDecimal,
    val currency: String,
    val type: PostingType
)

enum class PostingType {
    DEBIT, CREDIT
}

enum class TransactionStatus {
    PENDING, COMPLETED, FAILED, REVERSED
}
