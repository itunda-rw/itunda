package rw.itunda.commerce

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.LedgerEntry
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.Order
import rw.itunda.core.domain.OrderReturnRequest
import rw.itunda.core.domain.OrderReturnStatus
import rw.itunda.core.domain.OrderReturnType
import rw.itunda.core.domain.OrderStatus
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.OrderRepository
import rw.itunda.core.repository.OrderReturnRequestRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional

/**
 * First test coverage for real Coupang-style post-delivery Return & Exchange requests
 * -- see OrderReturnService's own doc comment for the full account, including why this
 * is genuinely distinct from OrderServiceTest's own cancelOrder coverage.
 */
class OrderReturnServiceTest : BehaviorSpec({

    fun deliveredOrder(deliveredAt: Instant = Instant.now()) = Order(
        id = "order_1", buyerId = "buyer_1", merchantId = "merchant_1", deliveryAddress = "addr",
        totalAmount = BigDecimal("6000"), fee = BigDecimal("90"), transactionId = "ledgertxn_1",
        status = OrderStatus.DELIVERED, updatedAt = deliveredAt,
    )

    val merchant = Merchant(id = "merchant_1", ownerUserId = "seller_1", accountId = "account_merchant", businessName = "Kigali Store", status = MerchantStatus.ACTIVE)

    Given("a buyer requesting a return on a real, recently-delivered order") {
        val orderRepository = mockk<OrderRepository>()
        val orderReturnRequestRepository = mockk<OrderReturnRequestRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = OrderReturnService(orderRepository, orderReturnRequestRepository, merchantRepository, ledgerEntryRepository, ledgerService, notificationRepository, pushNotificationService)

        When("the order was delivered 2 real days ago, within the 7-day window") {
            every { orderRepository.findById("order_1") } returns Optional.of(deliveredOrder(Instant.now().minus(2, ChronoUnit.DAYS)))
            every { orderReturnRequestRepository.findByOrderId("order_1") } returns emptyList()
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            val savedSlot = slot<OrderReturnRequest>()
            every { orderReturnRequestRepository.save(capture(savedSlot)) } answers { firstArg() }

            val result = service.requestReturn("buyer_1", "order_1", OrderReturnType.RETURN, "defective", "arrived broken")

            Then("it creates a real REQUESTED return request with the normalized reason code") {
                result.status shouldBe OrderReturnStatus.REQUESTED
                result.reasonCode shouldBe "DEFECTIVE"
                savedSlot.captured.type shouldBe OrderReturnType.RETURN
            }
            Then("it real-notifies the merchant owner, not the buyer") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "seller_1" && it.type == "COMMERCE_RETURN_REQUESTED" }) }
            }

            Then("the merchant owner also gets a real mobile push notification, not just the in-app one") {
                verify(exactly = 1) { pushNotificationService.sendToUser("seller_1", "New return request", any(), any()) }
            }
        }

        When("the order was delivered 10 real days ago, past the real 7-day window") {
            every { orderRepository.findById("order_1") } returns Optional.of(deliveredOrder(Instant.now().minus(10, ChronoUnit.DAYS)))

            Then("it throws ReturnWindowExpiredException before ever touching the return repository") {
                try {
                    service.requestReturn("buyer_1", "order_1", OrderReturnType.RETURN, "defective", null)
                    error("expected ReturnWindowExpiredException")
                } catch (e: ReturnWindowExpiredException) {
                    verify(exactly = 0) { orderReturnRequestRepository.save(any()) }
                }
            }
        }

        When("the order isn't DELIVERED yet") {
            val placedOrder = Order(
                id = "order_2", buyerId = "buyer_1", merchantId = "merchant_1", deliveryAddress = "addr",
                totalAmount = BigDecimal("6000"), fee = BigDecimal("90"), transactionId = "ledgertxn_2", status = OrderStatus.PACKED,
            )
            every { orderRepository.findById("order_2") } returns Optional.of(placedOrder)

            Then("it throws ReturnOrderNotDeliveredException -- a real return only applies post-delivery") {
                try {
                    service.requestReturn("buyer_1", "order_2", OrderReturnType.RETURN, "defective", null)
                    error("expected ReturnOrderNotDeliveredException")
                } catch (e: ReturnOrderNotDeliveredException) {
                    // expected
                }
            }
        }

        When("someone who isn't the real buyer tries to request a return") {
            every { orderRepository.findById("order_1") } returns Optional.of(deliveredOrder())

            Then("it throws ReturnOrderNotFoundException -- a real 404, not a 403 that would confirm the order exists") {
                try {
                    service.requestReturn("stranger", "order_1", OrderReturnType.RETURN, "defective", null)
                    error("expected ReturnOrderNotFoundException")
                } catch (e: ReturnOrderNotFoundException) {
                    // expected
                }
            }
        }

        When("the reason code isn't a real, recognized one") {
            every { orderRepository.findById("order_1") } returns Optional.of(deliveredOrder())

            Then("it throws InvalidReturnReasonException") {
                try {
                    service.requestReturn("buyer_1", "order_1", OrderReturnType.RETURN, "MADE_UP_REASON", null)
                    error("expected InvalidReturnReasonException")
                } catch (e: InvalidReturnReasonException) {
                    // expected
                }
            }
        }

        When("a return request is already pending review for this order") {
            every { orderRepository.findById("order_1") } returns Optional.of(deliveredOrder())
            every { orderReturnRequestRepository.findByOrderId("order_1") } returns listOf(
                OrderReturnRequest(id = "existing", orderId = "order_1", buyerId = "buyer_1", merchantId = "merchant_1", type = OrderReturnType.RETURN, reasonCode = "DEFECTIVE", reasonNote = null),
            )

            Then("it throws ReturnAlreadyRequestedException rather than creating a second concurrent request") {
                try {
                    service.requestReturn("buyer_1", "order_1", OrderReturnType.EXCHANGE, "wrong_item", null)
                    error("expected ReturnAlreadyRequestedException")
                } catch (e: ReturnAlreadyRequestedException) {
                    // expected
                }
            }
        }
    }

    Given("a seller deciding a real pending return request") {
        val orderRepository = mockk<OrderRepository>()
        val orderReturnRequestRepository = mockk<OrderReturnRequestRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val ledgerService = mockk<LedgerService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = OrderReturnService(orderRepository, orderReturnRequestRepository, merchantRepository, ledgerEntryRepository, ledgerService, notificationRepository, pushNotificationService)

        val pendingReturn = OrderReturnRequest(
            id = "return_1", orderId = "order_1", buyerId = "buyer_1", merchantId = "merchant_1",
            type = OrderReturnType.RETURN, reasonCode = "DEFECTIVE", reasonNote = null,
        )

        When("the real seller approves a real RETURN request") {
            every { orderReturnRequestRepository.findByIdForUpdate("return_1") } returns Optional.of(pendingReturn)
            every { merchantRepository.findByOwnerUserId("seller_1") } returns merchant
            every { orderRepository.findById("order_1") } returns Optional.of(deliveredOrder())
            val originalEntries = listOf(
                LedgerEntry(id = "le_1", transactionId = "ledgertxn_1", accountId = "account_buyer", accountType = LedgerAccountType.WALLET, direction = LedgerDirection.DEBIT, amount = BigDecimal("6000"), currency = "RWF", balanceAfter = BigDecimal("94000"), memo = "Order - Kigali Store"),
                LedgerEntry(id = "le_2", transactionId = "ledgertxn_1", accountId = "account_merchant", accountType = LedgerAccountType.WALLET, direction = LedgerDirection.CREDIT, amount = BigDecimal("5910"), currency = "RWF", balanceAfter = BigDecimal("5910"), memo = "Order collection - Kigali Store"),
                LedgerEntry(id = "le_3", transactionId = "ledgertxn_1", accountId = "fee_revenue", accountType = LedgerAccountType.FEE_REVENUE, direction = LedgerDirection.CREDIT, amount = BigDecimal("90"), currency = "RWF", balanceAfter = BigDecimal("90"), memo = "Order fee - Kigali Store"),
            )
            every { ledgerEntryRepository.findByTransactionId("ledgertxn_1") } returns originalEntries
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("refund_txn_1", emptyList())
            every { orderReturnRequestRepository.save(any()) } answers { firstArg() }

            val result = service.decide("seller_1", "return_1", approve = true)

            Then("it real-flips every original leg's direction, same reversing-ledger-entry technique cancelOrder already established") {
                val legs = legsSlot.captured
                legs.first { it.accountId == "account_buyer" }.direction shouldBe LedgerDirection.CREDIT
                legs.first { it.accountId == "account_merchant" }.direction shouldBe LedgerDirection.DEBIT
                legs.first { it.accountId == "fee_revenue" }.direction shouldBe LedgerDirection.DEBIT
            }
            Then("it marks the request APPROVED with a real refund transaction id") {
                result.status shouldBe OrderReturnStatus.APPROVED
                result.refundTransactionId shouldBe "refund_txn_1"
            }
            Then("it real-notifies the buyer") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "buyer_1" && it.type == "COMMERCE_RETURN_DECIDED" }) }
            }

            Then("the buyer also gets a real mobile push notification, not just the in-app one") {
                verify(exactly = 1) { pushNotificationService.sendToUser("buyer_1", "Return approved", any(), any()) }
            }
        }

        When("a return refund decision is still inside its transaction") {
            every { orderReturnRequestRepository.findByIdForUpdate("return_1") } returns Optional.of(pendingReturn)
            every { merchantRepository.findByOwnerUserId("seller_1") } returns merchant
            every { orderRepository.findById("order_1") } returns Optional.of(deliveredOrder())
            every { ledgerEntryRepository.findByTransactionId("ledgertxn_1") } returns listOf(
                LedgerEntry(id = "le_1", transactionId = "ledgertxn_1", accountId = "account_buyer", accountType = LedgerAccountType.WALLET, direction = LedgerDirection.DEBIT, amount = BigDecimal("6000"), currency = "RWF", balanceAfter = BigDecimal("94000"), memo = "Order"),
            )
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("refund_after_commit", emptyList())
            every { orderReturnRequestRepository.save(any()) } answers { firstArg() }

            TransactionSynchronizationManager.initSynchronization()
            try {
                service.decide("seller_1", "return_1", approve = true)

                Then("the refund decision and durable notification are recorded, but the buyer push is withheld") {
                    verify(exactly = 1) { orderReturnRequestRepository.save(any()) }
                    verify(exactly = 1) { notificationRepository.save(match { it.userId == "buyer_1" && it.type == "COMMERCE_RETURN_DECIDED" }) }
                    verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any()) }
                }

                Then("the buyer receives the refund decision only after commit") {
                    TransactionSynchronizationManager.getSynchronizations().single().afterCommit()
                    verify(exactly = 1) {
                        pushNotificationService.sendToUser(
                            "buyer_1",
                            "Return approved",
                            any(),
                            mapOf("orderId" to "order_1", "returnRequestId" to "return_1"),
                        )
                    }
                }
            } finally {
                TransactionSynchronizationManager.clearSynchronization()
            }
        }

        When("the real seller approves a real EXCHANGE request") {
            val exchangeRequest = OrderReturnRequest(
                id = "return_2", orderId = "order_1", buyerId = "buyer_1", merchantId = "merchant_1",
                type = OrderReturnType.EXCHANGE, reasonCode = "SIZE_FIT", reasonNote = null,
            )
            every { orderReturnRequestRepository.findByIdForUpdate("return_2") } returns Optional.of(exchangeRequest)
            every { merchantRepository.findByOwnerUserId("seller_1") } returns merchant
            every { orderReturnRequestRepository.save(any()) } answers { firstArg() }

            val result = service.decide("seller_1", "return_2", approve = true)

            Then("it never touches the ledger -- an exchange moves no money by itself") {
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                result.status shouldBe OrderReturnStatus.APPROVED
                result.refundTransactionId shouldBe null
            }
        }

        When("the real seller rejects a real pending request") {
            every { orderReturnRequestRepository.findByIdForUpdate("return_1") } returns Optional.of(pendingReturn)
            every { merchantRepository.findByOwnerUserId("seller_1") } returns merchant
            every { orderReturnRequestRepository.save(any()) } answers { firstArg() }

            val result = service.decide("seller_1", "return_1", approve = false)

            Then("it marks the request REJECTED without ever touching the ledger") {
                result.status shouldBe OrderReturnStatus.REJECTED
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }

        When("someone who isn't the real seller for this order tries to decide it") {
            every { orderReturnRequestRepository.findByIdForUpdate("return_1") } returns Optional.of(pendingReturn)
            val otherMerchant = Merchant(id = "merchant_2", ownerUserId = "other_seller", accountId = "account_other", businessName = "Other Store", status = MerchantStatus.ACTIVE)
            every { merchantRepository.findByOwnerUserId("other_seller") } returns otherMerchant

            // Real 404 (not 403) -- found live in a 2026-08-02 audit pass: a non-owning
            // merchant must get the same "not found" a genuinely unknown returnRequestId
            // would, never a distinguishable 403 that confirms the id exists.
            Then("it throws ReturnRequestNotFoundException, not a distinguishable 403") {
                try {
                    service.decide("other_seller", "return_1", approve = true)
                    error("expected ReturnRequestNotFoundException")
                } catch (e: ReturnRequestNotFoundException) {
                    verify(exactly = 0) { orderReturnRequestRepository.save(any()) }
                }
            }
        }

        When("the real request has already been decided") {
            val decided = OrderReturnRequest(
                id = "return_3", orderId = "order_1", buyerId = "buyer_1", merchantId = "merchant_1",
                type = OrderReturnType.RETURN, reasonCode = "DEFECTIVE", reasonNote = null, status = OrderReturnStatus.APPROVED,
            )
            every { orderReturnRequestRepository.findByIdForUpdate("return_3") } returns Optional.of(decided)
            every { merchantRepository.findByOwnerUserId("seller_1") } returns merchant

            Then("it throws ReturnRequestAlreadyDecidedException rather than re-deciding it") {
                try {
                    service.decide("seller_1", "return_3", approve = false)
                    error("expected ReturnRequestAlreadyDecidedException")
                } catch (e: ReturnRequestAlreadyDecidedException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
