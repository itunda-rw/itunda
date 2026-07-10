package rw.itunda.core.events

import java.math.BigDecimal
import java.time.Instant

/** Payload for the `payment.provider_succeeded` topic -- one of the event names
 * docs/TOSS_RWANDA_ALIGNMENT.md's event model documents; wired for real
 * (2026-07-11) at the two call sites that actually go through
 * `ProviderConnector.attempt` (`BillsService.payBill`/`buyAirtime`). Merchant QR
 * collection has no external rail call (pure wallet-to-wallet), so it never
 * publishes this event -- there's no provider to have succeeded against. */
data class PaymentProviderSucceededEvent(
    val transactionId: String,
    val railId: String,
    val railDisplayName: String,
    val description: String,
    val amount: BigDecimal,
    val currency: String,
    val succeededAt: Instant,
)

const val TOPIC_PAYMENT_PROVIDER_SUCCEEDED = "payment.provider_succeeded"
