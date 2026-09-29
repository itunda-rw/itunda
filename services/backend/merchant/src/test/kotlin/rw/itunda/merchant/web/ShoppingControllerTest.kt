package rw.itunda.merchant.web

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import rw.itunda.core.domain.Conversation
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.repository.MenuOptionChoiceRepository
import rw.itunda.core.repository.MenuOptionGroupRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.FrequentlyOrderedWithProjection
import rw.itunda.core.repository.OrderItemRepository
import rw.itunda.core.repository.ProductOrderCountProjection
import rw.itunda.core.repository.ProductPriceTierRepository
import rw.itunda.core.repository.ProductReviewRepository
import rw.itunda.core.security.CurrentUser
import rw.itunda.merchant.MerchantProductService
import rw.itunda.merchant.ShoppingMerchantBrowseService
import rw.itunda.messaging.SelfConversationException
import rw.itunda.messaging.MessagingService
import java.math.BigDecimal
import java.util.Optional

class ShoppingControllerTest : BehaviorSpec({

    fun controller(
        merchantRepository: MerchantRepository = mockk(),
        merchantProductRepository: MerchantProductRepository = mockk(),
        orderItemRepository: OrderItemRepository = mockk(),
        messagingService: MessagingService = mockk(),
        productReviewRepository: ProductReviewRepository = mockk(relaxed = true),
    ) = ShoppingController(
        merchantRepository,
        merchantProductRepository,
        mockk<MenuOptionGroupRepository>(relaxed = true),
        mockk<MenuOptionChoiceRepository>(relaxed = true),
        mockk<ProductPriceTierRepository>(relaxed = true),
        mockk<MerchantProductService>(relaxed = true),
        productReviewRepository,
        orderItemRepository,
        messagingService,
        mockk<ShoppingMerchantBrowseService>(relaxed = true),
    )

    Given("a real buyer messaging a real registered merchant's seller") {
        val merchantRepository = mockk<MerchantRepository>()
        val messagingService = mockk<MessagingService>()
        val merchant = mockk<Merchant>()
        every { merchant.ownerUserId } returns "user_seller"
        every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
        val conversation = mockk<Conversation>()
        every { messagingService.startOrGetConversation("user_buyer", "user_seller") } returns conversation

        val sut = controller(merchantRepository = merchantRepository, messagingService = messagingService)

        When("contacting the seller") {
            val response = sut.contactSeller("merchant_1", CurrentUser("user_buyer"))

            Then("it resolves the real conversation via the real shared messaging system") {
                response.statusCode.value() shouldBe 200
                response.body?.get("conversation") shouldBe conversation
                verify(exactly = 1) { messagingService.startOrGetConversation("user_buyer", "user_seller") }
            }
        }
    }

    Given("a merchant owner trying to message their own store") {
        val merchantRepository = mockk<MerchantRepository>()
        val messagingService = mockk<MessagingService>()
        val merchant = mockk<Merchant>()
        every { merchant.ownerUserId } returns "user_owner"
        every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
        every { messagingService.startOrGetConversation("user_owner", "user_owner") } throws SelfConversationException("Cannot start a conversation with yourself")

        val sut = controller(merchantRepository = merchantRepository, messagingService = messagingService)

        When("contacting their own store") {
            Then("it's rejected as an own-merchant attempt, not a raw self-conversation error") {
                shouldThrow<OwnMerchantException> {
                    sut.contactSeller("merchant_1", CurrentUser("user_owner"))
                }
            }
        }
    }

    Given("a contact-seller request against a merchant id that doesn't exist") {
        val merchantRepository = mockk<MerchantRepository>()
        every { merchantRepository.findById("missing") } returns Optional.empty()
        val sut = controller(merchantRepository = merchantRepository)

        When("contacting it") {
            Then("it 404s rather than reaching messaging at all") {
                shouldThrow<ShoppingMerchantNotFoundException> {
                    sut.contactSeller("missing", CurrentUser("user_buyer"))
                }
            }
        }
    }

    Given("a real Deals page where products have genuinely different real order counts") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val orderItemRepository = mockk<OrderItemRepository>()

        fun product(id: String) = MerchantProduct(
            id = id, merchantId = "merchant_1", name = "Product $id", price = BigDecimal("1000"),
        )
        val products = listOf("p1", "p2", "p3", "p4", "p5").map { product(it) }
        val page: Page<MerchantProduct> = PageImpl(products, PageRequest.of(0, 20), products.size.toLong())
        every { merchantProductRepository.findDeals(MerchantStatus.ACTIVE, null, any()) } returns page
        every { merchantRepository.findAllById(listOf("merchant_1")) } returns listOf(
            mockk<Merchant>().also { every { it.id } returns "merchant_1"; every { it.businessName } returns "Test Store" },
        )
        // p1: 10 real orders, p2: 5, p3: 3 (right at the threshold), p4: 2 (below
        // threshold), p5: 0 (never ordered) -- only the top 3 that clear
        // MIN_ORDERS_FOR_BEST_SELLER should end up flagged.
        every { orderItemRepository.getProductOrderCounts(products.map { it.id }) } returns listOf(
            projection("p1", 10), projection("p2", 5), projection("p3", 3), projection("p4", 2), projection("p5", 0),
        )

        val sut = controller(
            merchantRepository = merchantRepository,
            merchantProductRepository = merchantProductRepository,
            orderItemRepository = orderItemRepository,
        )

        When("fetching the Deals rail") {
            val response = sut.getDeals(null, PageRequest.of(0, 20))

            Then("only the real top sellers clearing the minimum are flagged, never the untouched or below-threshold ones") {
                @Suppress("UNCHECKED_CAST")
                val body = response.body?.get("products") as List<Map<String, Any?>>
                val flagged = body.filter { it["isBestSeller"] == true }.map { it["id"] }.toSet()
                flagged shouldBe setOf("p1", "p2", "p3")
                body.first { it["id"] == "p4" }["isBestSeller"] shouldBe false
                body.first { it["id"] == "p5" }["isBestSeller"] shouldBe false
            }
        }
    }
    Given("a real product with genuinely different real co-occurrence counts against other products") {
        val orderItemRepository = mockk<OrderItemRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val merchantRepository = mockk<MerchantRepository>()

        // co1: 5 real shared orders, co2: 3, co3: 2 (right at the threshold),
        // co4: 1 (below threshold, never surfaced) -- ranked highest-first, capped
        // at MAX_FREQUENTLY_ORDERED_WITH (4), so all three that clear the bar show.
        every { orderItemRepository.getFrequentlyOrderedWith("p1") } returns listOf(
            coOccurrenceProjection("co1", 5), coOccurrenceProjection("co2", 3),
            coOccurrenceProjection("co3", 2), coOccurrenceProjection("co4", 1),
        )
        fun product(id: String) = MerchantProduct(id = id, merchantId = "merchant_2", name = "Product $id", price = BigDecimal("500"))
        // Returned out of rank order deliberately, to prove the controller re-sorts
        // by the real co-occurrence ranking rather than trusting findAllById's order.
        every { merchantProductRepository.findAllById(listOf("co1", "co2", "co3")) } returns listOf(product("co3"), product("co1"), product("co2"))
        every { merchantRepository.findAllById(listOf("merchant_2")) } returns listOf(
            mockk<Merchant>().also { every { it.id } returns "merchant_2"; every { it.businessName } returns "Other Store" },
        )

        val sut = controller(
            orderItemRepository = orderItemRepository,
            merchantProductRepository = merchantProductRepository,
            merchantRepository = merchantRepository,
        )

        When("fetching what's frequently ordered with it") {
            val response = sut.getFrequentlyOrderedWith("p1")

            Then("only real pairs clearing the minimum co-occurrence show, ranked highest-first") {
                @Suppress("UNCHECKED_CAST")
                val body = response.body?.get("products") as List<Map<String, Any?>>
                body.map { it["id"] } shouldBe listOf("co1", "co2", "co3")
            }
        }
    }

    Given("a real product with no real co-purchases at all") {
        val orderItemRepository = mockk<OrderItemRepository>()
        every { orderItemRepository.getFrequentlyOrderedWith("p_lonely") } returns emptyList()
        val sut = controller(orderItemRepository = orderItemRepository)

        When("fetching what's frequently ordered with it") {
            val response = sut.getFrequentlyOrderedWith("p_lonely")

            Then("it's honestly empty, never padded with unrelated products") {
                @Suppress("UNCHECKED_CAST")
                val body = response.body?.get("products") as List<Map<String, Any?>>
                body shouldBe emptyList()
            }
        }
    }
})

private fun projection(productId: String, count: Long): ProductOrderCountProjection =
    object : ProductOrderCountProjection {
        override val productId = productId
        override val count = count
    }

private fun coOccurrenceProjection(productId: String, count: Long): FrequentlyOrderedWithProjection =
    object : FrequentlyOrderedWithProjection {
        override val productId = productId
        override val count = count
    }
