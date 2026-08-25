package rw.itunda.commerce

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.Order
import rw.itunda.core.domain.OrderItem
import rw.itunda.core.domain.OrderStatus
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.AccountType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.pricing.effectiveUnitPrice
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.OrderItemRepository
import rw.itunda.core.repository.OrderRepository
import rw.itunda.core.repository.ProductPriceTierRepository
import rw.itunda.core.repository.RiderRepository
import rw.itunda.core.repository.TimeDealRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID


data class OrderItemRequest(val productId: String, val quantity: Int)
data class OrderDetail(val order: Order, val items: List<OrderItem>)
data class OrderRiderLocationView(val latitude: Double, val longitude: Double, val updatedAt: Instant)

/**
 * Real Coupang-style multi-item checkout -- the third and last of the three new
 * "super app" phases named in the 2026-07-18 goal expansion, built last since it's the
 * largest and most operationally complex (real multi-item carts, real delivery status)
 * of the three, and reuses two things the other two phases (or earlier sessions) already
 * proved out: the real `Merchant`/`MerchantProduct` catalog (Toss Place) as the seller
 * side, and the exact same real account-to-account ledger movement `MerchantService
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
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService,
    private val transactionRepository: TransactionRepository,
    private val fraudRuleEngine: FraudRuleEngine,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val notificationRepository: NotificationRepository,
    private val priceTierRepository: ProductPriceTierRepository,
    private val riderRepository: RiderRepository,
    private val pushNotificationService: PushNotificationService,
    private val timeDealRepository: TimeDealRepository,
    private val affiliateService: AffiliateService,
    private val autoTopUpService: rw.itunda.account.AutoTopUpService,
) {
    private val logger = LoggerFactory.getLogger(OrderService::class.java)

    // Same real Toss Payments fee-schedule reasoning MerchantService.feeRate's own
    // comment gives -- one flat rate in the middle of Toss's published 0.8%-1.8% range,
    // reused rather than inventing a second number for what is, underneath, the same
    // kind of account-to-account merchant collection.
    private val feeRate = BigDecimal("0.015")

    private val statusOrder = listOf(OrderStatus.PLACED, OrderStatus.PACKED, OrderStatus.SHIPPED, OrderStatus.DELIVERED)

    @Transactional
    fun placeOrder(buyerId: String, merchantId: String, items: List<OrderItemRequest>, deliveryAddress: String, referralCode: String? = null): OrderDetail {
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
        // Real Baemin CEO app 영업일시중지/휴무일 설정 enforcement -- see
        // Merchant.isAcceptingOrders/isClosedToday's own doc comments and
        // EatsOrderService.placeOrder's identical checks for the full sourcing. Both
        // flags already gate the Eats (delivery) checkout path and are already
        // surfaced to buyers as a browse-time badge here (ShoppingController's own
        // "isAcceptingOrders"/"closedToday" catalog fields), but nothing in this
        // Commerce checkout -- the one place that actually moves money for this
        // Merchant catalog -- ever re-checked them: a buyer with a stale client, a
        // cached product page, or a direct API call could keep placing real paid
        // orders against a store that explicitly paused or closed for the day. Same
        // "real flag correctly enforced on one write path sharing this Merchant
        // catalog but not this one" gap as MerchantProduct.soldOut/isSurplusDeal had
        // until this session's own earlier fixes.
        if (!merchant.isAcceptingOrders) {
            throw MerchantNotAcceptingOrdersException("This store isn't accepting orders right now")
        }
        if (merchant.isClosedToday()) {
            throw MerchantNotAcceptingOrdersException("This store is closed today")
        }

        val merchantAccount = accountRepository.findById(merchant.accountId)
            .orElseThrow { MerchantNoAccountException("Merchant settlement account not found") }
        // Real Toss Bank/Toss Pay separation (2026-08-21) -- see
        // MerchantService.collect()'s own doc comment for the full sourced
        // architecture. A Commerce order is real merchant collection, same as
        // QR/code payment -- draws from the buyer's itunda Pay money, auto-topped
        // from Bank (then an external linked account) if short at checkout time
        // (see the auto-topup block right before this order's ledger post below).
        var buyerAccount = accountRepository.findByUserIdAndType(buyerId, AccountType.PAY)
            ?: throw BuyerNoAccountException("No itunda Pay money found for this account")

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

        data class Resolved(val product: MerchantProduct, val productId: String, val name: String, val unitPrice: BigDecimal, val quantity: Int)
        val now = Instant.now()
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
            // Real Baemin CEO app/DoorDash-style "86" enforcement -- MerchantProduct.
            // soldOut's own doc comment explicitly says a sold-out item stays visible on
            // the customer-facing menu but must be "blocked from new orders until the
            // merchant flips it back". That toggle (MerchantProductService.setSoldOut,
            // 2026-08-16) and its restock-notification fan-out (2026-08-18) were both
            // real and wired, but nothing in this checkout path ever actually checked the
            // flag -- a buyer could freely order an item the merchant had explicitly
            // marked unavailable. Checked here, not folded into the stockQuantity check
            // below: soldOut is an independent manual toggle, not derived from inventory
            // count (a product can be soldOut with stockQuantity > 0, e.g. an ingredient
            // shortage, or have no stockQuantity tracking at all).
            if (product.soldOut) {
                throw ProductSoldOutException("${product.name} is temporarily sold out")
            }
            // Real 마감할인 (closing/surplus discount) expiry enforcement -- see
            // MerchantProduct.isSurplusDeal/surplusExpiresAt's own doc comment and
            // MerchantProductRepository.findSurplusDeals' browse query, which already
            // correctly hides an expired closing deal from the surplus-deals rail. That
            // browse-time filter is a read-path check only, though -- nothing in this
            // checkout path, the one place that actually moves money, ever re-checked
            // `surplusExpiresAt` before now. A buyer with a cached product page, a deep
            // link opened before closing time, or a direct API call could keep buying an
            // expired closing-time sale at its discounted price indefinitely, exactly the
            // same "real flag that displays correctly but isn't enforced where it counts"
            // gap `soldOut` had until this same session's own earlier fix. Same
            // "distinct real exception, not a misleading 404" discipline as that fix: the
            // product still exists and is still shown, it's just past its own declared
            // closing time.
            if (product.isSurplusDeal && product.surplusExpiresAt?.isAfter(now) == false) {
                throw SurplusDealExpiredException("${product.name}'s closing deal has expired")
            }
            // Real Coupang 타임특가 (Time Deal, item 226) -- see TimeDeal.kt's own doc
            // comment. A real active deal with enough remaining quantity for this whole
            // line wins over the normal price-tier resolution; anything else (no deal,
            // expired, sold out, or not enough left for the full requested quantity)
            // falls through to the exact same effectiveUnitPrice this checkout already
            // used before this feature existed -- zero regression to that already-tested
            // path. Deliberately all-or-nothing per line (no partial-deal-plus-normal-
            // price split), the same "keep it simple, not a fabricated split-pricing UX"
            // discipline this session applies elsewhere.
            val activeDeal = timeDealRepository.findActiveDealForProduct(product.id, now)
            val unitPrice = if (activeDeal != null && activeDeal.remainingQuantity >= req.quantity) {
                activeDeal.remainingQuantity -= req.quantity
                timeDealRepository.save(activeDeal)
                activeDeal.dealPrice
            } else {
                effectiveUnitPrice(product.price, req.quantity, tiersByProduct[product.id].orEmpty())
            }
            Resolved(product, product.id, product.name, unitPrice, req.quantity)
        }
        // A null stockQuantity means the merchant deliberately sells an unlimited
        // service/digital item. Finite inventory is decremented in this same database
        // transaction as the payment and order; MerchantProduct's optimistic version
        // prevents two concurrent checkouts from silently overselling the final unit.
        resolved.groupBy { it.product.id }.forEach { (_, lines) ->
            val product = lines.first().product
            val requested = lines.sumOf { it.quantity }
            product.stockQuantity?.let { available ->
                if (available < requested) throw InsufficientProductStockException("${product.name} has only $available item(s) left")
                product.stockQuantity = available - requested
            }
        }
        val decrementedProducts = resolved.map { it.product }.distinctBy { it.id }.filter { it.stockQuantity != null }
        if (decrementedProducts.isNotEmpty()) merchantProductRepository.saveAll(decrementedProducts)
        val totalAmount = resolved.fold(BigDecimal.ZERO) { acc, r -> acc + r.unitPrice.multiply(BigDecimal(r.quantity)) }

        // Real 가게별 최소주문금액 (per-merchant minimum order amount) enforcement --
        // `Merchant.minOrderAmount`/`setMinOrderAmount` were already real and
        // merchant-settable, shown to buyers in `ShoppingController`'s own catalog
        // response, but never actually checked at order time anywhere in this backend
        // -- the same real gap found and fixed the same day in
        // `EatsOrderService.placeOrder`, by re-reading this already-shipped field's own
        // callers before building a new feature.
        val minOrderAmount = merchant.minOrderAmount
        if (minOrderAmount != null && totalAmount < minOrderAmount) {
            throw MinOrderAmountNotMetException("This store requires a minimum order of $minOrderAmount RWF")
        }

        val fee = totalAmount.multiply(feeRate).setScale(2, RoundingMode.HALF_UP)
        val netToMerchant = totalAmount.subtract(fee)

        buyerAccount = autoTopUpService.ensureSufficientPayBalance(buyerId, buyerAccount, totalAmount)

        val result = ledgerService.postLedgerTransaction(
            buyerAccount.currency,
            listOf(
                LedgerLeg(buyerAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, totalAmount, "Order - ${merchant.businessName}"),
                LedgerLeg(merchantAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, netToMerchant, "Order collection - ${merchant.businessName}"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, fee, "Order fee - ${merchant.businessName}"),
            ),
        )

        val transaction = Transaction(
            id = result.transactionId,
            referenceNumber = "ORDER${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
            senderId = buyerId,
            recipientId = merchant.ownerUserId,
            fromAccountId = buyerAccount.id,
            toAccountId = merchantAccount.id,
            amount = totalAmount,
            fee = fee,
            currency = buyerAccount.currency,
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
        //
        // Real push wired in (2026-07-28), same real "fulfillment can't start until the
        // merchant notices" urgency as MerchantService.collect's own payment-received
        // push -- a merchant relying on the in-app poll alone could leave a real order
        // unfulfilled for hours.
        try {
            val title = "New order received"
            val body = "A new order for ${orderItems.sumOf { it.quantity }} item(s) just came in -- $totalAmount RWF"
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = merchant.ownerUserId, type = "NEW_COMMERCE_ORDER",
                    title = title, body = body,
                    isRead = false, createdAt = Instant.now(), dataJson = "{\"orderId\":\"${order.id}\"}",
                ),
            )
            sendNewOrderPushAfterCommit(merchant.ownerUserId, title, body, order.id)
        } catch (e: Exception) {
            // Non-critical -- the real order already completed and succeeded.
        }

        // Real 쿠팡파트너스 (Coupang Partners)-style affiliate commission (item 229) --
        // see AffiliateService's own doc comment. Best-effort, same "auxiliary side-
        // effect can't block the real operation" discipline as the new-order push above:
        // an unknown/self-referral code, or any other issue here, must never fail an
        // already-completed real order.
        try {
            affiliateService.payCommissionIfReferred(referralCode, order.id, buyerId, totalAmount)
        } catch (e: Exception) {
            logger.warn("Could not pay affiliate commission for order {}", order.id, e)
        }

        return OrderDetail(order, orderItems)
    }

    /** A merchant must not be asked to fulfil an order whose payment transaction rolled back. */
    private fun sendNewOrderPushAfterCommit(ownerUserId: String, title: String, body: String, orderId: String) {
        val send = {
            try {
                pushNotificationService.sendToUser(ownerUserId, title, body, mapOf("orderId" to orderId))
            } catch (e: Exception) {
                logger.warn("Could not send new-order push for commerce order {}", orderId, e)
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
     * a seller has marked an order PACKED, cancelling needs a real return/dispute flow
     * instead (goods may already be in motion) -- see `OrderReturnService`, a genuinely
     * different feature covering DELIVERED orders specifically (correction: an earlier
     * version of this comment called that feature "not attempted here"; it was built
     * the same day as this method, just as its own dedicated service rather than a
     * method on this class -- this comment simply never got updated to say so). Either
     * the real buyer or the real seller can cancel from PLACED (a buyer changing their
     * mind, or a seller who can't fulfil it -- e.g. out of stock -- both real, common
     * reasons at this stage).
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

        // A PLACED order has not begun fulfillment, so a completed cancellation puts
        // its finite catalog units back into saleable inventory. This belongs in the
        // same transaction as the reversal and status change: a refund without a
        // restock (or the reverse) would leave the merchant's live availability wrong.
        val itemsByProduct = orderItemRepository.findByOrderId(order.id).groupBy { it.productId }
        val restockedProducts = itemsByProduct.mapNotNull { (productId, items) ->
            val product = merchantProductRepository.findById(productId).orElse(null) ?: return@mapNotNull null
            product.stockQuantity?.let { available ->
                product.stockQuantity = Math.addExact(available, items.sumOf { it.quantity })
                product
            }
        }
        if (restockedProducts.isNotEmpty()) merchantProductRepository.saveAll(restockedProducts)

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
        // Real push (item 124) -- the real "your order was packed/shipped/delivered"
        // moment every real Coupang/Toss Shopping/Naver Shopping-style app pushes
        // instantly, not just on the next in-app poll. This row's own NEW_COMMERCE_ORDER
        // (seller side) already pushes; this closes the matching buyer-side gap.
        pushNotificationService.sendToUser(order.buyerId, title, body, mapOf("orderId" to order.id))
    }
}
