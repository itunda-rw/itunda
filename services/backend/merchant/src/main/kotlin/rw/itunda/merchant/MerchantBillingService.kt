package rw.itunda.merchant

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.MerchantBillingPlan
import rw.itunda.core.domain.MerchantBillingSubscription
import rw.itunda.core.domain.MerchantBillingSubscriptionStatus
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.MerchantBillingPlanRepository
import rw.itunda.core.repository.MerchantBillingSubscriptionRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class InvalidBillingPlanException(message: String) : RuntimeException(message)
class BillingPlanNotFoundException(message: String) : RuntimeException(message)
class BillingSubscriptionNotFoundException(message: String) : RuntimeException(message)
class SelfSubscriptionException(message: String) : RuntimeException(message)
class BillingNoAccountException(message: String) : RuntimeException(message)

/**
 * Real Kakao Pay 정기결제/Toss Payments billing-key-style recurring merchant billing --
 * see `MerchantBillingPlan.kt`/`MerchantBillingSubscription.kt`'s own doc comments for
 * the full account. Reuses the exact real account-to-account ledger movement
 * `MerchantService.collect()` already established for QR payments -- a recurring
 * charge is not a new kind of money movement, just a different trigger for the same
 * real transaction shape. The actual charge-posting step lives in
 * [MerchantBillingChargeExecutor] as its own bean -- see that class's own doc comment
 * for why.
 */
@Service
class MerchantBillingService(
    private val merchantBillingPlanRepository: MerchantBillingPlanRepository,
    private val merchantBillingSubscriptionRepository: MerchantBillingSubscriptionRepository,
    private val merchantRepository: MerchantRepository,
    private val accountRepository: AccountRepository,
    private val chargeExecutor: MerchantBillingChargeExecutor,
    private val rateLimiter: RateLimiter,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
) {
    private val log = LoggerFactory.getLogger(MerchantBillingService::class.java)

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
     * payment silently failed. `MerchantBillingChargeExecutor.execute` is left to
     * throw here so `@Transactional` rolls back with no subscription row ever
     * persisted.
     */
    @Transactional
    fun subscribe(customerId: String, planId: String): MerchantBillingSubscription {
        rateLimiter.checkLimit("merchant-billing:subscribe:$customerId", limit = 20, window = Duration.ofHours(1))

        val plan = merchantBillingPlanRepository.findById(planId).orElseThrow { BillingPlanNotFoundException("Plan not found") }
        if (!plan.active) throw BillingPlanNotFoundException("Plan not found")
        val merchant = merchantRepository.findById(plan.merchantId).orElseThrow { MerchantNotFoundException("Merchant not found") }
        if (merchant.ownerUserId == customerId) throw SelfSubscriptionException("Cannot subscribe to your own billing plan")

        val customerAccount = accountRepository.findByUserIdAndType(customerId, AccountType.MAIN)
            ?: throw BillingNoAccountException("No account found for this account")
        val merchantAccount = accountRepository.findById(merchant.accountId).orElse(null)
            ?: throw BillingNoAccountException("Merchant account not found")

        val subscription = MerchantBillingSubscription(
            id = "billing_sub_${UUID.randomUUID()}", planId = plan.id, merchantId = plan.merchantId, customerId = customerId,
            nextChargeAt = Instant.now(),
        )
        chargeExecutor.execute(subscription, plan, merchant, customerAccount, merchantAccount)
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
     * failure (insufficient funds, closed account) so the scheduler's per-item loop is
     * never blocked by one bad subscription, same discipline `AutoTransferService
     * .executeOne` already establishes for a different real recurring flow. A failed
     * charge is skipped, not retried same-cycle: the schedule still advances to the
     * next real occurrence.
     *
     * Deliberately NOT `@Transactional` itself (2026-08-17 fix) -- see
     * [MerchantBillingChargeExecutor]'s own doc comment for why: the real charge
     * attempt below is a genuine cross-bean call to that separate bean, so a failure
     * there gets its own independent transaction and can never poison this method's
     * own bookkeeping. `merchantBillingSubscriptionRepository.save` below still
     * persists atomically on its own via Spring Data's implicit per-call transaction.
     */
    fun chargeOne(subscription: MerchantBillingSubscription, plan: MerchantBillingPlan? = null): Boolean {
        val resolvedPlan = plan ?: merchantBillingPlanRepository.findById(subscription.planId).orElse(null)
        if (resolvedPlan == null) {
            subscription.lastFailureReason = "Billing plan no longer available"
            subscription.nextChargeAt = subscription.nextChargeAt.plus(1, ChronoUnit.DAYS)
            notifyChargeFailed(subscription, planName = null)
            merchantBillingSubscriptionRepository.save(subscription)
            return false
        }
        val merchant = merchantRepository.findById(subscription.merchantId).orElse(null)
        val customerAccount = accountRepository.findByUserIdAndType(subscription.customerId, AccountType.MAIN)
        val merchantAccount = merchant?.let { accountRepository.findById(it.accountId).orElse(null) }

        val succeeded = if (merchant == null || customerAccount == null || merchantAccount == null) {
            subscription.lastFailureReason = "Merchant or account no longer available"
            false
        } else {
            try {
                chargeExecutor.execute(subscription, resolvedPlan, merchant, customerAccount, merchantAccount)
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
        if (!succeeded) {
            notifyChargeFailed(subscription, planName = resolvedPlan.name)
        }
        merchantBillingSubscriptionRepository.save(subscription)
        return succeeded
    }

    // Real Toss Payments billing-failure alert -- same real, sourced convention
    // ProductSubscriptionService.notifyDeliveryFailed / BillAutoPayProcessor
    // .notifyAutoPayFailed / AutoTransferService.notifyTransferFailed /
    // ScheduledTransferService.notifyTransferFailed already establish (see any of their
    // own doc comments): itunda's recurring/scheduled-charge failure paths previously
    // only ever recorded the failure silently, never told the customer. Purely a
    // best-effort side effect wrapped in its own try/catch -- never allowed to affect
    // the real save. Safe by construction: chargeOne is deliberately NOT @Transactional
    // (its own 2026-08-17 §118 fix -- the real charge attempt is a genuine cross-bean
    // call to MerchantBillingChargeExecutor, which is fully @Transactional on its own),
    // so a failing notification save can never poison the real subscription bookkeeping.
    // "We'll try again next cycle" wording matches AutoTransferService's message, not
    // ScheduledTransferService's terminal one -- a MerchantBillingSubscription stays
    // ACTIVE and keeps recurring after a failed charge, it never moves to a FAILED
    // status the way a one-time ScheduledTransfer does.
    private fun notifyChargeFailed(subscription: MerchantBillingSubscription, planName: String?) {
        try {
            val title = "Subscription payment failed"
            val subject = planName?.let { "your \"$it\" subscription" } ?: "your subscription"
            val body = "We couldn't charge $subject: ${subscription.lastFailureReason}. We'll try again next cycle."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = subscription.customerId, type = "MERCHANT_BILLING_FAILED",
                    title = title, body = body, isRead = false, createdAt = Instant.now(),
                    dataJson = "{\"subscriptionId\":\"${subscription.id}\"}",
                ),
            )
            pushNotificationService.sendToUser(subscription.customerId, title, body, mapOf("subscriptionId" to subscription.id))
        } catch (e: Exception) {
            log.warn("Could not send merchant-billing-failure notification for {}", subscription.id, e)
        }
    }
}
