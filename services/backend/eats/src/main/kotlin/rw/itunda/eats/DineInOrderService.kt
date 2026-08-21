package rw.itunda.eats

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.core.domain.DineInOrder
import rw.itunda.core.domain.DineInOrderItem
import rw.itunda.core.domain.DineInOrderStatus
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.AccountType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.DineInOrderItemRepository
import rw.itunda.core.repository.DineInOrderRepository
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.MenuOptionChoiceRepository
import rw.itunda.core.repository.MenuOptionGroupRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

class InvalidDineInTableException(message: String) : RuntimeException(message)
class EmptyDineInOrderException(message: String) : RuntimeException(message)
class InvalidDineInQuantityException(message: String) : RuntimeException(message)
class DineInMenuItemNotFoundException(message: String) : RuntimeException(message)
class DineInMenuItemSoldOutException(message: String) : RuntimeException(message)
class DineInMenuItemSurplusDealExpiredException(message: String) : RuntimeException(message)
class DineInRestaurantNotFoundException(message: String) : RuntimeException(message)
class DineInRestaurantNoAccountException(message: String) : RuntimeException(message)
class DineInBuyerNoAccountException(message: String) : RuntimeException(message)
class SelfDineInOrderException(message: String) : RuntimeException(message)
class DineInOrderNotFoundException(message: String) : RuntimeException(message)
class InvalidDineInStatusTransitionException(message: String) : RuntimeException(message)
class MissingRequiredDineInMenuOptionException(message: String) : RuntimeException(message)
class InvalidDineInMenuOptionSelectionException(message: String) : RuntimeException(message)
class DineInRestaurantNotAcceptingOrdersException(message: String) : RuntimeException(message)

data class DineInOrderItemRequest(val menuItemId: String, val quantity: Int, val selectedChoiceIds: List<String> = emptyList())
data class DineInOrderDetail(val order: DineInOrder, val items: List<DineInOrderItem>)

/**
 * Real 배민오더-style table/QR in-store ordering -- the direct sibling of `EatsOrderService`,
 * reusing the exact same real `Merchant`/`MerchantProduct` catalog and the exact same
 * real menu-options resolution, but with no delivery address, no rider, and no delivery
 * fee/holding leg at all: `totalAmount` == `itemsSubtotal` (platformFee comes out of the
 * restaurant's own share), settled straight into the restaurant's account in one atomic
 * ledger post at placement time, same as `MerchantService.collect()`'s own real account-to-
 * account movement. See `DineInOrder.kt`'s own doc comment for the full account of why this
 * is a separate entity rather than an extension of `EatsOrder`.
 */
@Service
class DineInOrderService(
    private val merchantRepository: MerchantRepository,
    private val merchantProductRepository: MerchantProductRepository,
    private val dineInOrderRepository: DineInOrderRepository,
    private val dineInOrderItemRepository: DineInOrderItemRepository,
    private val menuOptionGroupRepository: MenuOptionGroupRepository,
    private val menuOptionChoiceRepository: MenuOptionChoiceRepository,
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService,
    private val transactionRepository: TransactionRepository,
    private val fraudRuleEngine: FraudRuleEngine,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
    private val autoTopUpService: rw.itunda.account.AutoTopUpService,
) {
    private val logger = LoggerFactory.getLogger(DineInOrderService::class.java)

    companion object {
        // Same 1.5% Toss Payments fee-schedule reasoning OrderService.feeRate/
        // EatsOrderService.platformFeeRate already give -- reused rather than inventing a
        // fourth number.
        private val PLATFORM_FEE_RATE = BigDecimal("0.015")

        private val restaurantStatusOrder = listOf(
            DineInOrderStatus.PLACED, DineInOrderStatus.ACCEPTED, DineInOrderStatus.PREPARING, DineInOrderStatus.SERVED,
        )
    }

    @Transactional
    fun placeOrder(buyerId: String, restaurantId: String, tableNumber: String, items: List<DineInOrderItemRequest>, notes: String? = null): DineInOrderDetail {
        if (items.isEmpty()) {
            throw EmptyDineInOrderException("An order needs at least one item")
        }
        val trimmedTable = tableNumber.trim()
        if (trimmedTable.isEmpty()) {
            throw InvalidDineInTableException("A table number is required")
        }
        if (trimmedTable.length > 50) {
            throw InvalidDineInTableException("Table number must be 50 characters or fewer")
        }
        val trimmedNotes = notes?.trim()?.ifBlank { null }
        if (trimmedNotes != null && trimmedNotes.length > 500) {
            throw InvalidDineInTableException("Notes must be 500 characters or fewer")
        }
        val restaurant = merchantRepository.findById(restaurantId)
            .orElseThrow { DineInRestaurantNotFoundException("Restaurant not found") }
        if (restaurant.ownerUserId == buyerId) {
            throw SelfDineInOrderException("Cannot order from your own restaurant")
        }
        // Real Baemin CEO app 영업일시중지/휴무일 설정 enforcement -- see
        // Merchant.isAcceptingOrders/isClosedToday's own doc comments and
        // EatsOrderService.placeOrder's identical checks for the full sourcing. Both
        // flags already gate the Eats (delivery) checkout path for this exact same
        // shared `Merchant` catalog, but nothing in this dine-in (in-store table/QR)
        // checkout -- the other place that actually moves real money against the same
        // restaurant -- ever re-checked them: a restaurant that paused itself for
        // order overload or declared today a recurring closed day could still have a
        // buyer place and pay for a real dine-in order via a stale client, cached
        // table QR link, or direct API call. Same "real flag correctly enforced on one
        // write path sharing this Merchant catalog but not this one" gap as
        // MerchantProduct.soldOut/isSurplusDeal had until this session's own earlier
        // fixes, and the exact same gap just closed on commerce's own
        // OrderService.placeOrder for this same Merchant catalog.
        if (!restaurant.isAcceptingOrders) {
            throw DineInRestaurantNotAcceptingOrdersException("This restaurant isn't accepting orders right now")
        }
        if (restaurant.isClosedToday()) {
            throw DineInRestaurantNotAcceptingOrdersException("This restaurant is closed today")
        }

        val restaurantAccount = accountRepository.findById(restaurant.accountId)
            .orElseThrow { DineInRestaurantNoAccountException("Restaurant settlement account not found") }
        // Real Toss Bank/Toss Pay separation (2026-08-21) -- see MerchantService
        // .collect()'s own doc comment. A dine-in order is real merchant collection,
        // same as QR/code payment -- draws from the buyer's itunda Pay money,
        // auto-topped from Bank (then an external linked account) if short.
        var buyerAccount = accountRepository.findByUserIdAndType(buyerId, AccountType.PAY)
            ?: throw DineInBuyerNoAccountException("No itunda Pay money found for this account")

        val distinctMenuItemIds = items.map { it.menuItemId }.distinct()
        val groupsByProduct = if (distinctMenuItemIds.isNotEmpty()) {
            menuOptionGroupRepository.findByProductIdInOrderByDisplayOrderAsc(distinctMenuItemIds).groupBy { it.productId }
        } else {
            emptyMap()
        }
        val choicesByGroup = groupsByProduct.values.flatten().map { it.id }.let { groupIds ->
            if (groupIds.isEmpty()) emptyMap() else menuOptionChoiceRepository.findByGroupIdInOrderByDisplayOrderAsc(groupIds).groupBy { it.groupId }
        }

        data class Resolved(val productId: String, val name: String, val unitPrice: BigDecimal, val quantity: Int, val selectedOptionsJson: String?)
        val resolved = items.map { req ->
            if (req.quantity <= 0) {
                throw InvalidDineInQuantityException("Quantity must be at least 1")
            }
            val menuItem = merchantProductRepository.findById(req.menuItemId)
                .orElseThrow { DineInMenuItemNotFoundException("Menu item not found") }
            if (menuItem.merchantId != restaurantId || !menuItem.active) {
                throw DineInMenuItemNotFoundException("Menu item not found")
            }
            // Real Baemin CEO app/DoorDash-style "86" enforcement -- see
            // MerchantProduct.soldOut's own doc comment and EatsOrderService.placeOrder's
            // identical check for the full reasoning.
            if (menuItem.soldOut) {
                throw DineInMenuItemSoldOutException("${menuItem.name} is temporarily sold out")
            }
            // Real 마감할인 (closing/surplus discount) expiry enforcement -- see
            // MerchantProduct.isSurplusDeal/surplusExpiresAt's own doc comment and
            // commerce's OrderService.placeOrder / EatsOrderService.placeOrder's
            // identical fix/reasoning for the same shared MerchantProduct catalog.
            if (menuItem.isSurplusDeal && menuItem.surplusExpiresAt?.isAfter(Instant.now()) == false) {
                throw DineInMenuItemSurplusDealExpiredException("${menuItem.name}'s closing deal has expired")
            }

            val groups = groupsByProduct[menuItem.id] ?: emptyList()
            var optionsDelta = BigDecimal.ZERO
            var selectedOptionsJson: String? = null
            if (groups.isNotEmpty()) {
                val selectedSet = req.selectedChoiceIds.toSet()
                val allValidChoices = groups.flatMap { choicesByGroup[it.id] ?: emptyList() }
                val allValidChoiceIds = allValidChoices.map { it.id }.toSet()
                if (!allValidChoiceIds.containsAll(selectedSet)) {
                    throw InvalidDineInMenuOptionSelectionException("One or more selected options do not belong to ${menuItem.name}")
                }
                val choiceById = allValidChoices.associateBy { it.id }
                val summaries = mutableListOf<Triple<String, String, BigDecimal>>()
                for (group in groups) {
                    val groupChoiceIds = (choicesByGroup[group.id] ?: emptyList()).map { it.id }.toSet()
                    // Real multi-select optional add-ons (2026-07-26) -- see
                    // EatsOrderService's identical fix and MenuOptionGroup.kt's own doc
                    // comment for the full account.
                    val selectedInGroup = selectedSet.intersect(groupChoiceIds)
                    if (group.required && selectedInGroup.isEmpty()) {
                        throw MissingRequiredDineInMenuOptionException("Select at least one option for '${group.name}' on ${menuItem.name}")
                    }
                    if (!group.multiSelect && selectedInGroup.size > 1) {
                        throw InvalidDineInMenuOptionSelectionException("Only one option can be selected for '${group.name}' on ${menuItem.name}")
                    }
                    selectedInGroup.forEach { choiceId ->
                        val chosen = choiceById.getValue(choiceId)
                        optionsDelta = optionsDelta.add(chosen.priceDelta)
                        summaries.add(Triple(group.name, chosen.name, chosen.priceDelta))
                    }
                }
                selectedOptionsJson = buildSelectedOptionsJson(summaries)
            }
            Resolved(menuItem.id, menuItem.name, menuItem.price.add(optionsDelta), req.quantity, selectedOptionsJson)
        }
        val itemsSubtotal = resolved.fold(BigDecimal.ZERO) { acc, r -> acc + r.unitPrice.multiply(BigDecimal(r.quantity)) }
        val platformFee = itemsSubtotal.multiply(PLATFORM_FEE_RATE).setScale(2, RoundingMode.HALF_UP)
        val netToRestaurant = itemsSubtotal.subtract(platformFee)
        val totalAmount = itemsSubtotal

        buyerAccount = autoTopUpService.ensureSufficientPayBalance(buyerId, buyerAccount, totalAmount)

        val result = ledgerService.postLedgerTransaction(
            buyerAccount.currency,
            listOf(
                LedgerLeg(buyerAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, totalAmount, "Dine-in order - ${restaurant.businessName}"),
                LedgerLeg(restaurantAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, netToRestaurant, "Dine-in order collection - ${restaurant.businessName}"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, platformFee, "Dine-in platform fee - ${restaurant.businessName}"),
            ),
        )

        val transaction = Transaction(
            id = result.transactionId,
            referenceNumber = "DINEIN${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
            senderId = buyerId,
            recipientId = restaurant.ownerUserId,
            fromAccountId = buyerAccount.id,
            toAccountId = restaurantAccount.id,
            amount = totalAmount,
            fee = platformFee,
            currency = buyerAccount.currency,
            type = TransactionType.PAYMENT,
            status = TransactionStatus.COMPLETED,
            description = "Dine-in order - ${restaurant.businessName}",
            channel = "DINE_IN_ORDER",
            completedAt = Instant.now(),
        )
        fraudRuleEngine.evaluate(buyerId, restaurant.ownerUserId, totalAmount, transaction.id)
        transactionRepository.save(transaction)

        val order = dineInOrderRepository.save(
            DineInOrder(
                id = "dine_in_order_${UUID.randomUUID()}", buyerId = buyerId, restaurantId = restaurantId,
                tableNumber = trimmedTable, itemsSubtotal = itemsSubtotal, platformFee = platformFee,
                totalAmount = totalAmount, transactionId = result.transactionId, notes = trimmedNotes,
            ),
        )
        val orderItems = resolved.map {
            DineInOrderItem(
                id = "dine_in_order_item_${UUID.randomUUID()}", orderId = order.id, productId = it.productId,
                productName = it.name, unitPrice = it.unitPrice, quantity = it.quantity,
                selectedOptionsJson = it.selectedOptionsJson,
            )
        }
        dineInOrderItemRepository.saveAll(orderItems)
        notifyRestaurant(order, restaurant.ownerUserId)

        return DineInOrderDetail(order, orderItems)
    }

    fun getMyOrders(buyerId: String, pageable: Pageable): Page<DineInOrder> =
        dineInOrderRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId, pageable)

    fun getRestaurantOrders(ownerUserId: String, pageable: Pageable): Page<DineInOrder> {
        val restaurant = merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw DineInRestaurantNotFoundException("This account is not registered as a merchant")
        return dineInOrderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurant.id, pageable)
    }

    fun getOrderDetail(requesterId: String, orderId: String): DineInOrderDetail {
        val order = dineInOrderRepository.findById(orderId).orElseThrow { DineInOrderNotFoundException("Order not found") }
        val restaurant = merchantRepository.findById(order.restaurantId).orElse(null)
        val isBuyer = order.buyerId == requesterId
        val isRestaurant = restaurant?.ownerUserId == requesterId
        if (!isBuyer && !isRestaurant) {
            throw DineInOrderNotFoundException("Order not found")
        }
        return DineInOrderDetail(order, dineInOrderItemRepository.findByOrderId(orderId))
    }

    /** Restaurant-only, forward-only status progression through PLACED -> ACCEPTED ->
     * PREPARING -> SERVED -- same discipline as EatsOrderService.updateRestaurantStatus,
     * minus the rider-dispatch step this order type never has. */
    @Transactional
    fun updateStatus(ownerUserId: String, orderId: String, newStatus: DineInOrderStatus): DineInOrder {
        val restaurant = merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw DineInRestaurantNotFoundException("This account is not registered as a merchant")
        val order = dineInOrderRepository.findById(orderId).orElseThrow { DineInOrderNotFoundException("Order not found") }
        if (order.restaurantId != restaurant.id) {
            throw DineInOrderNotFoundException("Order not found")
        }
        val currentIndex = restaurantStatusOrder.indexOf(order.status)
        val newIndex = restaurantStatusOrder.indexOf(newStatus)
        if (currentIndex == -1 || newIndex != currentIndex + 1) {
            throw InvalidDineInStatusTransitionException(
                "Cannot move from ${order.status} to $newStatus -- status can only advance one step at a time",
            )
        }
        order.status = newStatus
        order.updatedAt = Instant.now()
        return dineInOrderRepository.save(order)
    }

    /** Real cancellation + refund -- same reversing-ledger-entry technique
     * EatsOrderService.cancelOrder uses, scoped to PLACED orders only, before the
     * restaurant has started real fulfillment work. */
    @Transactional
    fun cancelOrder(requesterId: String, orderId: String): DineInOrder {
        val order = dineInOrderRepository.findById(orderId).orElseThrow { DineInOrderNotFoundException("Order not found") }
        val restaurant = merchantRepository.findById(order.restaurantId).orElse(null)
        val isBuyer = order.buyerId == requesterId
        val isRestaurant = restaurant?.ownerUserId == requesterId
        if (!isBuyer && !isRestaurant) {
            throw DineInOrderNotFoundException("Order not found")
        }
        if (order.status != DineInOrderStatus.PLACED) {
            throw InvalidDineInStatusTransitionException("Only a PLACED order can be cancelled -- this order is already ${order.status}")
        }

        val originalEntries = ledgerEntryRepository.findByTransactionId(order.transactionId)
        val reversedLegs = originalEntries.map { entry ->
            val flipped = if (entry.direction == LedgerDirection.DEBIT) LedgerDirection.CREDIT else LedgerDirection.DEBIT
            LedgerLeg(entry.accountId, entry.accountType, flipped, entry.amount, "Refund for order ${order.id}")
        }
        val refund = ledgerService.postLedgerTransaction(originalEntries.first().currency, reversedLegs)

        order.status = DineInOrderStatus.CANCELLED
        order.refundTransactionId = refund.transactionId
        order.updatedAt = Instant.now()
        val saved = dineInOrderRepository.save(order)
        if (isRestaurant) {
            notifyBuyer(saved, "Order cancelled", "${restaurant?.businessName ?: "The restaurant"} cancelled your order. Your payment has been refunded.")
        }
        return saved
    }

    private fun buildSelectedOptionsJson(summaries: List<Triple<String, String, BigDecimal>>): String {
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
        val items = summaries.joinToString(",") { (groupName, choiceName, delta) ->
            "{\"groupName\":\"${esc(groupName)}\",\"choiceName\":\"${esc(choiceName)}\",\"priceDelta\":$delta}"
        }
        return "[$items]"
    }

    private fun notifyRestaurant(order: DineInOrder, restaurantOwnerUserId: String) {
        val title = "New table order"
        val body = "Table ${order.tableNumber} placed a new order."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = restaurantOwnerUserId, type = "DINE_IN_ORDER_PLACED",
                title = title, body = body, isRead = false, createdAt = Instant.now(),
                dataJson = "{\"orderId\":\"${order.id}\"}",
            ),
        )
        sendPushAfterCommit(restaurantOwnerUserId, title, body, order.id)
    }

    private fun notifyBuyer(order: DineInOrder, title: String, body: String) {
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = order.buyerId, type = "DINE_IN_ORDER_UPDATE",
                title = title, body = body, isRead = false, createdAt = Instant.now(),
                dataJson = "{\"orderId\":\"${order.id}\"}",
            ),
        )
        // Real push (item 124) -- same buyer-facing status-update urgency as
        // OrderService.notifyBuyer's own matching Commerce gap, closed the same pass.
        sendPushAfterCommit(order.buyerId, title, body, order.id)
    }

    /** Dine-in alerts must describe only a committed order or committed refund state. */
    private fun sendPushAfterCommit(userId: String, title: String, body: String, orderId: String) {
        val send = {
            try {
                pushNotificationService.sendToUser(userId, title, body, mapOf("orderId" to orderId))
            } catch (e: Exception) {
                logger.warn("Could not send dine-in order push for order {}", orderId, e)
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
