package rw.itunda.merchant

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.repository.MenuOptionChoiceRepository
import rw.itunda.core.repository.MenuOptionGroupRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import java.math.BigDecimal
import java.util.Optional

/**
 * Real menu-item option groups (2026-07-21, v1: required single-select only) -- see
 * MenuOptionGroup.kt's own doc comment for the full account of the gap this closes.
 * Mirrors MerchantProductServiceTest's own ownership/validation test shape.
 */
class MenuOptionServiceTest : BehaviorSpec({

    val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_1", walletId = "wallet_merchant", businessName = "Kigali Grill", status = MerchantStatus.ACTIVE)
    val product = MerchantProduct(id = "product_1", merchantId = "merchant_1", name = "Burger", price = BigDecimal("3000"))

    Given("a restaurant owner adding a real required option group to their own menu item") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val menuOptionGroupRepository = mockk<MenuOptionGroupRepository>()
        val menuOptionChoiceRepository = mockk<MenuOptionChoiceRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = MenuOptionService(merchantRepository, merchantProductRepository, menuOptionGroupRepository, menuOptionChoiceRepository, rateLimiter)

        every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
        every { merchantProductRepository.findById("product_1") } returns Optional.of(product)
        every { menuOptionGroupRepository.findByProductIdOrderByDisplayOrderAsc("product_1") } returns emptyList()
        every { menuOptionGroupRepository.save(any()) } answers { firstArg() }
        every { menuOptionChoiceRepository.saveAll(any<List<rw.itunda.core.domain.MenuOptionChoice>>()) } answers { firstArg() }

        When("adding a real 'Size' group with three real choices") {
            val view = service.addOptionGroup(
                "owner_1", "product_1", "Size",
                listOf(
                    MenuOptionChoiceRequest("Small", BigDecimal.ZERO),
                    MenuOptionChoiceRequest("Medium", BigDecimal("500")),
                    MenuOptionChoiceRequest("Large", BigDecimal("1000")),
                ),
            )

            Then("it creates the real group and its real choices, one real +0 RWF choice included") {
                view.group.name shouldBe "Size"
                view.group.productId shouldBe "product_1"
                view.choices.size shouldBe 3
                view.choices.first { it.name == "Small" }.priceDelta shouldBe BigDecimal.ZERO
                view.choices.first { it.name == "Large" }.priceDelta shouldBe BigDecimal("1000")
            }
        }

        When("adding a group with zero choices") {
            Then("it real-fails -- a required group with nothing to choose is meaningless") {
                try {
                    service.addOptionGroup("owner_1", "product_1", "Empty", emptyList())
                    error("expected InvalidMenuOptionGroupException")
                } catch (e: InvalidMenuOptionGroupException) {
                    // expected
                }
            }
        }

        When("adding a choice with a negative price delta") {
            Then("it real-fails -- would let a choice undercut the real base list price") {
                try {
                    service.addOptionGroup("owner_1", "product_1", "Size", listOf(MenuOptionChoiceRequest("Discount", BigDecimal("-100"))))
                    error("expected InvalidMenuOptionGroupException")
                } catch (e: InvalidMenuOptionGroupException) {
                    // expected
                }
            }
        }
    }

    Given("a restaurant owner trying to add an option group to SOMEONE ELSE'S menu item") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val menuOptionGroupRepository = mockk<MenuOptionGroupRepository>()
        val menuOptionChoiceRepository = mockk<MenuOptionChoiceRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = MenuOptionService(merchantRepository, merchantProductRepository, menuOptionGroupRepository, menuOptionChoiceRepository, rateLimiter)
        val othersProduct = MerchantProduct(id = "product_9", merchantId = "merchant_other", name = "Not Yours", price = BigDecimal("1000"))

        every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
        every { merchantProductRepository.findById("product_9") } returns Optional.of(othersProduct)

        When("attempting to add a group") {
            Then("it real-404s rather than leaking or mutating another merchant's menu item") {
                try {
                    service.addOptionGroup("owner_1", "product_9", "Size", listOf(MenuOptionChoiceRequest("Small")))
                    error("expected MerchantProductNotFoundException")
                } catch (e: MerchantProductNotFoundException) {
                    // expected
                }
            }
        }
    }

    Given("a buyer (or anyone) reading a real menu item's real option groups") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val menuOptionGroupRepository = mockk<MenuOptionGroupRepository>()
        val menuOptionChoiceRepository = mockk<MenuOptionChoiceRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = MenuOptionService(merchantRepository, merchantProductRepository, menuOptionGroupRepository, menuOptionChoiceRepository, rateLimiter)
        val group = rw.itunda.core.domain.MenuOptionGroup(id = "group_1", productId = "product_1", name = "Size")
        val choices = listOf(rw.itunda.core.domain.MenuOptionChoice(id = "choice_1", groupId = "group_1", name = "Small", priceDelta = BigDecimal.ZERO))

        every { menuOptionGroupRepository.findByProductIdOrderByDisplayOrderAsc("product_1") } returns listOf(group)
        every { menuOptionChoiceRepository.findByGroupIdInOrderByDisplayOrderAsc(listOf("group_1")) } returns choices

        When("fetched with no ownership gate at all") {
            val views = service.getOptionGroups("product_1")

            Then("it returns the real group with its real choices") {
                views.size shouldBe 1
                views.first().group.name shouldBe "Size"
                views.first().choices.first().name shouldBe "Small"
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
