package rw.itunda.commerce

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.OrderReturnRequest
import rw.itunda.core.domain.OrderReturnStatus
import rw.itunda.core.domain.OrderReturnType
import rw.itunda.core.domain.OrderStatus
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
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
class ReturnRequestNotSellerException(message: String) : RuntimeException(message)
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
) {
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
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = merchant.ownerUserId, type = "COMMERCE_RETURN_REQUESTED",
                    title = "New ${type.name.lowercase()} request", body = "A buyer requested a ${type.name.lowercase()} for order ${order.id}.",
                    isRead = false, createdAt = Instant.now(), dataJson = "{\"orderId\":\"${order.id}\",\"returnRequestId\":\"${request.id}\"}",
                ),
            )
        }
        return request
    }

    fun getMyReturnRequests(buyerId: String, pageable: Pageable): Page<OrderReturnRequest> =
        orderReturnRequestRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId, pageable)

    fun getMerchantReturnQueue(ownerUserId: String, pageable: Pageable): Page<OrderReturnRequest> {
        val merchant = merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw ReturnOrderNotFoundException("This account is not registered as a merchant")
        return orderReturnRequestRepository.findByMerchantIdAndStatusOrderByCreatedAtAsc(merchant.id, OrderReturnStatus.REQUESTED, pageable)
    }

    /**
     * Seller-only decision. An approved RETURN reuses `OrderService.cancelOrder`'s exact
     * reversing-ledger-entry technique against the order's own original transaction --
     * see this class's own doc comment for why. An approved EXCHANGE moves no money.
     */
    @Transactional
    fun decide(ownerUserId: String, returnRequestId: String, approve: Boolean): OrderReturnRequest {
        val request = orderReturnRequestRepository.findById(returnRequestId)
            .orElseThrow { ReturnRequestNotFoundException("Return request not found") }
        val merchant = merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw ReturnOrderNotFoundException("This account is not registered as a merchant")
        if (request.merchantId != merchant.id) {
            throw ReturnRequestNotSellerException("This return request does not belong to your store")
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

        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = request.buyerId, type = "COMMERCE_RETURN_DECIDED",
                title = if (approve) "${request.type.name.lowercase().replaceFirstChar { it.uppercase() }} approved" else "${request.type.name.lowercase().replaceFirstChar { it.uppercase() }} rejected",
                body = if (approve && request.type == OrderReturnType.RETURN) "Your refund has been processed." else if (approve) "The seller approved your exchange." else "The seller rejected your request.",
                isRead = false, createdAt = Instant.now(), dataJson = "{\"orderId\":\"${request.orderId}\",\"returnRequestId\":\"${request.id}\"}",
            ),
        )
        return saved
    }
}
