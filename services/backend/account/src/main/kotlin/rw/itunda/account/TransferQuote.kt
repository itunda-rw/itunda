package rw.itunda.account

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class QuoteStatus { PENDING, CLAIMED, CONFIRMED, EXPIRED, CANCELLED }

/**
 * Short-lived transfer quote — deliberately NOT a JPA/database entity, same design
 * choice as backend/src/services/transfers.ts's in-memory `quotes` Map: a quote is
 * throwaway state with a 60s TTL, not a durable record, so there's no reason to pay for
 * a DB round-trip on every quote. `userId` is stamped at creation and re-checked at
 * confirm time, closing the same "quote hijack" gap fixed in the Express backend
 * (SECURITY.md / CHANGELOG): a guessed or leaked quoteId can't be confirmed by a
 * different authenticated user.
 *
 * `CLAIMED` (real bug found live 2026-08-02) closes a real double-spend race: not
 * being a JPA entity, this quote can't lean on `@Version`, and a plain `quote.status
 * == PENDING` read followed later by a plain `quote.status = CONFIRMED` write (the
 * previous shape) has no atomicity at all -- two concurrent `confirmTransfer` calls
 * for the same quoteId (a client retry with a fresh Idempotency-Key, or two racing
 * requests) could both observe PENDING before either wrote CONFIRMED, and both go on
 * to call the provider and post the real ledger legs, a genuine double-spend of one
 * quote. See [QuoteStore.claim]/[QuoteStore.releaseClaim] for the actual fix.
 */
data class TransferQuote(
    val id: String,
    val userId: String,
    val fromAccountId: String,
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

    fun create(userId: String, fromAccountId: String, recipient: String, amount: BigDecimal, fee: BigDecimal, currency: String): TransferQuote {
        val quote = TransferQuote(
            id = "quote_${UUID.randomUUID()}",
            userId = userId,
            fromAccountId = fromAccountId,
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

    /**
     * Atomically transitions a still-PENDING, not-yet-expired quote to CLAIMED, so at
     * most one concurrent [AccountService.confirmTransfer] call for the same quoteId
     * can ever proceed past this point -- see [TransferQuote]'s own doc comment for
     * the real double-spend race this closes. `computeIfPresent`'s remapping function
     * runs under `ConcurrentHashMap`'s own per-bucket lock, giving the read-check-write
     * the atomicity a plain field mutation on the shared quote object never had.
     *
     * A quote that's PENDING but already past its own `expiresAt` is real-expired as a
     * side effect of this same atomic step (rather than left for a separate,
     * non-atomic check), so a caller can never claim an already-expired quote through
     * a race window either. Returns null if the quote doesn't exist, isn't PENDING, or
     * was just real-expired -- the caller re-reads the quote's current status to pick
     * the exact right error.
     */
    fun claim(id: String, now: Instant): TransferQuote? {
        var claimed: TransferQuote? = null
        quotes.computeIfPresent(id) { _, existing ->
            if (existing.status == QuoteStatus.PENDING) {
                if (now.isAfter(existing.expiresAt)) {
                    existing.status = QuoteStatus.EXPIRED
                } else {
                    existing.status = QuoteStatus.CLAIMED
                    claimed = existing
                }
            }
            existing
        }
        return claimed
    }

    /** Reverts a CLAIMED quote back to PENDING after a failed downstream step (a real
     * provider decline) -- preserves the pre-existing "the same quote can be retried
     * after a decline" behavior [AccountService.confirmTransfer] already relied on,
     * rather than a decline permanently stranding the quote as unusable. */
    fun releaseClaim(id: String) {
        quotes.computeIfPresent(id) { _, existing ->
            if (existing.status == QuoteStatus.CLAIMED) existing.status = QuoteStatus.PENDING
            existing
        }
    }
}
