package rw.itunda.wallet

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class QuoteStatus { PENDING, CONFIRMED, EXPIRED, CANCELLED }

/**
 * Short-lived transfer quote — deliberately NOT a JPA/database entity, same design
 * choice as backend/src/services/transfers.ts's in-memory `quotes` Map: a quote is
 * throwaway state with a 60s TTL, not a durable record, so there's no reason to pay for
 * a DB round-trip on every quote. `userId` is stamped at creation and re-checked at
 * confirm time, closing the same "quote hijack" gap fixed in the Express backend
 * (SECURITY.md / CHANGELOG): a guessed or leaked quoteId can't be confirmed by a
 * different authenticated user.
 */
data class TransferQuote(
    val id: String,
    val userId: String,
    val fromWalletId: String,
    val recipient: String,
    val amount: BigDecimal,
    val fee: BigDecimal,
    val totalDebit: BigDecimal,
    val currency: String,
    var status: QuoteStatus,
    val expiresAt: Instant,
    val createdAt: Instant = Instant.now(),
)

private const val QUOTE_TTL_SECONDS = 60L

class QuoteStore {
    private val quotes = ConcurrentHashMap<String, TransferQuote>()

    fun create(userId: String, fromWalletId: String, recipient: String, amount: BigDecimal, fee: BigDecimal, currency: String): TransferQuote {
        val quote = TransferQuote(
            id = "quote_${UUID.randomUUID()}",
            userId = userId,
            fromWalletId = fromWalletId,
            recipient = recipient,
            amount = amount,
            fee = fee,
            totalDebit = amount.add(fee),
            currency = currency,
            status = QuoteStatus.PENDING,
            expiresAt = Instant.now().plusSeconds(QUOTE_TTL_SECONDS),
        )
        quotes[quote.id] = quote
        return quote
    }

    fun get(id: String): TransferQuote? = quotes[id]
}
