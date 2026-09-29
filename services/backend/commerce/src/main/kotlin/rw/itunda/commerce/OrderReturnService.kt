package rw.itunda.commerce

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.OrderReturnRequest
import rw.itunda.core.domain.OrderReturnStatus
import rw.itunda.core.domain.OrderReturnType
import rw.itunda.core.domain.OrderStatus
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.OrderRepository
import rw.itunda.core.repository.OrderReturnRequestRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID

class ReturnOrderNotFoundException(message: String) : RuntimeException(message)
class ReturnOrderNotDeliveredException(message: String) : RuntimeException(message)
class ReturnWindowExpiredException(message: String) : RuntimeException(message)
class ReturnAlreadyRequestedException(message: String) : RuntimeException(message)
class InvalidReturnReasonException(message: String) : RuntimeException(message)
class ReturnRequestNotFoundException(message: String) : RuntimeException(message)
class ReturnRequestAlreadyDecidedException(message: String) : RuntimeException(message)

/**
 * Real Coupang-style post-delivery Return & Exchange requests (반품/교환 신청) -- see
 * Coupang's own My Coupang cancel/return/exchange flow, the Coupang Open API's Return/
 * Cancellation Request List Query, and Coupang Marketplace's own seller-university
 * "Product Shipping and Returns" page. Genuinely distinct from `OrderService
 * .cancelOrder`, which is deliberately scoped to only PLACED orders (before real
 * fulfillment work starts) -- this covers the separate real event that only becomes
 * possible once an order is DELIVERED: the buyer wants to send the item back (RETURN)
 * or swap it (EXCHANGE), the seller reviews the request against a real reason code, and
 * an approved RETURN triggers a real refund.
 *
 * Reuses `OrderService.cancelOrder`'s exact reversing-ledger-entry technique for the
 * refund: read the original transaction's own real ledger legs and post a new
 * transaction with every leg's direction flipped, never mutating or deleting the
 * original record -- real double-entry accounting practice, not a special-cased refund
 * primitive. An EXCHANGE never moves money by itself (an approved exchange is a real
 * "the seller will ship a replacement" agreement, not a return-then-rebuy) -- honestly
 * scoped: no automatic re-shipment/re-fulfillment flow exists in this backend (delivery
 * status is self-declared by the merchant, see Order.kt's own doc comment), so an
 * approved EXCHANGE is a real, recorded outcome the seller then fulfils manually the
 * same way they fulfil PACKED/SHIPPED/DELIVERED today.
 *
 * A real 7-day window from delivery, matching Coupang's own real standard return
 * period -- itunda's own honest scoping choice since Coupang's exact window varies by
 * product category and neither sourced account discloses one universal number.
 */
@Service
class OrderReturnService(
    private val orderRepository: OrderRepository,
    private val orderReturnRequestRepository: OrderReturnRequestRepository,
    private val merchantRepository: MerchantRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val ledgerService: LedgerService,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
) {
    private val logger = LoggerFactory.getLogger(OrderReturnService::class.java)

    companion object {
        val RETURN_WINDOW: Duration = Duration.ofDays(7)
        val VALID_REASON_CODES = setOf("DEFECTIVE", "WRONG_ITEM", "NOT_AS_DESCRIBED", "NO_LONGER_NEEDED", "SIZE_FIT", "OTHER")
    }

    @Transactional
    fun requestReturn(buyerId: String, orderId: String, type: OrderReturnType, reasonCode: String, reasonNote: String?): OrderReturnRequest {
        val order = orderRepository.findById(orderId).orElseThrow { ReturnOrderNotFoundException("Order not found") }
        if (order.buyerId != buyerId) {
            // Same "don't reveal a resource exists" 404 discipline OrderService itself
            // already establishes for a non-participant.
            throw ReturnOrderNotFoundException("Order not found")
        }
        if (order.status != OrderStatus.DELIVERED) {
            throw ReturnOrderNotDeliveredException("Only a DELIVERED order can have a return or exchange requested -- this order is ${order.status}")
        }
        if (Duration.between(order.updatedAt, Instant.now()) > RETURN_WINDOW) {
            throw ReturnWindowExpiredException("The real 7-day return window for this order has passed")
        }
        val normalizedReason = reasonCode.trim().uppercase()
        if (normalizedReason !in VALID_REASON_CODES) {
            throw InvalidReturnReasonException("reasonCode must be one of $VALID_REASON_CODES")
        }
        val existing = orderReturnRequestRepository.findByOrderId(orderId)
        if (existing.any { it.status == OrderReturnStatus.REQUESTED }) {
            throw ReturnAlreadyRequestedException("A return or exchange request is already pending review for this order")
        }

        val request = orderReturnRequestRepository.save(
            OrderReturnRequest(
                id = "order_return_${UUID.randomUUID()}", orderId = order.id, buyerId = buyerId, merchantId = order.merchantId,
                type = type, reasonCode = normalizedReason, reasonNote = reasonNote?.take(500),
            ),
        )
        val merchant = merchantRepository.findById(order.merchantId).orElse(null)
        if (merchant != null) {
            val title = "New ${type.name.lowercase()} request"
            val body = "A buyer requested a ${type.name.lowercase()} for order ${order.id}."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = merchant.ownerUserId, type = "COMMERCE_RETURN_REQUESTED",
                    title = title, body = body,
                    isRead = false, createdAt = Instant.now(), dataJson = "{\"orderId\":\"${order.id}\",\"returnRequestId\":\"${request.id}\"}",
                ),
            )
            sendPushAfterCommit(merchant.ownerUserId, title, body, order.id, request.id)
        }
        return request
    }

    fun getMyReturnRequests(buyerId: String, pageable: Pageable): Page<OrderReturnRequest> =
        orderReturnRequestRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId, pageable)

    fun getMerchantReturnQueue(ownerUserId: String, pageable: Pageable): Page<OrderReturnRequest> {
        // Real fix (2026-08-09), found live on a physical device: this used to throw
        // ReturnOrderNotFoundException, which OrderController maps to ApiError("ORDER_NOT_FOUND",
        // ...) -- the wrong code for "not a merchant." Every client (Android/iOS/web) checks
        // specifically for "MERCHANT_NOT_FOUND" to silently hide this section for buyer-only
        // accounts (see ShopScreen.kt's MerchantReturnQueueView doc comment); the mismatched code
        // meant every ordinary customer saw a visible "That couldn't be found." error card
        // instead of the section silently not rendering, exactly the alarming-the-common-case
        // outcome that design was meant to avoid. MerchantNotFoundException (used by this same
        // service's sibling getMerchantOrders-equivalent checks in OrderService.kt) is the
        // correct type -- already mapped to the right code.
        val merchant = merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw MerchantNotFoundException("This account is not registered as a merchant")
        return orderReturnRequestRepository.findByMerchantIdAndStatusOrderByCreatedAtAsc(merchant.id, OrderReturnStatus.REQUESTED, pageable)
    }

    /**
     * Seller-only decision. An approved RETURN reuses `OrderService.cancelOrder`'s exact
     * reversing-ledger-entry technique against the order's own original transaction --
     * see this class's own doc comment for why. An approved EXCHANGE moves no money.
     */
    // Real lost-update fix (2026-09-03) -- see OrderReturnRequestRepository.findByIdForUpdate's
    // own doc comment: this check-then-act-then-refund had no row lock.
    @Transactional
    fun decide(ownerUserId: String, returnRequestId: String, approve: Boolean): OrderReturnRequest {
        val request = orderReturnRequestRepository.findByIdForUpdate(returnRequestId)
            .orElseThrow { ReturnRequestNotFoundException("Return request not found") }
        // Same wrong-exception-type bug as getMerchantReturnQueue above, same fix.
        val merchant = merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw MerchantNotFoundException("This account is not registered as a merchant")
        if (request.merchantId != merchant.id) {
            // Real 404 (not 403) -- found live in a 2026-08-02 audit pass: this used to
            // throw a distinct ReturnRequestNotSellerException mapped to 403 FORBIDDEN,
            // the one IDOR check in this class that broke from the "don't reveal a
            // resource exists to someone who shouldn't see it" discipline every other
            // ownership check here (and across this whole codebase) already follows --
            // a 403 here would let another merchant distinguish "this returnRequestId
            // exists but isn't mine" (403) from "this returnRequestId doesn't exist at
            // all" (404) just by probing ids.
            throw ReturnRequestNotFoundException("Return request not found")
        }
        if (request.status != OrderReturnStatus.REQUESTED) {
            throw ReturnRequestAlreadyDecidedException("This return request has already been ${request.status}")
        }

        if (approve && request.type == OrderReturnType.RETURN) {
            val order = orderRepository.findById(request.orderId).orElseThrow { ReturnOrderNotFoundException("Order not found") }
            val originalEntries = ledgerEntryRepository.findByTransactionId(order.transactionId)
            val reversedLegs = originalEntries.map { entry ->
                val flipped = if (entry.direction == LedgerDirection.DEBIT) LedgerDirection.CREDIT else LedgerDirection.DEBIT
                LedgerLeg(entry.accountId, entry.accountType, flipped, entry.amount, "Return refund for order ${order.id}")
            }
            val refund = ledgerService.postLedgerTransaction(originalEntries.first().currency, reversedLegs)
            request.refundTransactionId = refund.transactionId
        }

        request.status = if (approve) OrderReturnStatus.APPROVED else OrderReturnStatus.REJECTED
        request.decidedAt = Instant.now()
        request.updatedAt = Instant.now()
        val saved = orderReturnRequestRepository.save(request)

        val decidedTitle = if (approve) "${request.type.name.lowercase().replaceFirstChar { it.uppercase() }} approved" else "${request.type.name.lowercase().replaceFirstChar { it.uppercase() }} rejected"
        val decidedBody = if (approve && request.type == OrderReturnType.RETURN) "Your refund has been processed." else if (approve) "The seller approved your exchange." else "The seller rejected your request."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = request.buyerId, type = "COMMERCE_RETURN_DECIDED",
                title = decidedTitle, body = decidedBody,
                isRead = false, createdAt = Instant.now(), dataJson = "{\"orderId\":\"${request.orderId}\",\"returnRequestId\":\"${request.id}\"}",
            ),
        )
        sendPushAfterCommit(request.buyerId, decidedTitle, decidedBody, request.orderId, request.id)
        return saved
    }

    /** Customer-facing return status must match the committed request and refund state. */
    private fun sendPushAfterCommit(userId: String, title: String, body: String, orderId: String, returnRequestId: String) {
        val data = mapOf("orderId" to orderId, "returnRequestId" to returnRequestId)
        val send = {
            try {
                pushNotificationService.sendToUser(userId, title, body, data)
            } catch (e: Exception) {
                logger.warn("Could not send commerce-return push for request {}", returnRequestId, e)
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
