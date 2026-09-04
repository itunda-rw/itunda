package rw.itunda.eats

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.EatsFulfillmentType
import rw.itunda.core.domain.EatsOrder
import rw.itunda.core.domain.EatsOrderItem
import rw.itunda.core.domain.EatsOrderStatus
import rw.itunda.core.domain.GroupEatsOrder
import rw.itunda.core.domain.GroupEatsOrderItem
import rw.itunda.core.domain.GroupEatsOrderParticipant
import rw.itunda.core.domain.GroupEatsOrderStatus
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.repository.GroupEatsOrderItemRepository
import rw.itunda.core.repository.GroupEatsOrderParticipantRepository
import rw.itunda.core.repository.GroupEatsOrderRepository
import rw.itunda.core.repository.MenuOptionChoiceRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.splitbill.SplitBillService
import rw.itunda.splitbill.SplitBillWithParticipants
import java.math.BigDecimal
import java.util.Optional

/**
 * First test coverage for GroupEatsOrderService -- and a real, previously-live bug
 * fix, not just a missing test. `finalizeOrder` is one @Transactional method that
 * places ONE real food order (the host is the buyer of record), then loops over every
 * OTHER participant requesting their own subtotal back via
 * SplitBillService.createDirectSplitBill -> createSplitBill, which itself calls
 * `rateLimiter.checkLimit("splitbill:create:$organizerId", limit = 20, window = 1h)`
 * on EVERY call. A real group order with 21+ paying non-host participants (or a host
 * who already created other split bills that hour) hits that limit partway through
 * THIS SAME loop -- and since nothing caught it, the resulting
 * RateLimitExceededException rolled back the whole transaction, including the real
 * food order already placed above it. Fixed alongside this test (same commit) with a
 * per-participant try/catch around the split-bill request only.
 */
class GroupEatsOrderServiceTest : BehaviorSpec({

    fun merchant() = Merchant(id = "restaurant_1", ownerUserId = "owner_1", accountId = "account_merchant", businessName = "Kigali Kitchen", status = MerchantStatus.ACTIVE)

    fun groupOrder() = GroupEatsOrder(
        id = "grouporder_1", hostUserId = "host_1", restaurantId = "restaurant_1",
        joinCode = "ABC123", deliveryAddress = "123 Main St", status = GroupEatsOrderStatus.OPEN,
    )

    fun participant(userId: String) = GroupEatsOrderParticipant(id = "gop_$userId", groupOrderId = "grouporder_1", userId = userId)

    fun item(userId: String, id: String) = GroupEatsOrderItem(
        id = id, groupOrderId = "grouporder_1", userId = userId, productId = "product_1",
        quantity = 1, unitPriceSnapshot = BigDecimal("5000"),
    )

    fun placedOrder() = EatsOrder(
        id = "eats_order_1", buyerId = "host_1", restaurantId = "restaurant_1",
        deliveryAddress = "123 Main St", itemsSubtotal = BigDecimal("15000"), deliveryFee = BigDecimal("1500"),
        platformFee = BigDecimal("90"), totalAmount = BigDecimal("16590"), transactionId = "ledgertxn_1",
        status = EatsOrderStatus.PLACED,
    )

    fun buildService(
        groupEatsOrderRepository: GroupEatsOrderRepository = mockk(),
        groupEatsOrderParticipantRepository: GroupEatsOrderParticipantRepository = mockk(),
        groupEatsOrderItemRepository: GroupEatsOrderItemRepository = mockk(),
        merchantRepository: MerchantRepository = mockk(),
        merchantProductRepository: MerchantProductRepository = mockk(),
        menuOptionChoiceRepository: MenuOptionChoiceRepository = mockk(relaxed = true),
        eatsOrderService: EatsOrderService = mockk(),
        splitBillService: SplitBillService = mockk(),
        rateLimiter: RateLimiter = mockk(relaxed = true),
    ) = GroupEatsOrderService(
        groupEatsOrderRepository, groupEatsOrderParticipantRepository, groupEatsOrderItemRepository,
        merchantRepository, merchantProductRepository, menuOptionChoiceRepository,
        eatsOrderService, splitBillService, rateLimiter,
    )

    Given("an open group order with 1 host and 3 paying participants, where the middle participant's split-bill request fails") {
        val groupEatsOrderRepository = mockk<GroupEatsOrderRepository>()
        val groupEatsOrderParticipantRepository = mockk<GroupEatsOrderParticipantRepository>()
        val groupEatsOrderItemRepository = mockk<GroupEatsOrderItemRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val eatsOrderService = mockk<EatsOrderService>()
        val splitBillService = mockk<SplitBillService>()
        val service = buildService(
            groupEatsOrderRepository = groupEatsOrderRepository,
            groupEatsOrderParticipantRepository = groupEatsOrderParticipantRepository,
            groupEatsOrderItemRepository = groupEatsOrderItemRepository,
            merchantRepository = merchantRepository,
            merchantProductRepository = merchantProductRepository,
            eatsOrderService = eatsOrderService,
            splitBillService = splitBillService,
        )

        val order = groupOrder()
        val participants = listOf(participant("host_1"), participant("p2"), participant("p3"), participant("p4"))
        val items = listOf(item("host_1", "i1"), item("p2", "i2"), item("p3", "i3"), item("p4", "i4"))

        every { groupEatsOrderRepository.findById("grouporder_1") } returns Optional.of(order)
        every { groupEatsOrderParticipantRepository.findByGroupOrderIdAndUserId("grouporder_1", "host_1") } returns participant("host_1")
        every { groupEatsOrderRepository.findByIdForUpdate("grouporder_1") } returns Optional.of(order)
        every { groupEatsOrderParticipantRepository.findByGroupOrderId("grouporder_1") } returns participants
        every { groupEatsOrderItemRepository.findByGroupOrderId("grouporder_1") } returns items
        every { merchantProductRepository.findAllById(any()) } returns emptyList()
        every { merchantRepository.findById("restaurant_1") } returns Optional.of(merchant())
        every { eatsOrderService.placeOrder(any(), any(), any(), any(), any(), any(), any()) } returns EatsOrderDetail(
            placedOrder(), listOf(EatsOrderItem(id = "eoi_1", orderId = "eats_order_1", productId = "product_1", productName = "Item", unitPrice = BigDecimal("5000"), quantity = 3)),
        )
        every { groupEatsOrderRepository.save(any()) } answers { firstArg() }
        every { splitBillService.createDirectSplitBill(organizerId = "host_1", otherUserId = "p2", totalAmount = any(), description = any()) } returns mockk<SplitBillWithParticipants>(relaxed = true)
        every { splitBillService.createDirectSplitBill(organizerId = "host_1", otherUserId = "p3", totalAmount = any(), description = any()) } throws RuntimeException("rate limit exceeded")
        every { splitBillService.createDirectSplitBill(organizerId = "host_1", otherUserId = "p4", totalAmount = any(), description = any()) } returns mockk<SplitBillWithParticipants>(relaxed = true)

        When("the host finalizes the group order") {
            val result = service.finalizeOrder("host_1", "grouporder_1")

            Then("the real food order is placed and returned -- it's unaffected by a later split-bill failure") {
                result.order.id shouldBe "eats_order_1"
            }
            Then("all 3 non-host participants are still attempted -- the middle failure doesn't stop the rest") {
                verify(exactly = 1) { splitBillService.createDirectSplitBill(organizerId = "host_1", otherUserId = "p2", totalAmount = any(), description = any()) }
                verify(exactly = 1) { splitBillService.createDirectSplitBill(organizerId = "host_1", otherUserId = "p3", totalAmount = any(), description = any()) }
                verify(exactly = 1) { splitBillService.createDirectSplitBill(organizerId = "host_1", otherUserId = "p4", totalAmount = any(), description = any()) }
            }
            Then("the group order is still marked FINALIZED despite the one split-bill failure") {
                order.status shouldBe GroupEatsOrderStatus.FINALIZED
            }
        }
    }
})
