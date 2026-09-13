package rw.itunda.merchant

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantBillingPlan
import rw.itunda.core.domain.MerchantBillingSubscription
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.Account
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.pricing.PlatformFees
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.TransactionRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

/**
 * Real charge-posting step for `MerchantBillingService`, split into its own bean
 * (2026-08-17) -- previously a private `executeCharge` method on `MerchantBillingService`
 * itself, called via self-invocation from `chargeOne`. Since `chargeOne` was also
 * `@Transactional`, a real `InsufficientFundsException` thrown by
 * `LedgerService.postLedgerTransaction` (a separately-proxied bean) inside that call
 * marked `chargeOne`'s own ambient transaction rollback-only *at the moment it threw* --
 * catching it one level up did not undo that mark, so `chargeOne`'s own bookkeeping
 * (`nextChargeAt`/`lastFailureReason`/save) would still fail to commit with a real
 * `UnexpectedRollbackException`, uncaught by `MerchantBillingScheduler.run()`'s loop.
 * The identical root cause behind Section 115's bills auto-pay bug (see
 * `BillAutoPayProcessor`'s own doc comment). Fix: `chargeOne` calls `execute()` here as
 * a genuine cross-bean proxied call instead, so a failure gets its own independent
 * physical transaction that rolls back cleanly on its own and never poisons the
 * caller's ambient state.
 */
@Component
class MerchantBillingChargeExecutor(
    private val ledgerService: LedgerService,
    private val transactionRepository: TransactionRepository,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
    private val autoTopUpService: rw.itunda.account.AutoTopUpService,
    private val fraudRuleEngine: FraudRuleEngine,
) {
    private val log = LoggerFactory.getLogger(MerchantBillingChargeExecutor::class.java)

    // Consolidated 2026-09-06 into core/pricing/PlatformFees -- see its own doc comment.
    private val feeRate = PlatformFees.PLATFORM_FEE_RATE

    /**
     * The one real charge attempt shared by both callers -- throws honestly
     * (`InsufficientFundsException` or otherwise) rather than swallowing anything;
     * `MerchantBillingService.subscribe()` lets it propagate so a failed first charge
     * rolls back the whole attempt, while `chargeOne` wraps this call in its own
     * try/catch for the scheduler's resilient per-cycle behavior.
     */
    @Transactional
    fun execute(subscription: MerchantBillingSubscription, plan: MerchantBillingPlan, merchant: Merchant, customerAccount: Account, merchantAccount: Account) {
        // Real Toss Bank/Toss Pay separation (2026-08-21) -- see MerchantService
        // .collect()'s own doc comment for the full sourced architecture. A recurring
        // subscription charge is the same real merchant-collection moment collect()
        // handles for QR payments, so it gets the identical treatment: auto-fund the
        // shortfall from itunda Bank (then an external linked account) before falling
        // through to a real, honest InsufficientFundsException. `customerAccount` is
        // already resolved to the customer's PAY account by both real callers
        // (MerchantBillingService.subscribe/chargeOne).
        val resolvedCustomerAccount = autoTopUpService.ensureSufficientPayBalance(subscription.customerId, customerAccount, plan.amount)
        val fee = plan.amount.multiply(feeRate).setScale(2, RoundingMode.HALF_UP)
        val netToMerchant = plan.amount.subtract(fee)
        val result = ledgerService.postLedgerTransaction(
            resolvedCustomerAccount.currency,
            listOf(
                LedgerLeg(resolvedCustomerAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, plan.amount, "Subscription charge - ${plan.name}"),
                LedgerLeg(merchantAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, netToMerchant, "Subscription collection - ${plan.name}"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, fee, "Subscription fee - ${plan.name}"),
            ),
        )
        val transaction = Transaction(
            id = result.transactionId,
            referenceNumber = "BILLING${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
            senderId = subscription.customerId, recipientId = merchant.ownerUserId,
            fromAccountId = resolvedCustomerAccount.id, toAccountId = merchantAccount.id,
            amount = plan.amount, fee = fee, currency = resolvedCustomerAccount.currency,
            type = TransactionType.PAYMENT, status = TransactionStatus.COMPLETED,
            description = "Subscription charge - ${plan.name}", channel = "MERCHANT_BILLING",
            completedAt = Instant.now(),
        )
        // Real gap found live (2026-09-14, FraudRuleEngine-verify sweep): this class's
        // own doc comment says a recurring subscription charge "is the same real
        // merchant-collection moment collect() handles for QR payments, so it gets the
        // identical treatment" -- but never copied collect()'s real
        // fraudRuleEngine.evaluate call. Reachable from chargeOne with zero customer
        // action in the loop at all (a scheduler-triggered recurring charge), so this
        // is a real, unattended money-movement path with no fraud review whatsoever.
        // Evaluated before the transaction row is saved, same ordering as
        // MerchantService.collect's own comment (evaluating after the save would let
        // this transaction match itself as prior history).
        fraudRuleEngine.evaluate(subscription.customerId, merchant.ownerUserId, plan.amount, transaction.id)
        transactionRepository.save(transaction)
        subscription.lastFailureReason = null
        subscription.chargeCount += 1
        subscription.lastChargedAt = Instant.now()
        try {
            val title = "Subscription charged"
            val body = "${plan.amount} RWF charged for ${plan.name} at ${merchant.businessName}"
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = subscription.customerId, type = "MERCHANT_BILLING_CHARGED",
                    title = title, body = body,
                    isRead = false, createdAt = Instant.now(), dataJson = "{\"subscriptionId\":\"${subscription.id}\"}",
                ),
            )
            sendChargedPushAfterCommit(subscription.customerId, title, body, subscription.id)
        } catch (e: Exception) {
            // Non-critical -- the real charge already completed and succeeded.
            log.warn("Failed to notify customer of subscription charge {}", subscription.id, e)
        }
    }

    /** A charge alert must never announce a payment whose enclosing transaction rolled back. */
    private fun sendChargedPushAfterCommit(customerId: String, title: String, body: String, subscriptionId: String) {
        val send = {
            try {
                pushNotificationService.sendToUser(customerId, title, body, mapOf("subscriptionId" to subscriptionId))
            } catch (e: Exception) {
                log.warn("Could not send subscription-charge push for subscription {}", subscriptionId, e)
            }
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
    }
}
