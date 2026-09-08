package rw.itunda.merchant.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.security.CurrentUser
import rw.itunda.merchant.MerchantFeeWaiverService
import rw.itunda.merchant.MerchantNotFoundException
import rw.itunda.merchant.MerchantNotWaivedException
import rw.itunda.merchant.MerchantService

// First test coverage for MerchantModerationAdminController -- named as a real,
// deferred follow-up in the admin-accountability pass that gave this controller's
// suspend/reactivate/revoke-fee-waiver endpoints their currentUser.userId capture
// (Bank/Merchant product-completeness pass, cycle 2, 2026-09-09) -- picked up in the
// very next cycle, matching this same pass's own reusable "found but not built"
// discipline. Mirrors VehicleInspectionMechanicModerationAdminControllerTest's shape
// exactly (same admin-gated /api/v1/system/** RBAC, not independently testable at
// this plain-object unit-test tier).
class MerchantModerationAdminControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "admin_1")

    Given("a real uncategorized-merchant moderation queue") {
        val merchantRepository = mockk<MerchantRepository>()
        val controller = MerchantModerationAdminController(mockk(), merchantRepository, mockk())
        val pageable = PageRequest.of(0, 50)
        val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_1", accountId = "account_1", businessName = "New Shop")
        val page = PageImpl(listOf(merchant))
        every { merchantRepository.findByStatusAndCategoryIsNull(MerchantStatus.ACTIVE, pageable) } returns page

        When("fetching it") {
            val response = controller.uncategorized(pageable)

            Then("it real-delegates and reports the real merchant, no category filled in") {
                verify(exactly = 1) { merchantRepository.findByStatusAndCategoryIsNull(MerchantStatus.ACTIVE, pageable) }
                @Suppress("UNCHECKED_CAST")
                val merchants = response.body?.get("merchants") as List<Map<String, Any?>>
                merchants.single()["merchantId"] shouldBe "merchant_1"
            }
        }
    }

    Given("an admin suspending a real merchant") {
        val merchantService = mockk<MerchantService>()
        val controller = MerchantModerationAdminController(merchantService, mockk(), mockk())
        val suspended = Merchant(id = "merchant_2", ownerUserId = "owner_2", accountId = "account_2", businessName = "Bad Shop", status = MerchantStatus.SUSPENDED)
        every { merchantService.suspendMerchant("merchant_2", "admin_1") } returns suspended

        When("suspending") {
            val response = controller.suspend("merchant_2", currentUser)

            Then("it real-delegates to the service with the acting admin's id") {
                verify(exactly = 1) { merchantService.suspendMerchant("merchant_2", "admin_1") }
                response.body?.get("status") shouldBe "SUSPENDED"
            }
        }
    }

    Given("an admin reactivating a real suspended merchant") {
        val merchantService = mockk<MerchantService>()
        val controller = MerchantModerationAdminController(merchantService, mockk(), mockk())
        val reactivated = Merchant(id = "merchant_3", ownerUserId = "owner_3", accountId = "account_3", businessName = "Reformed Shop", status = MerchantStatus.ACTIVE)
        every { merchantService.reactivateMerchant("merchant_3", "admin_1") } returns reactivated

        When("reactivating") {
            val response = controller.reactivate("merchant_3", currentUser)

            Then("it real-delegates to the service with the acting admin's id") {
                verify(exactly = 1) { merchantService.reactivateMerchant("merchant_3", "admin_1") }
                response.body?.get("status") shouldBe "ACTIVE"
            }
        }
    }

    Given("a real fee-waiver revocation candidate list") {
        val merchantFeeWaiverService = mockk<MerchantFeeWaiverService>()
        val controller = MerchantModerationAdminController(mockk(), mockk(), merchantFeeWaiverService)
        val candidate = mapOf("merchantId" to "merchant_4", "businessName" to "Grown Shop")
        every { merchantFeeWaiverService.getRevocationCandidates() } returns listOf(candidate)

        When("fetching it") {
            val response = controller.feeWaiverCandidates()

            Then("it real-delegates to the service") {
                verify(exactly = 1) { merchantFeeWaiverService.getRevocationCandidates() }
                response.body?.get("candidates") shouldBe listOf(candidate)
            }
        }
    }

    Given("an admin revoking a real merchant's fee waiver") {
        val merchantFeeWaiverService = mockk<MerchantFeeWaiverService>()
        val controller = MerchantModerationAdminController(mockk(), mockk(), merchantFeeWaiverService)
        val revoked = Merchant(id = "merchant_5", ownerUserId = "owner_5", accountId = "account_5", businessName = "Grown Shop")
        every { merchantFeeWaiverService.revokeFeeWaiver("merchant_5", "admin_1") } returns revoked

        When("revoking") {
            val response = controller.revokeFeeWaiver("merchant_5", currentUser)

            Then("it real-delegates to the service with the acting admin's id") {
                verify(exactly = 1) { merchantFeeWaiverService.revokeFeeWaiver("merchant_5", "admin_1") }
                response.body?.get("merchantId") shouldBe "merchant_5"
            }
        }
    }

    Given("a real MerchantNotFoundException") {
        val controller = MerchantModerationAdminController(mockk(), mockk(), mockk())

        When("its exception handler maps it to a real HTTP response") {
            val response = controller.handleNotFound(MerchantNotFoundException("Merchant not found"))

            Then("it maps to 404 with code MERCHANT_NOT_FOUND, not a generic 500") {
                response.statusCode shouldBe HttpStatus.NOT_FOUND
                response.body?.code shouldBe "MERCHANT_NOT_FOUND"
            }
        }
    }

    Given("a real MerchantNotWaivedException") {
        val controller = MerchantModerationAdminController(mockk(), mockk(), mockk())

        When("its exception handler maps it to a real HTTP response") {
            val response = controller.handleNotWaived(MerchantNotWaivedException("This merchant does not have an active fee waiver to revoke"))

            Then("it maps to 409 with code MERCHANT_NOT_WAIVED, not a generic 500") {
                response.statusCode shouldBe HttpStatus.CONFLICT
                response.body?.code shouldBe "MERCHANT_NOT_WAIVED"
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
