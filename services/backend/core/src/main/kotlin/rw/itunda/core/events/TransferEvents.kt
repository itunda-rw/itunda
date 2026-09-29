package rw.itunda.core.events

import java.math.BigDecimal
import java.time.Instant

/** Payload for the `transfer.confirmed` topic -- one of the event names
 * docs/TOSS_RWANDA_ALIGNMENT.md's event model documents; wired for real
 * (2026-07-11) alongside `ledger.posted`. */
data class TransferConfirmedEvent(
    val transactionId: String,
    val fromAccountId: String,
    val recipient: String,
    val amount: BigDecimal,
    val fee: BigDecimal,
    val currency: String,
    val confirmedAt: Instant,
)

const val TOPIC_TRANSFER_CONFIRMED = "transfer.confirmed"
