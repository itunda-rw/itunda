package rw.itunda.account

import org.springframework.stereotype.Service
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.TransactionRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant

enum class SubscriptionCadence { WEEKLY, MONTHLY }

data class DetectedSubscription(
    val displayName: String,
    val amount: BigDecimal,
    val cadence: SubscriptionCadence,
    val occurrenceCount: Int,
    val lastPaidAt: Instant,
    val nextExpectedAt: Instant,
    val monthlyEquivalent: BigDecimal,
    // Real price-change alert (2026-07-26) -- see this class's own doc comment for the
    // real Toss capability this closes.
    val priceIncreased: Boolean,
    val previousAmount: BigDecimal?,
)

data class SubscriptionSummary(val subscriptions: List<DetectedSubscription>, val estimatedMonthlyTotal: BigDecimal)

/**
 * Real recurring-payment ("subscription") detection over a user's own real transaction
 * history -- closes Toss's own real "구독 관리" (subscription management) feature: a
 * real Toss user account confirms "결제되는 카드 연결해놓으면 알아서 분류해줍니다" (linking a
 * card lets Toss automatically detect and categorize your subscriptions -- Netflix,
 * Watcha, Coupang, YouTube, Melon, iCloud, Apple Music, Naver Plus), and Toss Bank's own
 * official glossary article (tossbank.com/articles/subscribe) reports a real sourced
 * "98.4% of adults aged 20-60 have tried a subscription service" and "an average of 3-4
 * subscriptions per user."
 *
 * Scoped to real merchant `PAYMENT`s and `BILL`s only -- the exact real channels the
 * Toss mechanic targets (card-linked recurring merchant/utility charges), deliberately
 * excluding `TRANSFER` (P2P), a materially different use case Toss's own feature isn't
 * describing. **Fully additive and read-only**: no new entity, no schema change, no
 * ledger/money-movement code touched at all -- a real, fresh `AVG`/grouping computation
 * over the existing `transactions` table at read time, same "no cached counter"
 * discipline `ProductReview`/`MerchantBookingReview`'s own rating summaries already
 * establish.
 *
 * A payment is flagged recurring once the SAME real recipient/description has recurred
 * at least twice at a real, consistent weekly (6-8 real days apart) or monthly (25-35
 * real days apart) cadence -- itunda's own honest scoping choice for classifying
 * "consistent," since neither sourced account discloses Toss's own exact detection
 * thresholds.
 *
 * **Price-change alert added 2026-07-26** -- closes a second real Toss capability this
 * same feature is named for (linked-card subscriptions flagged with their real current
 * price, and a real amount change surfaced rather than silently missed). Grouping used
 * to require the SAME exact amount every occurrence, which meant a genuine price hike
 * (Netflix raising its fee, say) silently broke the detected series in two: the old
 * amount's history went quiet, and the new amount needed its own two-occurrence
 * detection window to reappear as a "new" subscription, with no price-change signal at
 * all. Grouping is now by recipient/description only; cadence consistency (not amount
 * consistency) is what defines "this is a recurring series," and `priceIncreased`/
 * `previousAmount` surface a genuine amount change between the two most recent real
 * charges.
 */
@Service
class SubscriptionDetectionService(
    private val transactionRepository: TransactionRepository,
    private val merchantRepository: MerchantRepository,
) {
    companion object {
        private val WEEKLY_RANGE = 6..8
        private val MONTHLY_RANGE = 25..35
        private val WEEKS_PER_MONTH = BigDecimal("4.345")
    }

    fun detectSubscriptions(userId: String): SubscriptionSummary {
        val transactions = transactionRepository.findBySenderIdAndTypeInAndStatusOrderByCreatedAtAsc(
            userId, listOf(TransactionType.PAYMENT, TransactionType.BILL), TransactionStatus.COMPLETED,
        )
        val groups = transactions.groupBy { groupKey(it) }

        val detected = groups.values.mapNotNull { occurrences ->
            if (occurrences.size < 2) return@mapNotNull null
            val intervalsDays = occurrences.zipWithNext { a, b -> Duration.between(a.createdAt, b.createdAt).toDays() }
            val avgIntervalDays = intervalsDays.average()
            val cadence = when {
                avgIntervalDays.toInt() in WEEKLY_RANGE -> SubscriptionCadence.WEEKLY
                avgIntervalDays.toInt() in MONTHLY_RANGE -> SubscriptionCadence.MONTHLY
                else -> return@mapNotNull null
            }
            val last = occurrences.last()
            val previous = occurrences[occurrences.size - 2]
            val priceIncreased = last.amount.stripTrailingZeros() != previous.amount.stripTrailingZeros()
            val monthlyEquivalent = if (cadence == SubscriptionCadence.WEEKLY) {
                last.amount.multiply(WEEKS_PER_MONTH).setScale(2, RoundingMode.HALF_UP)
            } else {
                last.amount
            }
            DetectedSubscription(
                displayName = displayName(last),
                amount = last.amount,
                cadence = cadence,
                occurrenceCount = occurrences.size,
                lastPaidAt = last.createdAt,
                nextExpectedAt = last.createdAt.plus(Duration.ofDays(avgIntervalDays.toLong())),
                monthlyEquivalent = monthlyEquivalent,
                priceIncreased = priceIncreased,
                previousAmount = if (priceIncreased) previous.amount else null,
            )
        }.sortedByDescending { it.monthlyEquivalent }

        val total = detected.fold(BigDecimal.ZERO) { acc, s -> acc + s.monthlyEquivalent }
        return SubscriptionSummary(detected, total)
    }

    // Same recipient (merchant) or same bill memo -- deliberately NOT the amount too
    // (2026-07-26), see this class's own doc comment: a real price change is a signal
    // to surface, not a reason to lose track of the series.
    private fun groupKey(t: Transaction): String = "${t.type}|${t.recipientId}|${t.description}"

    // Real merchant business name for a PAYMENT (recipientId is the merchant owner's
    // userId, per MerchantService.collect); the transaction's own description
    // ("Bill payment X") is already the real, honest label for a BILL.
    private fun displayName(t: Transaction): String {
        if (t.type == TransactionType.PAYMENT) {
            merchantRepository.findByOwnerUserId(t.recipientId)?.let { return it.businessName }
        }
        return t.description
    }
}
