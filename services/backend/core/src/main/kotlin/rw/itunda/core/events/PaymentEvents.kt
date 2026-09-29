package rw.itunda.core.events

import java.math.BigDecimal
import java.time.Instant

/** Payload for the `payment.provider_succeeded` topic -- one of the event names
 * docs/TOSS_RWANDA_ALIGNMENT.md's event model documents; wired for real
 * (2026-07-11) at the two call sites that actually go through
 * `ProviderConnector.attempt` (`BillsService.payBill`/`buyAirtime`). Merchant QR
 * collection has no external rail call (pure account-to-account), so it never
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

/** Payload for the `payment.provider_failed` topic -- wired for real (2026-07-11),
 * same call sites as [PaymentProviderSucceededEvent]. No `transactionId` here: a
 * decline happens in [rw.itunda.core.provider.ProviderConnector.attempt], before the
 * ledger is ever touched, so no transaction exists to reference. Published via
 * [EventPublisher.publishImmediately], not [EventPublisher.publishAfterCommit] --
 * the enclosing @Transactional rolls back right after this fires (the decline is
 * rethrown to the caller), so an afterCommit hook would never run. */
data class PaymentProviderFailedEvent(
    val railId: String,
    val railDisplayName: String,
    val description: String,
    val amount: BigDecimal,
    val currency: String,
    val reason: String,
    val failedAt: Instant,
)

const val TOPIC_PAYMENT_PROVIDER_FAILED = "payment.provider_failed"
