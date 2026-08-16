package rw.itunda.commerce

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.ProductSubscription
import rw.itunda.core.domain.ProductSubscriptionStatus
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.ProductSubscriptionRepository
import rw.itunda.core.repository.WalletRepository
import rw.itunda.merchant.ShoppingCashbackService
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class ProductSubscriptionNotFoundException(message: String) : RuntimeException(message)
class InvalidProductSubscriptionException(message: String) : RuntimeException(message)
class ProductSubscriptionProductNotFoundException(message: String) : RuntimeException(message)

/**
 * Real Coupang 정기배송 (subscribe & save)-style recurring product delivery -- see
 * ProductSubscription.kt's own doc comment for the full sourced account. Execution
 * reuses `OrderService.placeOrder` unmodified for the real order/delivery itself, and
 * `ShoppingCashbackService.awardCashback` unmodified for the real 5% discount rebate --
 * a subscribed delivery is not a new kind of money movement or fulfillment, just a
 * different trigger for two already-real, already-proven mechanisms.
 */
@Service
class ProductSubscriptionService(
    private val productSubscriptionRepository: ProductSubscriptionRepository,
    private val merchantRepository: MerchantRepository,
    private val merchantProductRepository: MerchantProductRepository,
    private val walletRepository: WalletRepository,
    private val orderService: OrderService,
    private val shoppingCashbackService: ShoppingCashbackService,
    private val rateLimiter: RateLimiter,
) {
    private val log = LoggerFactory.getLogger(ProductSubscriptionService::class.java)

    companion object {
        const val MAX_INTERVAL_DAYS = 180
        val SUBSCRIPTION_DISCOUNT_RATE = java.math.BigDecimal("0.05")
    }

    /**
     * The first cycle happens immediately (matching every other recurring feature in
     * this codebase, e.g. `MerchantBillingService.subscribe`) -- a failed first
     * delivery must fail the whole subscribe attempt, not leave a "successfully
     * subscribed" row behind a silently failed order, so `placeOrder`'s own exceptions
     * are left to propagate and roll back this `@Transactional` method entirely.
     */
    @Transactional
    fun subscribe(customerId: String, merchantId: String, productId: String, quantity: Int, intervalDays: Int, deliveryAddress: String): ProductSubscription {
        if (quantity <= 0) throw InvalidProductSubscriptionException("Quantity must be greater than zero")
        if (intervalDays < 1 || intervalDays > MAX_INTERVAL_DAYS) throw InvalidProductSubscriptionException("Interval must be between 1 and $MAX_INTERVAL_DAYS days")

        rateLimiter.checkLimit("productsubscription:subscribe:$customerId", limit = 20, window = Duration.ofHours(1))

        val product = merchantProductRepository.findById(productId).orElseThrow { ProductSubscriptionProductNotFoundException("Product not found") }
        if (product.merchantId != merchantId) throw ProductSubscriptionProductNotFoundException("Product not found")

        val orderDetail = orderService.placeOrder(customerId, merchantId, listOf(OrderItemRequest(productId, quantity)), deliveryAddress)

        val buyerWallet = walletRepository.findByUserIdAndType(customerId, WalletType.MAIN)
        val merchant = merchantRepository.findById(merchantId).orElse(null)
        if (buyerWallet != null && merchant != null) {
            try {
                shoppingCashbackService.awardCashback(buyerWallet, orderDetail.order.totalAmount, merchant.businessName, SUBSCRIPTION_DISCOUNT_RATE)
            } catch (e: Exception) {
                // Non-critical -- the real order already completed successfully; a
                // failed discount rebate must never undo a real delivery that was
                // already placed, same "auxiliary side-effect can't block real money
                // movement" discipline ShoppingCashbackService's own doc comment names.
                log.error("Subscription discount rebate failed for a successfully placed order", e)
            }
        }

        return productSubscriptionRepository.save(
            ProductSubscription(
                id = "productsub_${UUID.randomUUID()}", customerId = customerId, merchantId = merchantId, productId = productId,
                quantity = quantity, intervalDays = intervalDays, deliveryAddress = deliveryAddress,
                nextDeliveryAt = Instant.now().plus(intervalDays.toLong(), ChronoUnit.DAYS),
                lastDeliveredAt = Instant.now(), deliveryCount = 1,
            ),
        )
    }

    fun getMine(customerId: String): List<ProductSubscription> = productSubscriptionRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)

    fun pause(customerId: String, id: String): ProductSubscription {
        val subscription = productSubscriptionRepository.findByIdAndCustomerId(id, customerId) ?: throw ProductSubscriptionNotFoundException("Subscription not found")
        subscription.status = ProductSubscriptionStatus.PAUSED
        return productSubscriptionRepository.save(subscription)
    }

    fun resume(customerId: String, id: String): ProductSubscription {
        val subscription = productSubscriptionRepository.findByIdAndCustomerId(id, customerId) ?: throw ProductSubscriptionNotFoundException("Subscription not found")
        subscription.status = ProductSubscriptionStatus.ACTIVE
        // Real re-arm, not an instant catch-up delivery -- resuming today re-anchors to
        // a fresh full interval from now, same convention AutoTransferService.resume
        // already established, never firing immediately just because it was paused.
        subscription.nextDeliveryAt = Instant.now().plus(subscription.intervalDays.toLong(), ChronoUnit.DAYS)
        return productSubscriptionRepository.save(subscription)
    }

    fun cancel(customerId: String, id: String): ProductSubscription {
        val subscription = productSubscriptionRepository.findByIdAndCustomerId(id, customerId) ?: throw ProductSubscriptionNotFoundException("Subscription not found")
        subscription.status = ProductSubscriptionStatus.CANCELLED
        subscription.cancelledAt = Instant.now()
        return productSubscriptionRepository.save(subscription)
    }

    fun getDueForExecution(): List<ProductSubscription> =
        productSubscriptionRepository.findByStatusAndNextDeliveryAtLessThanEqual(ProductSubscriptionStatus.ACTIVE, Instant.now())

    // Real recurring delivery -- returns false (never throws) on a genuine, honest
    // failure (insufficient funds, product no longer available) so the scheduler's
    // per-item loop is never blocked by one bad subscription, same discipline
    // AutoTransferService.executeOne/MerchantBillingService.chargeOne already
    // established. A failed delivery is skipped, not retried same-cycle: the schedule
    // still advances to the next real occurrence.
    //
    // Deliberately NOT @Transactional itself (2026-08-17 fix) -- orderService.placeOrder
    // below is a separately-proxied bean, already fully @Transactional on its own. If
    // this method were also @Transactional, a real exception thrown from placeOrder
    // would mark THIS method's own ambient transaction rollback-only at the moment it
    // throws -- catching it in the try/catch below would not undo that mark, and the
    // subscription.save() at the end would fail with a real UnexpectedRollbackException
    // even though the failure was already handled gracefully. Same root cause as
    // MerchantBillingService.chargeOne's fix -- see MerchantBillingChargeExecutor's own
    // doc comment for the full account. placeOrder remains fully atomic on its own via
    // its own @Transactional annotation; productSubscriptionRepository.save below is
    // independently atomic via Spring Data's implicit per-call transaction.
    fun executeOne(subscription: ProductSubscription): Boolean {
        val succeeded = try {
            val orderDetail = orderService.placeOrder(
                subscription.customerId, subscription.merchantId, listOf(OrderItemRequest(subscription.productId, subscription.quantity)), subscription.deliveryAddress,
            )
            val buyerWallet = walletRepository.findByUserIdAndType(subscription.customerId, WalletType.MAIN)
            val merchant = merchantRepository.findById(subscription.merchantId).orElse(null)
            if (buyerWallet != null && merchant != null) {
                try {
                    shoppingCashbackService.awardCashback(buyerWallet, orderDetail.order.totalAmount, merchant.businessName, SUBSCRIPTION_DISCOUNT_RATE)
                } catch (e: Exception) {
                    log.error("Subscription discount rebate failed for subscription {}", subscription.id, e)
                }
            }
            subscription.lastFailureReason = null
            subscription.deliveryCount += 1
            subscription.lastDeliveredAt = Instant.now()
            true
        } catch (e: InsufficientFundsException) {
            subscription.lastFailureReason = "Insufficient balance"
            false
        } catch (e: Exception) {
            log.error("Subscription delivery {} failed with an unexpected error", subscription.id, e)
            subscription.lastFailureReason = "Couldn't complete this delivery"
            false
        }
        subscription.nextDeliveryAt = subscription.nextDeliveryAt.plus(subscription.intervalDays.toLong(), ChronoUnit.DAYS)
        productSubscriptionRepository.save(subscription)
        return succeeded
    }
}
