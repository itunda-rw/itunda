package rw.itunda.commerce

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Order
import rw.itunda.core.domain.OrderItem
import rw.itunda.core.domain.OrderStatus
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.WalletType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.OrderItemRepository
import rw.itunda.core.repository.OrderRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

class MerchantNotFoundException(message: String) : RuntimeException(message)
class MerchantNoWalletException(message: String) : RuntimeException(message)
class BuyerNoWalletException(message: String) : RuntimeException(message)
class EmptyOrderException(message: String) : RuntimeException(message)
class InvalidDeliveryAddressException(message: String) : RuntimeException(message)
class InvalidQuantityException(message: String) : RuntimeException(message)
class OrderProductNotFoundException(message: String) : RuntimeException(message)
class SelfOrderException(message: String) : RuntimeException(message)
class OrderNotFoundException(message: String) : RuntimeException(message)
class InvalidOrderStatusTransitionException(message: String) : RuntimeException(message)

data class OrderItemRequest(val productId: String, val quantity: Int)
data class OrderDetail(val order: Order, val items: List<OrderItem>)

/**
 * Real Coupang-style multi-item checkout -- the third and last of the three new
 * "super app" phases named in the 2026-07-18 goal expansion, built last since it's the
 * largest and most operationally complex (real multi-item carts, real delivery status)
 * of the three, and reuses two things the other two phases (or earlier sessions) already
 * proved out: the real `Merchant`/`MerchantProduct` catalog (Toss Place) as the seller
 * side, and the exact same real wallet-to-wallet ledger movement `MerchantService
 * .collect()` already established for QR payments -- this is not a new payment
 * mechanism, just a multi-line-item version of the same real money movement.
 *
 * Real, not invented: product prices are read from the real, current `MerchantProduct`
 * row at checkout time (never trusted from the client -- a client-supplied price would
 * be a real price-tampering vulnerability), then snapshotted onto `OrderItem` so a
 * later catalog price change doesn't retroactively change a paid order's receipt.
 *
 * Honestly scoped: see Order.kt's own doc comment for why delivery status is real but
 * self-declared by the merchant, not a real third-party courier integration. Also
 * deliberately excludes order cancellation/refunds -- a genuinely separate feature (the
 * same reversing-ledger-entry technique `SupportService.reverseTransaction` already
 * established would be the right shape for it, just not attempted in this pass) --
 * status only ever moves forward, PLACED -> PACKED -> SHIPPED -> DELIVERED.
 */
@Service
class OrderService(
    private val merchantRepository: MerchantRepository,
    private val merchantProductRepository: MerchantProductRepository,
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val transactionRepository: TransactionRepository,
    private val fraudRuleEngine: FraudRuleEngine,
) {
    // Same real Toss Payments fee-schedule reasoning MerchantService.feeRate's own
    // comment gives -- one flat rate in the middle of Toss's published 0.8%-1.8% range,
    // reused rather than inventing a second number for what is, underneath, the same
    // kind of wallet-to-wallet merchant collection.
    private val feeRate = BigDecimal("0.015")

    private val statusOrder = listOf(OrderStatus.PLACED, OrderStatus.PACKED, OrderStatus.SHIPPED, OrderStatus.DELIVERED)

    @Transactional
    fun placeOrder(buyerId: String, merchantId: String, items: List<OrderItemRequest>, deliveryAddress: String): OrderDetail {
        if (items.isEmpty()) {
            throw EmptyOrderException("An order needs at least one item")
        }
        val trimmedAddress = deliveryAddress.trim()
        if (trimmedAddress.isEmpty()) {
            throw InvalidDeliveryAddressException("A delivery address is required")
        }
        val merchant = merchantRepository.findById(merchantId)
            .orElseThrow { MerchantNotFoundException("Merchant not found") }
        if (merchant.ownerUserId == buyerId) {
            throw SelfOrderException("Cannot order from your own store")
        }

        val merchantWallet = walletRepository.findById(merchant.walletId)
            .orElseThrow { MerchantNoWalletException("Merchant settlement wallet not found") }
        val buyerWallet = walletRepository.findByUserIdAndType(buyerId, WalletType.MAIN)
            ?: throw BuyerNoWalletException("No wallet found for this account")

        // Real prices read from the live catalog row -- never trusted from the client
        // (see this class's own doc comment on why) -- and snapshotted onto each
        // OrderItem below so the receipt stays accurate even if the catalog changes later.
        data class Resolved(val productId: String, val name: String, val unitPrice: BigDecimal, val quantity: Int)
        val resolved = items.map { req ->
            if (req.quantity <= 0) {
                throw InvalidQuantityException("Quantity must be at least 1")
            }
            val product = merchantProductRepository.findById(req.productId)
                .orElseThrow { OrderProductNotFoundException("Product not found") }
            if (product.merchantId != merchantId || !product.active) {
                // Same "don't reveal a resource exists" 404, not a more specific error --
                // a product from a different merchant or a deactivated one is equally
                // "not orderable here" from this order's point of view.
                throw OrderProductNotFoundException("Product not found")
            }
            Resolved(product.id, product.name, product.price, req.quantity)
        }
        val totalAmount = resolved.fold(BigDecimal.ZERO) { acc, r -> acc + r.unitPrice.multiply(BigDecimal(r.quantity)) }
        val fee = totalAmount.multiply(feeRate).setScale(2, RoundingMode.HALF_UP)
        val netToMerchant = totalAmount.subtract(fee)

        val result = ledgerService.postLedgerTransaction(
            buyerWallet.currency,
            listOf(
                LedgerLeg(buyerWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, totalAmount, "Order - ${merchant.businessName}"),
                LedgerLeg(merchantWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, netToMerchant, "Order collection - ${merchant.businessName}"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, fee, "Order fee - ${merchant.businessName}"),
            ),
        )

        val transaction = Transaction(
            id = result.transactionId,
            referenceNumber = "ORDER${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
            senderId = buyerId,
            recipientId = merchant.ownerUserId,
            fromWalletId = buyerWallet.id,
            toWalletId = merchantWallet.id,
            amount = totalAmount,
            fee = fee,
            currency = buyerWallet.currency,
            type = TransactionType.PAYMENT,
            status = TransactionStatus.COMPLETED,
            description = "Order - ${merchant.businessName}",
            channel = "ONLINE_ORDER",
            completedAt = Instant.now(),
        )
        // Same evaluate-before-save ordering P2pService/MerchantService/PayrollService
        // already established -- evaluating after save would let this transaction match
        // itself as its own prior history.
        fraudRuleEngine.evaluate(buyerId, merchant.ownerUserId, totalAmount, transaction.id)
        transactionRepository.save(transaction)

        val order = orderRepository.save(
            Order(
                id = "order_${UUID.randomUUID()}", buyerId = buyerId, merchantId = merchantId,
                deliveryAddress = trimmedAddress, totalAmount = totalAmount, fee = fee, transactionId = result.transactionId,
            ),
        )
        val orderItems = resolved.map {
            OrderItem(
                id = "order_item_${UUID.randomUUID()}", orderId = order.id, productId = it.productId,
                productName = it.name, unitPrice = it.unitPrice, quantity = it.quantity,
            )
        }
        orderItemRepository.saveAll(orderItems)

        return OrderDetail(order, orderItems)
    }

    fun getMyOrders(buyerId: String, pageable: Pageable): Page<Order> =
        orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId, pageable)

    fun getMerchantOrders(ownerUserId: String, pageable: Pageable): Page<Order> {
        val merchant = merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw MerchantNotFoundException("This account is not registered as a merchant")
        return orderRepository.findByMerchantIdOrderByCreatedAtDesc(merchant.id, pageable)
    }

    /** Either the real buyer or the real merchant owner can view an order's items --
     * anyone else gets a real 404, not a 403 that would confirm the order exists. */
    fun getOrderDetail(requesterId: String, orderId: String): OrderDetail {
        val order = orderRepository.findById(orderId).orElseThrow { OrderNotFoundException("Order not found") }
        val merchant = merchantRepository.findById(order.merchantId).orElse(null)
        val isBuyer = order.buyerId == requesterId
        val isSeller = merchant?.ownerUserId == requesterId
        if (!isBuyer && !isSeller) {
            throw OrderNotFoundException("Order not found")
        }
        return OrderDetail(order, orderItemRepository.findByOrderId(orderId))
    }

    /** Seller-only, forward-only status progression -- see this class's own doc
     * comment for why cancellation/refunds are deliberately not supported here. */
    @Transactional
    fun updateOrderStatus(ownerUserId: String, orderId: String, newStatus: OrderStatus): Order {
        val merchant = merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw MerchantNotFoundException("This account is not registered as a merchant")
        val order = orderRepository.findById(orderId).orElseThrow { OrderNotFoundException("Order not found") }
        if (order.merchantId != merchant.id) {
            throw OrderNotFoundException("Order not found")
        }
        val currentIndex = statusOrder.indexOf(order.status)
        val newIndex = statusOrder.indexOf(newStatus)
        if (newIndex != currentIndex + 1) {
            throw InvalidOrderStatusTransitionException(
                "Cannot move from ${order.status} to $newStatus -- status can only advance one step at a time",
            )
        }
        order.status = newStatus
        order.updatedAt = Instant.now()
        return orderRepository.save(order)
    }
}
