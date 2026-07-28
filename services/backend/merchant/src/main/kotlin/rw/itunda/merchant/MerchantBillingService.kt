package rw.itunda.merchant

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantBillingPlan
import rw.itunda.core.domain.MerchantBillingSubscription
import rw.itunda.core.domain.MerchantBillingSubscriptionStatus
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.MerchantBillingPlanRepository
import rw.itunda.core.repository.MerchantBillingSubscriptionRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class InvalidBillingPlanException(message: String) : RuntimeException(message)
class BillingPlanNotFoundException(message: String) : RuntimeException(message)
class BillingSubscriptionNotFoundException(message: String) : RuntimeException(message)
class SelfSubscriptionException(message: String) : RuntimeException(message)
class BillingNoWalletException(message: String) : RuntimeException(message)

/**
 * Real Kakao Pay 정기결제/Toss Payments billing-key-style recurring merchant billing --
 * see `MerchantBillingPlan.kt`/`MerchantBillingSubscription.kt`'s own doc comments for
 * the full account. Reuses the exact real wallet-to-wallet ledger movement
 * `MerchantService.collect()` already established for QR payments -- a recurring
 * charge is not a new kind of money movement, just a different trigger for the same
 * real transaction shape.
 */
@Service
class MerchantBillingService(
    private val merchantBillingPlanRepository: MerchantBillingPlanRepository,
    private val merchantBillingSubscriptionRepository: MerchantBillingSubscriptionRepository,
    private val merchantRepository: MerchantRepository,
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val transactionRepository: TransactionRepository,
    private val notificationRepository: NotificationRepository,
    private val rateLimiter: RateLimiter,
    private val pushNotificationService: PushNotificationService,
) {
    private val log = LoggerFactory.getLogger(MerchantBillingService::class.java)

    // Same real Toss Payments fee-schedule reasoning MerchantService.feeRate's own
    // comment gives -- one flat rate in the middle of the published range, the same
    // real wallet-to-wallet collection underneath, just recurring.
    private val feeRate = BigDecimal("0.015")

    @Transactional
    fun createPlan(ownerUserId: String, name: String, description: String?, amount: BigDecimal, intervalDays: Int): MerchantBillingPlan {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || trimmedName.length > 255) {
            throw InvalidBillingPlanException("Name must be between 1 and 255 characters")
        }
        if (amount <= BigDecimal.ZERO) throw InvalidBillingPlanException("Amount must be greater than zero")
        if (intervalDays < 1 || intervalDays > 365) throw InvalidBillingPlanException("Interval must be between 1 and 365 days")
        val merchant = merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw MerchantNotFoundException("This account is not registered as a merchant")

        return merchantBillingPlanRepository.save(
            MerchantBillingPlan(
                id = "billing_plan_${UUID.randomUUID()}", merchantId = merchant.id, name = trimmedName,
                description = description?.trim()?.take(500)?.ifBlank { null }, amount = amount, intervalDays = intervalDays,
            ),
        )
    }

    fun getPlansForMerchant(merchantId: String): List<MerchantBillingPlan> = merchantBillingPlanRepository.findByMerchantIdAndActiveTrue(merchantId)

    fun getMyPlans(ownerUserId: String): List<MerchantBillingPlan> {
        val merchant = merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw MerchantNotFoundException("This account is not registered as a merchant")
        return merchantBillingPlanRepository.findByMerchantIdOrderByCreatedAtDesc(merchant.id)
    }

    @Transactional
    fun deactivatePlan(ownerUserId: String, planId: String): MerchantBillingPlan {
        val merchant = merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw MerchantNotFoundException("This account is not registered as a merchant")
        val plan = merchantBillingPlanRepository.findById(planId).orElseThrow { BillingPlanNotFoundException("Plan not found") }
        if (plan.merchantId != merchant.id) throw BillingPlanNotFoundException("Plan not found")
        plan.active = false
        return merchantBillingPlanRepository.save(plan)
    }

    /**
     * Real subscribe -- itunda's own honest equivalent of acquiring a real Kakao Pay
     * "sid"/Toss billing key: authorizing charges the first real cycle immediately
     * (matching both platforms' own real "인증 + 첫결제" flow, not a free trial this
     * feature never claimed), then arms `nextChargeAt` for the scheduler to pick up
     * automatically from here on, with zero further customer approval needed.
     *
     * Unlike the scheduler's own recurring `chargeOne`, a failed FIRST charge must
     * fail the whole subscribe attempt -- real Kakao Pay/Toss billing-key acquisition
     * never leaves you holding a "successfully created" authorization whose first
     * payment silently failed. `executeCharge` is left to throw here so `@Transactional`
     * rolls back with no subscription row ever persisted.
     */
    @Transactional
    fun subscribe(customerId: String, planId: String): MerchantBillingSubscription {
        rateLimiter.checkLimit("merchant-billing:subscribe:$customerId", limit = 20, window = Duration.ofHours(1))

        val plan = merchantBillingPlanRepository.findById(planId).orElseThrow { BillingPlanNotFoundException("Plan not found") }
        if (!plan.active) throw BillingPlanNotFoundException("Plan not found")
        val merchant = merchantRepository.findById(plan.merchantId).orElseThrow { MerchantNotFoundException("Merchant not found") }
        if (merchant.ownerUserId == customerId) throw SelfSubscriptionException("Cannot subscribe to your own billing plan")

        val customerWallet = walletRepository.findByUserIdAndType(customerId, WalletType.MAIN)
            ?: throw BillingNoWalletException("No wallet found for this account")
        val merchantWallet = walletRepository.findById(merchant.walletId).orElse(null)
            ?: throw BillingNoWalletException("Merchant wallet not found")

        val subscription = MerchantBillingSubscription(
            id = "billing_sub_${UUID.randomUUID()}", planId = plan.id, merchantId = plan.merchantId, customerId = customerId,
            nextChargeAt = Instant.now(),
        )
        executeCharge(subscription, plan, merchant, customerWallet, merchantWallet)
        subscription.nextChargeAt = subscription.nextChargeAt.plus(plan.intervalDays.toLong(), ChronoUnit.DAYS)
        return merchantBillingSubscriptionRepository.save(subscription)
    }

    fun getMySubscriptions(customerId: String): List<MerchantBillingSubscription> =
        merchantBillingSubscriptionRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)

    @Transactional
    fun cancelSubscription(customerId: String, subscriptionId: String): MerchantBillingSubscription {
        val subscription = merchantBillingSubscriptionRepository.findByIdAndCustomerId(subscriptionId, customerId)
            ?: throw BillingSubscriptionNotFoundException("Subscription not found")
        subscription.status = MerchantBillingSubscriptionStatus.CANCELLED
        subscription.cancelledAt = Instant.now()
        return merchantBillingSubscriptionRepository.save(subscription)
    }

    fun getDueForExecution(): List<MerchantBillingSubscription> =
        merchantBillingSubscriptionRepository.findByStatusAndNextChargeAtLessThanEqual(MerchantBillingSubscriptionStatus.ACTIVE, Instant.now())

    /**
     * Real recurring charge -- returns false (never throws) on a genuine, honest
     * failure (insufficient funds, closed wallet) so the scheduler's per-item loop is
     * never blocked by one bad subscription, same discipline `AutoTransferService
     * .executeOne` already establishes for a different real recurring flow. A failed
     * charge is skipped, not retried same-cycle: the schedule still advances to the
     * next real occurrence.
     */
    @Transactional
    fun chargeOne(subscription: MerchantBillingSubscription, plan: MerchantBillingPlan? = null): Boolean {
        val resolvedPlan = plan ?: merchantBillingPlanRepository.findById(subscription.planId).orElse(null)
        if (resolvedPlan == null) {
            subscription.lastFailureReason = "Billing plan no longer available"
            subscription.nextChargeAt = subscription.nextChargeAt.plus(1, ChronoUnit.DAYS)
            merchantBillingSubscriptionRepository.save(subscription)
            return false
        }
        val merchant = merchantRepository.findById(subscription.merchantId).orElse(null)
        val customerWallet = walletRepository.findByUserIdAndType(subscription.customerId, WalletType.MAIN)
        val merchantWallet = merchant?.let { walletRepository.findById(it.walletId).orElse(null) }

        val succeeded = if (merchant == null || customerWallet == null || merchantWallet == null) {
            subscription.lastFailureReason = "Merchant or wallet no longer available"
            false
        } else {
            try {
                executeCharge(subscription, resolvedPlan, merchant, customerWallet, merchantWallet)
                true
            } catch (e: InsufficientFundsException) {
                subscription.lastFailureReason = "Insufficient balance"
                false
            } catch (e: Exception) {
                log.error("Subscription charge {} failed with an unexpected error", subscription.id, e)
                subscription.lastFailureReason = "Couldn't complete this charge"
                false
            }
        }
        subscription.nextChargeAt = subscription.nextChargeAt.plus(resolvedPlan.intervalDays.toLong(), ChronoUnit.DAYS)
        merchantBillingSubscriptionRepository.save(subscription)
        return succeeded
    }

    /**
     * The one real charge attempt shared by both callers -- throws honestly
     * (`InsufficientFundsException` or otherwise) rather than swallowing anything;
     * `subscribe()` lets it propagate so a failed first charge rolls back the whole
     * attempt, while `chargeOne` wraps this in its own try/catch for the scheduler's
     * resilient per-cycle behavior.
     */
    private fun executeCharge(subscription: MerchantBillingSubscription, plan: MerchantBillingPlan, merchant: Merchant, customerWallet: Wallet, merchantWallet: Wallet) {
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
            pushNotificationService.sendToUser(subscription.customerId, title, body, mapOf("subscriptionId" to subscription.id))
        } catch (e: Exception) {
            // Non-critical -- the real charge already completed and succeeded.
        }
    }
}
