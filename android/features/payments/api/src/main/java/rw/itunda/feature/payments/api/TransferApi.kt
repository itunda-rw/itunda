package rw.itunda.feature.payments.api

import java.math.BigDecimal

/**
 * An intent to transfer money. Toss principle: "Quote before confirmation".
 * A TransferIntent is created first, quoted with fees, and then either confirmed or cancelled.
 */
data class TransferIntent(
    val id: String,
    val sourceAccountId: String,
    val recipient: String, // Can be phone number, account ID, etc.
    val amount: BigDecimal,
    val currency: String,
    val fee: BigDecimal? = null,
    val status: IntentStatus
)

enum class IntentStatus {
    DRAFT,
    QUOTED,
    PROCESSING,
    COMPLETED,
    FAILED
}

interface TransferService {
    /**
     * Step 1: Create the intent and get a quote (verifying against ledger).
     */
    suspend fun createQuote(sourceAccountId: String, recipient: String, amount: BigDecimal, currency: String): Result<TransferIntent>
    
    /**
     * Step 2: Confirm the transfer and execute it.
     */
    suspend fun confirmTransfer(intentId: String): Result<String> // Returns ledger transaction ID
}
