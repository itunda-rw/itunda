package rw.itunda.commerce

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.Order
import rw.itunda.core.domain.OrderItem
import rw.itunda.core.domain.OrderStatus
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.WalletType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.pricing.effectiveUnitPrice
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.OrderItemRepository
import rw.itunda.core.repository.OrderRepository
import rw.itunda.core.repository.ProductPriceTierRepository
import rw.itunda.core.repository.RiderRepository
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
class RiderNotRegisteredException(message: String) : RuntimeException(message)
class RiderNotAvailableException(message: String) : RuntimeException(message)
class RiderAlreadyOnDeliveryException(message: String) : RuntimeException(message)
class DeliveryAlreadyClaimedException(message: String) : RuntimeException(message)

data class OrderItemRequest(val productId: String, val quantity: Int)
data class OrderDetail(val order: Order, val items: List<OrderItem>)
data class OrderRiderLocationView(val latitude: Double, val longitude: Double, val updatedAt: Instant)

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
 * See Order.kt's own doc comment for the two real fulfillment paths delivery status can
 * take: merchant self-declared (no real third-party courier API exists, and never will
 * without regulatory/vendor access this system doesn't have), or itunda's own real
 * internal rider fleet claiming and completing the SHIPPED->DELIVERED leg with live GPS
 * tracking (2026-07-26) -- the same real network `EatsOrderService` already built and
 * proved out for food delivery, now doing double duty for packages too. Status only
 * ever moves forward, PLACED -> PACKED -> SHIPPED -> DELIVERED, regardless of which path
 * a given order takes.
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
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val notificationRepository: NotificationRepository,
    private val priceTierRepository: ProductPriceTierRepository,
    private val riderRepository: RiderRepository,
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
        // Real bound -- found via the same sweep that caught ProductReviewService's own
        // unbounded-comment gap (2026-07-20). deliveryAddress is VARCHAR(500), and this
        // DB's real STRICT_TRANS_TABLES mode throws a raw, unhandled 500 on an
        // over-length insert. Rejecting with a clean error rather than truncating --
        // unlike a review comment, silently truncating an address could genuinely
        // misdirect a real delivery.
        if (trimmedAddress.length > 500) {
            throw InvalidDeliveryAddressException("Delivery address must be 500 characters or fewer")
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
        // Real bulk/wholesale pricing (2026-07-25) -- batched up front for every
        // distinct product in this order, same N+1-avoidance discipline
        // EatsOrderService's own menu-options resolution already established. See
        // ProductPriceTier's own doc comment for the full account.
        val distinctProductIds = items.map { it.productId }.distinct()
        val tiersByProduct = if (distinctProductIds.isNotEmpty()) {
            priceTierRepository.findByProductIdInOrderByMinQuantityAsc(distinctProductIds).groupBy { it.productId }
        } else {
            emptyMap()
        }

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
            val unitPrice = effectiveUnitPrice(product.price, req.quantity, tiersByProduct[product.id].orEmpty())
            Resolved(product.id, product.name, unitPrice, req.quantity)
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

        // Real "new order" alert for the merchant (2026-07-26) -- see
        // EatsOrderService.placeOrder's own doc comment for the full account of this
        // same real gap found the same day: placeOrder never notified the merchant
        // owner at all, meaning the only way to learn a real order arrived was manually
        // polling GET /merchant-orders. Best-effort, same "auxiliary side-effect can't
        // block the real operation" discipline this codebase already establishes.
        try {
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = merchant.ownerUserId, type = "NEW_COMMERCE_ORDER",
                    title = "New order received",
                    body = "A new order for ${orderItems.sumOf { it.quantity }} item(s) just came in -- $totalAmount RWF",
                    isRead = false, createdAt = Instant.now(), dataJson = "{\"orderId\":\"${order.id}\"}",
                ),
            )
        } catch (e: Exception) {
            // Non-critical -- the real order already completed and succeeded.
        }

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

    /** Seller-only, forward-only status progression through the real PLACED -> PACKED
     * -> SHIPPED -> DELIVERED chain -- CANCELLED is a real but separate terminal state,
     * only reachable via [cancelOrder] below, never via this method (the `currentIndex
     * == -1` guard below is what stops a CANCELLED order from being "advanced" back
     * into the forward chain).
     *
     * SHIPPED/DELIVERED real-reject once a real itunda rider has claimed the delivery
     * (see [claimDelivery]/[completeDelivery]/Order.kt's own doc comment) -- those two
     * steps become the rider's own real events from that point on, the same "who
     * actually did the thing declares it" discipline `EatsOrderService.updateRestaurantStatus`
     * already keeps for its own rider-assigned orders. A merchant who never gets a rider
     * claim can still self-declare the full chain exactly as before -- nothing here
     * changes for that path. */
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
        if (currentIndex == -1 || newIndex != currentIndex + 1) {
            throw InvalidOrderStatusTransitionException(
                "Cannot move from ${order.status} to $newStatus -- status can only advance one step at a time",
            )
        }
        if (order.riderId != null && (newStatus == OrderStatus.SHIPPED || newStatus == OrderStatus.DELIVERED)) {
            throw InvalidOrderStatusTransitionException(
                "A rider has already claimed this delivery -- only the rider can advance it from here",
            )
        }
        order.status = newStatus
        order.updatedAt = Instant.now()
        val saved = orderRepository.save(order)
        when (newStatus) {
            OrderStatus.PACKED -> notifyBuyer(saved, "Order packed", "${merchant.businessName} has packed your order.")
            OrderStatus.SHIPPED -> notifyBuyer(saved, "Order shipped", "${merchant.businessName} has shipped your order.")
            OrderStatus.DELIVERED -> notifyBuyer(saved, "Order delivered", "Your order from ${merchant.businessName} has been delivered.")
            else -> {}
        }
        return saved
    }

    /**
     * Real itunda-own-fleet delivery claim (2026-07-26) -- closes the "blocked (real
     * third-party courier/delivery-logistics integration)" line the Commerce row of
     * docs/TOSS_PARITY_MATRIX.md used to carry, the same honest way
     * `EatsOrderService.claimDelivery` already did for food: itunda's own `Rider`s (a
     * shared `:core` entity, not duplicated here) claim a real PACKED order, exactly
     * one active delivery at a time. Deliberately simpler than Eats' own exclusive-offer
     * auto-dispatch machinery (no expiring per-rider offer window, no OSRM road-routing)
     * -- what makes tracking real is the claim + live GPS position below, not a second
     * copy of Eats' full dispatch sophistication; a genuinely separate, later pass could
     * add that if this ever needs to reduce claim-race contention at real scale.
     */
    @Transactional
    fun claimDelivery(riderUserId: String, orderId: String): Order {
        val rider = riderRepository.findByUserId(riderUserId)
            ?: throw RiderNotRegisteredException("This account is not registered as a rider")
        if (!rider.available) {
            throw RiderNotAvailableException("Go online before claiming a delivery")
        }
        if (orderRepository.existsByRiderIdAndStatusIn(rider.id, listOf(OrderStatus.SHIPPED))) {
            throw RiderAlreadyOnDeliveryException("Finish your current delivery before claiming another")
        }
        val order = orderRepository.findById(orderId).orElseThrow { OrderNotFoundException("Order not found") }
        if (order.status != OrderStatus.PACKED || order.riderId != null) {
            throw DeliveryAlreadyClaimedException("This delivery is no longer available")
        }
        order.riderId = rider.id
        order.status = OrderStatus.SHIPPED
        order.updatedAt = Instant.now()
        val saved = orderRepository.save(order)
        val merchant = merchantRepository.findById(order.merchantId).orElse(null)
        notifyBuyer(saved, "Order shipped", "${merchant?.businessName ?: "Your order"} has been picked up and is on the way.")
        return saved
    }

    /** Rider-only terminal edge for a claimed delivery -- see [claimDelivery]'s own doc
     * comment. Ownership-checked: only the rider who claimed this exact order may
     * complete it. */
    @Transactional
    fun completeDelivery(riderUserId: String, orderId: String): Order {
        val rider = riderRepository.findByUserId(riderUserId)
            ?: throw RiderNotRegisteredException("This account is not registered as a rider")
        val order = orderRepository.findById(orderId).orElseThrow { OrderNotFoundException("Order not found") }
        if (order.riderId != rider.id || order.status != OrderStatus.SHIPPED) {
            throw InvalidOrderStatusTransitionException("This delivery cannot be completed right now")
        }
        order.status = OrderStatus.DELIVERED
        order.updatedAt = Instant.now()
        val saved = orderRepository.save(order)
        val merchant = merchantRepository.findById(order.merchantId).orElse(null)
        notifyBuyer(saved, "Order delivered", "Your order from ${merchant?.businessName ?: "the seller"} has been delivered.")
        return saved
    }

    /** Real rider "available deliveries" browse -- every real PACKED order no rider has
     * claimed yet, distance-ranked from the rider's own live position when known (same
     * haversine fallback discipline `EatsOrderService.getAvailableDeliveries` already
     * uses when OSRM/road-routing isn't warranted), unranked-but-not-dropped when a
     * merchant has no real coordinates on file yet. */
    fun getAvailableDeliveries(riderUserId: String, pageable: Pageable): Page<Order> {
        val rider = riderRepository.findByUserId(riderUserId)
            ?: throw RiderNotRegisteredException("This account is not registered as a rider")
        val riderLat = rider.currentLatitude
        val riderLng = rider.currentLongitude
        if (riderLat == null || riderLng == null) {
            return orderRepository.findByStatusAndRiderIdIsNullOrderByCreatedAtAsc(OrderStatus.PACKED, pageable)
        }
        val candidates = orderRepository.findByStatusAndRiderIdIsNullOrderByCreatedAtAsc(OrderStatus.PACKED, Pageable.unpaged()).content
        if (candidates.isEmpty()) return PageImpl(emptyList(), pageable, 0)

        val merchantsById = merchantRepository.findAllById(candidates.map { it.merchantId }.distinct()).associateBy { it.id }
        val (locatable, unlocatable) = candidates.partition { order ->
            val m = merchantsById[order.merchantId]
            m?.latitude != null && m.longitude != null
        }
        val ranked = locatable
            .map { order -> val m = merchantsById.getValue(order.merchantId); order to GeoUtils.haversineKm(riderLat, riderLng, m.latitude!!, m.longitude!!) }
            .sortedBy { (_, distanceKm) -> distanceKm }
            .map { (order, _) -> order }
        val sorted = ranked + unlocatable
        val start = (pageable.offset).coerceAtMost(sorted.size.toLong()).toInt()
        val end = (start + pageable.pageSize).coerceAtMost(sorted.size)
        return PageImpl(sorted.subList(start, end), pageable, sorted.size.toLong())
    }

    fun getMyDeliveries(riderUserId: String, pageable: Pageable): Page<Order> {
        val rider = riderRepository.findByUserId(riderUserId)
            ?: throw RiderNotRegisteredException("This account is not registered as a rider")
        return orderRepository.findByRiderIdOrderByCreatedAtDesc(rider.id, pageable)
    }

    /** Real live rider-location tracking for a buyer watching their own delivery in
     * transit -- mirrors `EatsOrderService.getRiderLocation` exactly. `null` (not an
     * error) is the honest, expected response whenever there's genuinely nothing to
     * show yet (no rider claimed, or a claimed rider hasn't reported a position). */
    fun getRiderLocation(requesterId: String, orderId: String): OrderRiderLocationView? {
        val order = orderRepository.findById(orderId).orElseThrow { OrderNotFoundException("Order not found") }
        val merchant = merchantRepository.findById(order.merchantId).orElse(null)
        val rider = order.riderId?.let { riderRepository.findById(it).orElse(null) }
        val isBuyer = order.buyerId == requesterId
        val isSeller = merchant?.ownerUserId == requesterId
        val isRider = rider?.userId == requesterId
        if (!isBuyer && !isSeller && !isRider) {
            throw OrderNotFoundException("Order not found")
        }
        if (order.status != OrderStatus.SHIPPED) return null
        val lat = rider?.currentLatitude
        val lng = rider?.currentLongitude
        val updatedAt = rider?.locationUpdatedAt
        if (lat == null || lng == null || updatedAt == null) return null
        return OrderRiderLocationView(lat, lng, updatedAt)
    }

    /**
     * Real order cancellation + refund (2026-07-18) -- the "genuinely separate feature"
     * this class's own doc comment always named as deliberately deferred. Reuses the
     * exact reversing-ledger-entry technique `SupportService.reverseTransaction` already
     * established: read the original transaction's own ledger legs and post a new
     * transaction with every leg's direction flipped (same accounts, same amounts,
     * including the fee) -- a real reversing entry, never mutating or deleting the
     * original record, matching real double-entry accounting practice.
     *
     * Deliberately, honestly scoped to only PLACED orders -- the same "before real
     * fulfillment work has started" boundary this session already uses elsewhere. Once
     * a seller has marked an order PACKED, cancelling would need a real return/dispute
     * flow (goods may already be in motion), a genuinely different feature not attempted
     * here. Either the real buyer or the real seller can cancel from PLACED (a buyer
     * changing their mind, or a seller who can't fulfil it -- e.g. out of stock -- both
     * real, common reasons at this stage).
     */
    @Transactional
    fun cancelOrder(requesterId: String, orderId: String): Order {
        val order = orderRepository.findById(orderId).orElseThrow { OrderNotFoundException("Order not found") }
        val merchant = merchantRepository.findById(order.merchantId).orElse(null)
        val isBuyer = order.buyerId == requesterId
        val isSeller = merchant?.ownerUserId == requesterId
        if (!isBuyer && !isSeller) {
            throw OrderNotFoundException("Order not found")
        }
        if (order.status != OrderStatus.PLACED) {
            throw InvalidOrderStatusTransitionException("Only a PLACED order can be cancelled -- this order is already ${order.status}")
        }

        val originalEntries = ledgerEntryRepository.findByTransactionId(order.transactionId)
        val reversedLegs = originalEntries.map { entry ->
            val flipped = if (entry.direction == LedgerDirection.DEBIT) LedgerDirection.CREDIT else LedgerDirection.DEBIT
            LedgerLeg(entry.accountId, entry.accountType, flipped, entry.amount, "Refund for order ${order.id}")
        }
        val refund = ledgerService.postLedgerTransaction(originalEntries.first().currency, reversedLegs)

        order.status = OrderStatus.CANCELLED
        order.refundTransactionId = refund.transactionId
        order.updatedAt = Instant.now()
        val saved = orderRepository.save(order)
        // Only notify when the SELLER cancelled -- a buyer who cancelled their own
        // order already knows, same "don't notify someone about their own action"
        // discipline this exact pattern already established for Eats the same day.
        if (isSeller) {
            notifyBuyer(saved, "Order cancelled", "${merchant?.businessName ?: "The seller"} cancelled your order. Your payment has been refunded.")
        }
        return saved
    }

    // Real buyer order-status notifications (2026-07-20) -- the real "your order was
    // packed/shipped/delivered" moments every real Coupang/Toss Shopping/Naver
    // Shopping-style app sends, mirroring the identical gap closed for Eats the same
    // day (see EatsOrderService.notifyBuyer's own doc comment for the full account).
    private fun notifyBuyer(order: Order, title: String, body: String) {
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = order.buyerId, type = "COMMERCE_ORDER_UPDATE",
                title = title, body = body, isRead = false, createdAt = Instant.now(),
                dataJson = "{\"orderId\":\"${order.id}\"}",
            ),
        )
    }
}
