package rw.itunda.eats

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.EatsOrder
import rw.itunda.core.domain.EatsOrderItem
import rw.itunda.core.domain.EatsOrderStatus
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.WalletType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.EatsOrderItemRepository
import rw.itunda.core.repository.EatsOrderRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.RiderRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

class RestaurantNotFoundException(message: String) : RuntimeException(message)
class RestaurantNoWalletException(message: String) : RuntimeException(message)
class EatsBuyerNoWalletException(message: String) : RuntimeException(message)
class EmptyEatsOrderException(message: String) : RuntimeException(message)
class InvalidEatsDeliveryAddressException(message: String) : RuntimeException(message)
class InvalidEatsQuantityException(message: String) : RuntimeException(message)
class MenuItemNotFoundException(message: String) : RuntimeException(message)
class SelfEatsOrderException(message: String) : RuntimeException(message)
class EatsOrderNotFoundException(message: String) : RuntimeException(message)
class InvalidEatsOrderStatusTransitionException(message: String) : RuntimeException(message)
class RiderNotAvailableException(message: String) : RuntimeException(message)
class DeliveryAlreadyClaimedException(message: String) : RuntimeException(message)
class NotAssignedRiderException(message: String) : RuntimeException(message)

data class EatsOrderItemRequest(val menuItemId: String, val quantity: Int)
data class EatsOrderDetail(val order: EatsOrder, val items: List<EatsOrderItem>)

/**
 * Real Coupang Eats-style food ordering + delivery, the direct sibling of
 * `rw.itunda.commerce.OrderService` -- reuses the exact same real `Merchant`/
 * `MerchantProduct` catalog as restaurants/menu items (no second catalog system
 * invented) and the exact same real wallet-to-wallet ledger movement pattern
 * `MerchantService.collect()` established, just with a real flat delivery fee on top
 * that's held in `eats_delivery_holding` until a real rider completes the delivery, then
 * paid straight into that rider's own itunda wallet -- real money to a real person, the
 * same disbursement shape `PayrollService` already proved out, not a simulation.
 *
 * Honestly scoped like every other new module this session: `deliveryFee` is a real flat
 * amount (no real distance/geo data exists anywhere in this backend -- the same "no real
 * location data" limitation already named for the neighborhood marketplace), and order
 * cancellation/refunds are deliberately not built here either, matching commerce's own
 * scoping. See `EatsOrder.kt`'s own doc comment for the full account.
 */
@Service
class EatsOrderService(
    private val merchantRepository: MerchantRepository,
    private val merchantProductRepository: MerchantProductRepository,
    private val riderRepository: RiderRepository,
    private val eatsOrderRepository: EatsOrderRepository,
    private val eatsOrderItemRepository: EatsOrderItemRepository,
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val transactionRepository: TransactionRepository,
    private val fraudRuleEngine: FraudRuleEngine,
) {
    // Same 1.5% Toss Payments fee-schedule reasoning OrderService.feeRate/
    // MerchantService.feeRate already give -- reused rather than inventing a third number
    // for what is, underneath, the same kind of wallet-to-wallet merchant collection.
    private val platformFeeRate = BigDecimal("0.015")

    // A real flat delivery fee -- see this class's own doc comment on why flat, not
    // distance-based.
    private val deliveryFee = BigDecimal("1500")

    private val restaurantStatusOrder = listOf(
        EatsOrderStatus.PLACED, EatsOrderStatus.ACCEPTED, EatsOrderStatus.PREPARING, EatsOrderStatus.READY_FOR_PICKUP,
    )
    private val riderStatusOrder = listOf(EatsOrderStatus.RIDER_ASSIGNED, EatsOrderStatus.PICKED_UP, EatsOrderStatus.DELIVERED)

    @Transactional
    fun placeOrder(buyerId: String, restaurantId: String, items: List<EatsOrderItemRequest>, deliveryAddress: String): EatsOrderDetail {
        if (items.isEmpty()) {
            throw EmptyEatsOrderException("An order needs at least one item")
        }
        val trimmedAddress = deliveryAddress.trim()
        if (trimmedAddress.isEmpty()) {
            throw InvalidEatsDeliveryAddressException("A delivery address is required")
        }
        val restaurant = merchantRepository.findById(restaurantId)
            .orElseThrow { RestaurantNotFoundException("Restaurant not found") }
        if (restaurant.ownerUserId == buyerId) {
            throw SelfEatsOrderException("Cannot order from your own restaurant")
        }

        val restaurantWallet = walletRepository.findById(restaurant.walletId)
            .orElseThrow { RestaurantNoWalletException("Restaurant settlement wallet not found") }
        val buyerWallet = walletRepository.findByUserIdAndType(buyerId, WalletType.MAIN)
            ?: throw EatsBuyerNoWalletException("No wallet found for this account")

        // Real prices read from the live menu row at checkout time -- never trusted from
        // the client, same price-tampering prevention as commerce's OrderService.
        data class Resolved(val productId: String, val name: String, val unitPrice: BigDecimal, val quantity: Int)
        val resolved = items.map { req ->
            if (req.quantity <= 0) {
                throw InvalidEatsQuantityException("Quantity must be at least 1")
            }
            val menuItem = merchantProductRepository.findById(req.menuItemId)
                .orElseThrow { MenuItemNotFoundException("Menu item not found") }
            if (menuItem.merchantId != restaurantId || !menuItem.active) {
                throw MenuItemNotFoundException("Menu item not found")
            }
            Resolved(menuItem.id, menuItem.name, menuItem.price, req.quantity)
        }
        val itemsSubtotal = resolved.fold(BigDecimal.ZERO) { acc, r -> acc + r.unitPrice.multiply(BigDecimal(r.quantity)) }
        val platformFee = itemsSubtotal.multiply(platformFeeRate).setScale(2, RoundingMode.HALF_UP)
        val netToRestaurant = itemsSubtotal.subtract(platformFee)
        val totalAmount = itemsSubtotal.add(deliveryFee)

        val result = ledgerService.postLedgerTransaction(
            buyerWallet.currency,
            listOf(
                LedgerLeg(buyerWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, totalAmount, "Eats order - ${restaurant.businessName}"),
                LedgerLeg(restaurantWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, netToRestaurant, "Eats order collection - ${restaurant.businessName}"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, platformFee, "Eats platform fee - ${restaurant.businessName}"),
                LedgerLeg("eats_delivery_holding", LedgerAccountType.EATS_DELIVERY_HOLDING, LedgerDirection.CREDIT, deliveryFee, "Eats delivery fee held - ${restaurant.businessName}"),
            ),
        )

        val transaction = Transaction(
            id = result.transactionId,
            referenceNumber = "EATS${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
            senderId = buyerId,
            recipientId = restaurant.ownerUserId,
            fromWalletId = buyerWallet.id,
            toWalletId = restaurantWallet.id,
            amount = totalAmount,
            fee = platformFee.add(deliveryFee),
            currency = buyerWallet.currency,
            type = TransactionType.PAYMENT,
            status = TransactionStatus.COMPLETED,
            description = "Eats order - ${restaurant.businessName}",
            channel = "EATS_ORDER",
            completedAt = Instant.now(),
        )
        fraudRuleEngine.evaluate(buyerId, restaurant.ownerUserId, totalAmount, transaction.id)
        transactionRepository.save(transaction)

        val order = eatsOrderRepository.save(
            EatsOrder(
                id = "eats_order_${UUID.randomUUID()}", buyerId = buyerId, restaurantId = restaurantId,
                deliveryAddress = trimmedAddress, itemsSubtotal = itemsSubtotal, deliveryFee = deliveryFee,
                platformFee = platformFee, totalAmount = totalAmount, transactionId = result.transactionId,
            ),
        )
        val orderItems = resolved.map {
            EatsOrderItem(
                id = "eats_order_item_${UUID.randomUUID()}", orderId = order.id, productId = it.productId,
                productName = it.name, unitPrice = it.unitPrice, quantity = it.quantity,
            )
        }
        eatsOrderItemRepository.saveAll(orderItems)

        return EatsOrderDetail(order, orderItems)
    }

    fun getMyOrders(buyerId: String, pageable: Pageable): Page<EatsOrder> =
        eatsOrderRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId, pageable)

    fun getRestaurantOrders(ownerUserId: String, pageable: Pageable): Page<EatsOrder> {
        val restaurant = merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw RestaurantNotFoundException("This account is not registered as a merchant")
        return eatsOrderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurant.id, pageable)
    }

    fun getRiderDeliveries(riderUserId: String, pageable: Pageable): Page<EatsOrder> {
        val rider = riderRepository.findByUserId(riderUserId)
            ?: throw RiderNotRegisteredException("This account is not registered as a rider")
        return eatsOrderRepository.findByRiderIdOrderByCreatedAtDesc(rider.id, pageable)
    }

    /** Real query backing a rider's "available deliveries" list -- READY_FOR_PICKUP
     * orders with no rider claimed yet. Visible to any registered rider, not just
     * available ones (a rider deciding whether to go online can see the real demand). */
    fun getAvailableDeliveries(pageable: Pageable): Page<EatsOrder> =
        eatsOrderRepository.findByStatusAndRiderIdIsNullOrderByCreatedAtAsc(EatsOrderStatus.READY_FOR_PICKUP, pageable)

    /** Buyer, restaurant owner, or the assigned rider can view an order's items --
     * anyone else gets a real 404, not a 403 that would confirm the order exists. */
    fun getOrderDetail(requesterId: String, orderId: String): EatsOrderDetail {
        val order = eatsOrderRepository.findById(orderId).orElseThrow { EatsOrderNotFoundException("Order not found") }
        val restaurant = merchantRepository.findById(order.restaurantId).orElse(null)
        val rider = order.riderId?.let { riderRepository.findById(it).orElse(null) }
        val isBuyer = order.buyerId == requesterId
        val isRestaurant = restaurant?.ownerUserId == requesterId
        val isRider = rider?.userId == requesterId
        if (!isBuyer && !isRestaurant && !isRider) {
            throw EatsOrderNotFoundException("Order not found")
        }
        return EatsOrderDetail(order, eatsOrderItemRepository.findByOrderId(orderId))
    }

    /** Restaurant-only, forward-only status progression through PLACED -> ACCEPTED ->
     * PREPARING -> READY_FOR_PICKUP -- same discipline as commerce's OrderService. */
    @Transactional
    fun updateRestaurantStatus(ownerUserId: String, orderId: String, newStatus: EatsOrderStatus): EatsOrder {
        val restaurant = merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw RestaurantNotFoundException("This account is not registered as a merchant")
        val order = eatsOrderRepository.findById(orderId).orElseThrow { EatsOrderNotFoundException("Order not found") }
        if (order.restaurantId != restaurant.id) {
            throw EatsOrderNotFoundException("Order not found")
        }
        val currentIndex = restaurantStatusOrder.indexOf(order.status)
        val newIndex = restaurantStatusOrder.indexOf(newStatus)
        if (currentIndex == -1 || newIndex != currentIndex + 1) {
            throw InvalidEatsOrderStatusTransitionException(
                "Cannot move from ${order.status} to $newStatus -- status can only advance one step at a time",
            )
        }
        order.status = newStatus
        order.updatedAt = Instant.now()
        return eatsOrderRepository.save(order)
    }

    /** A real, available rider claims a READY_FOR_PICKUP order no one else has claimed
     * yet -- the actual "accept delivery" action, moving the order to RIDER_ASSIGNED. */
    @Transactional
    fun claimDelivery(riderUserId: String, orderId: String): EatsOrder {
        val rider = riderRepository.findByUserId(riderUserId)
            ?: throw RiderNotRegisteredException("This account is not registered as a rider")
        if (!rider.available) {
            throw RiderNotAvailableException("Go online before claiming a delivery")
        }
        val order = eatsOrderRepository.findById(orderId).orElseThrow { EatsOrderNotFoundException("Order not found") }
        if (order.status != EatsOrderStatus.READY_FOR_PICKUP || order.riderId != null) {
            throw DeliveryAlreadyClaimedException("This delivery is no longer available")
        }
        order.riderId = rider.id
        order.status = EatsOrderStatus.RIDER_ASSIGNED
        order.updatedAt = Instant.now()
        return eatsOrderRepository.save(order)
    }

    /** The assigned rider only, forward-only through RIDER_ASSIGNED -> PICKED_UP ->
     * DELIVERED. Reaching DELIVERED triggers the real delivery-fee payout, straight out
     * of `eats_delivery_holding` and into the rider's own wallet -- real money, paid the
     * moment the real work (the delivery) is actually done. */
    @Transactional
    fun updateRiderStatus(riderUserId: String, orderId: String, newStatus: EatsOrderStatus): EatsOrder {
        val rider = riderRepository.findByUserId(riderUserId)
            ?: throw RiderNotRegisteredException("This account is not registered as a rider")
        val order = eatsOrderRepository.findById(orderId).orElseThrow { EatsOrderNotFoundException("Order not found") }
        if (order.riderId != rider.id) {
            throw NotAssignedRiderException("You are not the rider assigned to this delivery")
        }
        val currentIndex = riderStatusOrder.indexOf(order.status)
        val newIndex = riderStatusOrder.indexOf(newStatus)
        if (currentIndex == -1 || newIndex != currentIndex + 1) {
            throw InvalidEatsOrderStatusTransitionException(
                "Cannot move from ${order.status} to $newStatus -- status can only advance one step at a time",
            )
        }
        order.status = newStatus
        order.updatedAt = Instant.now()

        if (newStatus == EatsOrderStatus.DELIVERED) {
            val riderWallet = walletRepository.findById(rider.walletId)
                .orElseThrow { RiderNoWalletException("Rider wallet not found") }
            val payout = ledgerService.postLedgerTransaction(
                riderWallet.currency,
                listOf(
                    LedgerLeg("eats_delivery_holding", LedgerAccountType.EATS_DELIVERY_HOLDING, LedgerDirection.DEBIT, order.deliveryFee, "Delivery fee payout - order ${order.id}"),
                    LedgerLeg(riderWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, order.deliveryFee, "Delivery fee payout - order ${order.id}"),
                ),
            )
            order.deliveryPayoutTransactionId = payout.transactionId
        }

        return eatsOrderRepository.save(order)
    }
}
