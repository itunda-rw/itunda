package rw.itunda.eats

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.EatsFulfillmentType
import rw.itunda.core.domain.GroupEatsOrder
import rw.itunda.core.domain.GroupEatsOrderItem
import rw.itunda.core.domain.GroupEatsOrderParticipant
import rw.itunda.core.domain.GroupEatsOrderStatus
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.repository.GroupEatsOrderItemRepository
import rw.itunda.core.repository.GroupEatsOrderParticipantRepository
import rw.itunda.core.repository.GroupEatsOrderRepository
import rw.itunda.core.repository.MenuOptionChoiceRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.splitbill.SplitBillService
import java.math.BigDecimal
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant

// A real 404, not a distinguishing 403, for both "doesn't exist" and "exists but you're
// not a participant" -- same existence-oracle discipline this session's own IDOR audit
// already applied to GroupAccount/SavingsController/CommunityController (see
// [[project_itunda_idor_audit]]): a stranger who isn't a participant should not be able
// to tell a real group order apart from a nonexistent id just from the HTTP status.
class GroupEatsOrderNotFoundException(message: String) : RuntimeException(message)
class GroupEatsOrderNotOpenException(message: String) : RuntimeException(message)
class GroupEatsOrderNotHostException(message: String) : RuntimeException(message)
class GroupEatsOrderEmptyException(message: String) : RuntimeException(message)
class GroupEatsOrderInvalidJoinCodeException(message: String) : RuntimeException(message)

data class GroupEatsOrderItemView(
    val item: GroupEatsOrderItem,
    val productName: String,
    val lineTotal: BigDecimal,
)

data class GroupEatsOrderParticipantView(
    val participant: GroupEatsOrderParticipant,
    val items: List<GroupEatsOrderItemView>,
    val subtotal: BigDecimal,
)

data class GroupEatsOrderDetail(
    val groupOrder: GroupEatsOrder,
    val participants: List<GroupEatsOrderParticipantView>,
    val grandTotal: BigDecimal,
)

/**
 * Real 배달의민족 함께주문 (Baemin "Together Order") -- see GroupEatsOrder.kt's own doc
 * comment for the full account of why this is a separate pre-checkout staging entity
 * rather than a change to [EatsOrderService]. Every real money movement still flows
 * through the exact same already-tested [EatsOrderService.placeOrder] (for the one real
 * combined order) and [SplitBillService.createDirectSplitBill] (for requesting each
 * other participant's own share back from them afterward, Baemin's own real "host pays
 * first, Dutch pay after" mechanism, confirmed via fresh 2026-06 sourced research) --
 * this class only ever manages the pre-checkout shared cart itself.
 */
@Service
class GroupEatsOrderService(
    private val groupEatsOrderRepository: GroupEatsOrderRepository,
    private val groupEatsOrderParticipantRepository: GroupEatsOrderParticipantRepository,
    private val groupEatsOrderItemRepository: GroupEatsOrderItemRepository,
    private val merchantRepository: MerchantRepository,
    private val merchantProductRepository: MerchantProductRepository,
    private val menuOptionChoiceRepository: MenuOptionChoiceRepository,
    private val eatsOrderService: EatsOrderService,
    private val splitBillService: SplitBillService,
    private val rateLimiter: RateLimiter,
) {
    private val log = LoggerFactory.getLogger(GroupEatsOrderService::class.java)
    private val secureRandom = SecureRandom()
    // Excludes visually ambiguous characters (0/O, 1/I) -- a real, spoken/typed-aloud
    // share code, unlike CertificateService's own hex serial number which is never
    // meant to be read out loud.
    private val joinCodeAlphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    @Transactional
    fun create(
        hostId: String,
        restaurantId: String,
        deliveryAddress: String,
        deliveryLatitude: Double? = null,
        deliveryLongitude: Double? = null,
        fulfillmentType: EatsFulfillmentType = EatsFulfillmentType.DELIVERY,
    ): GroupEatsOrder {
        rateLimiter.checkLimit("group-eats-order:create:$hostId", limit = 10, window = Duration.ofHours(1))
        val restaurant = merchantRepository.findById(restaurantId)
            .orElseThrow { RestaurantNotFoundException("Restaurant not found") }
        val trimmedAddress = deliveryAddress.trim()
        if (fulfillmentType == EatsFulfillmentType.DELIVERY && trimmedAddress.isEmpty()) {
            throw InvalidEatsDeliveryAddressException("A delivery address is required")
        }
        if ((deliveryLatitude == null) != (deliveryLongitude == null)) {
            throw InvalidEatsCoordinatesException("Both deliveryLatitude and deliveryLongitude are required together")
        }
        if (deliveryLatitude != null && deliveryLongitude != null && !GeoUtils.isValidCoordinate(deliveryLatitude, deliveryLongitude)) {
            throw InvalidEatsCoordinatesException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        val resolvedAddress = if (fulfillmentType == EatsFulfillmentType.PICKUP) "Pickup at ${restaurant.businessName}" else trimmedAddress
        val groupOrder = groupEatsOrderRepository.save(
            GroupEatsOrder(
                id = "group_eats_order_${java.util.UUID.randomUUID()}",
                hostUserId = hostId,
                restaurantId = restaurantId,
                joinCode = generateUniqueJoinCode(),
                deliveryAddress = resolvedAddress,
                deliveryLatitude = deliveryLatitude,
                deliveryLongitude = deliveryLongitude,
                fulfillmentType = fulfillmentType,
            ),
        )
        groupEatsOrderParticipantRepository.save(
            GroupEatsOrderParticipant(id = "group_eats_participant_${java.util.UUID.randomUUID()}", groupOrderId = groupOrder.id, userId = hostId),
        )
        return groupOrder
    }

    // A real, unpredictable, human-shareable 6-character code -- retried on the rare
    // collision rather than trusting randomness alone, same defensive-uniqueness
    // discipline real invite-code systems use.
    private fun generateUniqueJoinCode(): String {
        repeat(20) {
            val code = (1..6).map { joinCodeAlphabet[secureRandom.nextInt(joinCodeAlphabet.length)] }.joinToString("")
            if (groupEatsOrderRepository.findByJoinCode(code) == null) return code
        }
        throw IllegalStateException("Could not generate a unique join code")
    }

    @Transactional
    fun join(userId: String, joinCode: String): GroupEatsOrder {
        val normalized = joinCode.trim().uppercase()
        if (normalized.isEmpty()) throw GroupEatsOrderInvalidJoinCodeException("A join code is required")
        val groupOrder = groupEatsOrderRepository.findByJoinCode(normalized)
            ?: throw GroupEatsOrderInvalidJoinCodeException("No open group order found for this code")
        if (groupOrder.status != GroupEatsOrderStatus.OPEN) {
            throw GroupEatsOrderNotOpenException("This group order is no longer open")
        }
        // Idempotent re-join, same "already a member" quiet-success convention as
        // GroupMessagingService's own join handling -- not an error for a participant
        // who taps the link twice.
        if (groupEatsOrderParticipantRepository.findByGroupOrderIdAndUserId(groupOrder.id, userId) == null) {
            groupEatsOrderParticipantRepository.save(
                GroupEatsOrderParticipant(id = "group_eats_participant_${java.util.UUID.randomUUID()}", groupOrderId = groupOrder.id, userId = userId),
            )
        }
        return groupOrder
    }

    /** Replaces the caller's own items entirely -- simpler and safer than incremental
     * add/remove for a v1 shared cart, same "resend the full current state" convention
     * a client-side cart already keeps locally anyway. */
    @Transactional
    fun setMyItems(userId: String, groupOrderId: String, items: List<EatsOrderItemRequest>): GroupEatsOrderDetail {
        val groupOrder = getParticipantGroupOrder(userId, groupOrderId)
        if (groupOrder.status != GroupEatsOrderStatus.OPEN) {
            throw GroupEatsOrderNotOpenException("This group order is no longer open")
        }
        for (item in items) {
            if (item.quantity <= 0) throw InvalidEatsQuantityException("Quantity must be at least 1")
        }
        groupEatsOrderItemRepository.deleteByGroupOrderIdAndUserId(groupOrder.id, userId)
        val resolved = items.map { item ->
            val product = merchantProductRepository.findById(item.menuItemId)
                .orElseThrow { MenuItemNotFoundException("Menu item not found") }
            val choiceDeltaSum = if (item.selectedChoiceIds.isEmpty()) {
                BigDecimal.ZERO
            } else {
                menuOptionChoiceRepository.findAllById(item.selectedChoiceIds).sumOf { it.priceDelta }
            }
            GroupEatsOrderItem(
                id = "group_eats_order_item_${java.util.UUID.randomUUID()}",
                groupOrderId = groupOrder.id,
                userId = userId,
                productId = item.menuItemId,
                quantity = item.quantity,
                selectedChoiceIds = item.selectedChoiceIds.takeIf { it.isNotEmpty() }?.joinToString(","),
                unitPriceSnapshot = product.price + choiceDeltaSum,
            )
        }
        groupEatsOrderItemRepository.saveAll(resolved)
        return getDetail(userId, groupOrder.id)
    }

    fun getDetail(userId: String, groupOrderId: String): GroupEatsOrderDetail {
        val groupOrder = getParticipantGroupOrder(userId, groupOrderId)
        val participants = groupEatsOrderParticipantRepository.findByGroupOrderId(groupOrder.id)
        val allItems = groupEatsOrderItemRepository.findByGroupOrderId(groupOrder.id).groupBy { it.userId }
        val productNames = merchantProductRepository.findAllById(allItems.values.flatten().map { it.productId }.distinct())
            .associate { it.id to it.name }
        val views = participants.map { participant ->
            val items = allItems[participant.userId].orEmpty().map { item ->
                GroupEatsOrderItemView(
                    item = item,
                    productName = productNames[item.productId] ?: "Item",
                    lineTotal = item.unitPriceSnapshot * item.quantity.toBigDecimal(),
                )
            }
            GroupEatsOrderParticipantView(participant, items, items.sumOf { it.lineTotal })
        }
        return GroupEatsOrderDetail(groupOrder, views, views.sumOf { it.subtotal })
    }

    /**
     * Host-only. Combines every participant's own items into one real [EatsOrder] via
     * the unchanged [EatsOrderService.placeOrder] (host is the buyer/payer of record),
     * then requests each OTHER participant's own real subtotal back from them via
     * [SplitBillService.createDirectSplitBill] -- real Baemin's own confirmed "host pays
     * first, Dutch pay requested after" mechanism, not a fabricated new settlement model.
     * A participant with a zero subtotal (joined but never added anything) gets no split
     * request at all, same "nothing to collect" reasoning SplitBillService's own ladder
     * zero-share handling already established.
     *
     * Real fix (found via project_itunda_zero_test_coverage_sweep's broadened
     * transaction-poisoning check, 2026-09-05): the per-participant split-bill loop
     * below had no try/catch, and this whole method is one @Transactional block --
     * `SplitBillService.createSplitBill`'s own real `rateLimiter.checkLimit
     * ("splitbill:create:$organizerId", limit = 20, window = 1 hour)` fires ONCE PER
     * PARTICIPANT here, so a real group order with 21+ paying non-host participants
     * (or a host who already created other split bills that hour) hits it partway
     * through this very loop -- and since nothing catches it, that
     * RateLimitExceededException rolls back the WHOLE transaction, including the real
     * food order already placed via `eatsOrderService.placeOrder` above. A perfectly
     * valid, already-charged order would silently vanish because of an unrelated
     * per-organizer request-count limit, not anything wrong with the order itself.
     * Fixed with a per-participant try/catch: the real order (the thing that actually
     * matters -- everyone gets fed) is preserved regardless of how many Dutch-pay
     * requests fail to go out; a failed request is logged, not silently dropped
     * without a trace, and can be requested again manually via the group order's own
     * split-bill history.
     */
    @Transactional
    fun finalizeOrder(hostId: String, groupOrderId: String): EatsOrderDetail {
        val participantCheck = getParticipantGroupOrder(hostId, groupOrderId)
        if (participantCheck.hostUserId != hostId) {
            throw GroupEatsOrderNotHostException("Only the host can finalize this group order")
        }
        // Real fix for a genuine double-finalize race (found via a fresh concurrency
        // audit, 2026-08-16) -- see GroupEatsOrderRepository.findByIdForUpdate's own doc
        // comment. Locked separately from the participant/host check above (which never
        // changes concurrently and doesn't need to hold a row lock) so only the actual
        // status check-then-write is serialized.
        val groupOrder = groupEatsOrderRepository.findByIdForUpdate(groupOrderId)
            .orElseThrow { GroupEatsOrderNotFoundException("Group order not found") }
        if (groupOrder.status != GroupEatsOrderStatus.OPEN) {
            throw GroupEatsOrderNotOpenException("This group order is no longer open")
        }
        val detail = getDetail(hostId, groupOrder.id)
        val allItems = detail.participants.flatMap { it.items }
        if (allItems.isEmpty()) {
            throw GroupEatsOrderEmptyException("No one has added any items yet")
        }
        val mergedRequest = allItems.map { view ->
            EatsOrderItemRequest(
                menuItemId = view.item.productId,
                quantity = view.item.quantity,
                selectedChoiceIds = view.item.selectedChoiceIds?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            )
        }
        val orderDetail = eatsOrderService.placeOrder(
            buyerId = hostId,
            restaurantId = groupOrder.restaurantId,
            items = mergedRequest,
            deliveryAddress = groupOrder.deliveryAddress,
            deliveryLatitude = groupOrder.deliveryLatitude,
            deliveryLongitude = groupOrder.deliveryLongitude,
            fulfillmentType = groupOrder.fulfillmentType,
        )
        groupOrder.status = GroupEatsOrderStatus.FINALIZED
        groupOrder.resultingOrderId = orderDetail.order.id
        groupOrder.finalizedAt = Instant.now()
        groupEatsOrderRepository.save(groupOrder)

        val restaurantName = merchantRepository.findById(groupOrder.restaurantId).map { it.businessName }.orElse("the restaurant")
        for (participantView in detail.participants) {
            val participantId = participantView.participant.userId
            if (participantId == hostId) continue
            if (participantView.subtotal.compareTo(BigDecimal.ZERO) <= 0) continue
            try {
                splitBillService.createDirectSplitBill(
                    organizerId = hostId,
                    otherUserId = participantId,
                    totalAmount = participantView.subtotal,
                    description = "Together Order at $restaurantName",
                )
            } catch (e: Exception) {
                log.error("Split-bill request failed for participant {} on group order {} -- the real food order is unaffected", participantId, groupOrder.id, e)
            }
        }
        return orderDetail
    }

    @Transactional
    fun cancel(hostId: String, groupOrderId: String): GroupEatsOrder {
        val participantCheck = getParticipantGroupOrder(hostId, groupOrderId)
        if (participantCheck.hostUserId != hostId) {
            throw GroupEatsOrderNotHostException("Only the host can cancel this group order")
        }
        // Same real race as finalizeOrder above -- a concurrent cancel() racing a
        // finalizeOrder() (or two cancel() calls) for the same group order must not
        // both pass the OPEN check before either commits.
        val groupOrder = groupEatsOrderRepository.findByIdForUpdate(groupOrderId)
            .orElseThrow { GroupEatsOrderNotFoundException("Group order not found") }
        if (groupOrder.status != GroupEatsOrderStatus.OPEN) {
            throw GroupEatsOrderNotOpenException("This group order is no longer open")
        }
        groupOrder.status = GroupEatsOrderStatus.CANCELLED
        return groupEatsOrderRepository.save(groupOrder)
    }

    // Real 404, not 403, the moment either the group order doesn't exist OR the caller
    // isn't a participant of it -- see the top-of-file doc comment on
    // GroupEatsOrderNotFoundException for why these two cases must stay
    // indistinguishable to the caller.
    private fun getParticipantGroupOrder(userId: String, groupOrderId: String): GroupEatsOrder {
        val groupOrder = groupEatsOrderRepository.findById(groupOrderId)
            .orElseThrow { GroupEatsOrderNotFoundException("Group order not found") }
        requireParticipant(groupOrder.id, userId)
        return groupOrder
    }

    private fun requireParticipant(groupOrderId: String, userId: String) {
        groupEatsOrderParticipantRepository.findByGroupOrderIdAndUserId(groupOrderId, userId)
            ?: throw GroupEatsOrderNotFoundException("Group order not found")
    }
}
