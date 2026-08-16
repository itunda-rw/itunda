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
import rw.itunda.core.domain.Wallet
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
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
) {
    private val log = LoggerFactory.getLogger(MerchantBillingChargeExecutor::class.java)

    // Same real Toss Payments fee-schedule reasoning MerchantService.feeRate's own
    // comment gives -- one flat rate in the middle of the published range, the same
    // real wallet-to-wallet collection underneath, just recurring.
    private val feeRate = BigDecimal("0.015")

    /**
     * The one real charge attempt shared by both callers -- throws honestly
     * (`InsufficientFundsException` or otherwise) rather than swallowing anything;
     * `MerchantBillingService.subscribe()` lets it propagate so a failed first charge
     * rolls back the whole attempt, while `chargeOne` wraps this call in its own
     * try/catch for the scheduler's resilient per-cycle behavior.
     */
    @Transactional
    fun execute(subscription: MerchantBillingSubscription, plan: MerchantBillingPlan, merchant: Merchant, customerWallet: Wallet, merchantWallet: Wallet) {
        val fee = plan.amount.multiply(feeRate).setScale(2, RoundingMode.HALF_UP)
        val netToMerchant = plan.amount.subtract(fee)
        val result = ledgerService.postLedgerTransaction(
            customerWallet.currency,
            listOf(
                LedgerLeg(customerWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, plan.amount, "Subscription charge - ${plan.name}"),
                LedgerLeg(merchantWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, netToMerchant, "Subscription collection - ${plan.name}"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, fee, "Subscription fee - ${plan.name}"),
            ),
        )
        transactionRepository.save(
            Transaction(
                id = result.transactionId,
                referenceNumber = "BILLING${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
                senderId = subscription.customerId, recipientId = merchant.ownerUserId,
                fromWalletId = customerWallet.id, toWalletId = merchantWallet.id,
                amount = plan.amount, fee = fee, currency = customerWallet.currency,
                type = TransactionType.PAYMENT, status = TransactionStatus.COMPLETED,
                description = "Subscription charge - ${plan.name}", channel = "MERCHANT_BILLING",
                completedAt = Instant.now(),
            ),
        )
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
