package rw.itunda.marketplace.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.MarketplaceEscrow
import rw.itunda.core.domain.MarketplaceEscrowStatus
import rw.itunda.core.security.CurrentUser
import rw.itunda.marketplace.InvalidEscrowStatusException
import rw.itunda.marketplace.MarketplaceEscrowNotFoundException
import rw.itunda.marketplace.MarketplaceService
import java.math.BigDecimal

// First test coverage for MarketplaceEscrowAdminController -- named as a real,
// deferred follow-up in the admin-accountability pass that gave this controller's
// resolve() endpoint its currentUser.userId capture (Bank/Merchant product-
// completeness pass, cycle 2, 2026-09-09) -- picked up in the very next cycle,
// matching this same pass's own reusable "found but not built" discipline. Mirrors
// VehicleInspectionMechanicModerationAdminControllerTest's shape exactly (same
// admin-gated /api/v1/system/** RBAC, not independently testable at this
// plain-object unit-test tier).
class MarketplaceEscrowAdminControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "admin_1")

    fun escrow(id: String, status: MarketplaceEscrowStatus, resolvedBy: String? = null) = MarketplaceEscrow(
        id = id, listingId = "listing_$id", buyerId = "buyer_$id", sellerId = "seller_$id",
        amount = BigDecimal("10000"), fee = BigDecimal("150"), holdTransactionId = "ledgertxn_$id",
        status = status, resolvedBy = resolvedBy,
    )

    Given("a real pending-dispute queue") {
        val marketplaceService = mockk<MarketplaceService>()
        val controller = MarketplaceEscrowAdminController(marketplaceService)
        val disputed = escrow("escrow_1", MarketplaceEscrowStatus.DISPUTED)
        every { marketplaceService.getPendingDisputes() } returns listOf(disputed)

        When("fetching it") {
            val response = controller.disputes()

            Then("it real-delegates to the service") {
                verify(exactly = 1) { marketplaceService.getPendingDisputes() }
                response.body?.get("disputes") shouldBe listOf(disputed)
            }
        }
    }

    Given("an admin resolving a real disputed escrow in the seller's favor") {
        val marketplaceService = mockk<MarketplaceService>()
        val controller = MarketplaceEscrowAdminController(marketplaceService)
        val released = escrow("escrow_2", MarketplaceEscrowStatus.RELEASED, resolvedBy = "admin_1")
        every { marketplaceService.resolveDispute("escrow_2", true, "admin_1") } returns released

        When("resolving with release=true") {
            val response = controller.resolve("escrow_2", ResolveEscrowDisputeRequest(release = true), currentUser)

            Then("it real-delegates to the service with the acting admin's id") {
                verify(exactly = 1) { marketplaceService.resolveDispute("escrow_2", true, "admin_1") }
                response.body?.get("escrow") shouldBe released
            }
        }
    }

    Given("an admin resolving a real disputed escrow in the buyer's favor") {
        val marketplaceService = mockk<MarketplaceService>()
        val controller = MarketplaceEscrowAdminController(marketplaceService)
        val refunded = escrow("escrow_3", MarketplaceEscrowStatus.REFUNDED, resolvedBy = "admin_1")
        every { marketplaceService.resolveDispute("escrow_3", false, "admin_1") } returns refunded

        When("resolving with release=false") {
            val response = controller.resolve("escrow_3", ResolveEscrowDisputeRequest(release = false), currentUser)

            Then("it real-delegates to the service with the acting admin's id") {
                verify(exactly = 1) { marketplaceService.resolveDispute("escrow_3", false, "admin_1") }
                response.body?.get("escrow") shouldBe refunded
            }
        }
    }

    Given("a real MarketplaceEscrowNotFoundException") {
        val controller = MarketplaceEscrowAdminController(mockk())

        When("its exception handler maps it to a real HTTP response") {
            val response = controller.handleNotFound(MarketplaceEscrowNotFoundException("Escrow not found"))

            Then("it maps to 404 with code ESCROW_NOT_FOUND, not a generic 500") {
                response.statusCode shouldBe HttpStatus.NOT_FOUND
                response.body?.code shouldBe "ESCROW_NOT_FOUND"
            }
        }
    }

    Given("a real InvalidEscrowStatusException") {
        val controller = MarketplaceEscrowAdminController(mockk())

        When("its exception handler maps it to a real HTTP response") {
            val response = controller.handleInvalidStatus(InvalidEscrowStatusException("Only a DISPUTED escrow can be resolved -- this one is HELD"))

            Then("it maps to 409 with code INVALID_ESCROW_STATUS, not a generic 500") {
                response.statusCode shouldBe HttpStatus.CONFLICT
                response.body?.code shouldBe "INVALID_ESCROW_STATUS"
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
