package rw.itunda.core.events

import java.math.BigDecimal
import java.time.Instant

/** Payload for the `ledger.posted` topic -- one of the event names
 * docs/TOSS_RWANDA_ALIGNMENT.md's event model already documents. */
data class LedgerPostedEvent(
    val transactionId: String,
    val currency: String,
    val postedAt: Instant,
    val legs: List<LedgerPostedLeg>,
)

data class LedgerPostedLeg(
    val accountId: String,
    val accountType: String,
    val direction: String,
    val amount: BigDecimal,
    val memo: String,
)

const val TOPIC_LEDGER_POSTED = "ledger.posted"
