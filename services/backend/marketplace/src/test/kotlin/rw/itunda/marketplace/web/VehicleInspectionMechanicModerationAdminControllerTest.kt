package rw.itunda.marketplace.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.VehicleInspectionMechanic
import rw.itunda.marketplace.MechanicNotFoundException
import rw.itunda.marketplace.VehicleInspectionService

// First test coverage for the real mechanic-moderation admin surface (Vehicle
// product-completeness pass, 2026-09-07) -- mechanics are real money-receiving
// business actors that previously had zero admin lever, unlike
// MerchantModerationAdminController. Mapped under the ADMIN-gated system route
// prefix so SecurityConfig's existing hasRole("ADMIN") rule applies (not
// independently testable at this plain-object unit-test tier -- same established
// limit CertificateControllerTest's own doc comment already names).
class VehicleInspectionMechanicModerationAdminControllerTest : BehaviorSpec({

    Given("a real list of registered mechanics") {
        val service = mockk<VehicleInspectionService>()
        val controller = VehicleInspectionMechanicModerationAdminController(service)
        val mechanic = VehicleInspectionMechanic(id = "mechanic_1", userId = "user_1", accountId = "account_1", businessName = "Kigali Auto Care")
        every { service.getAllMechanics() } returns listOf(mechanic)

        When("listing them") {
            val response = controller.list()

            Then("it real-delegates and reports every real mechanic, suspended state included") {
                verify(exactly = 1) { service.getAllMechanics() }
                @Suppress("UNCHECKED_CAST")
                val mechanics = response.body?.get("mechanics") as List<Map<String, Any?>>
                mechanics.first()["mechanicId"] shouldBe "mechanic_1"
                mechanics.first()["suspended"] shouldBe false
            }
        }
    }

    Given("an admin suspending a real mechanic") {
        val service = mockk<VehicleInspectionService>()
        val controller = VehicleInspectionMechanicModerationAdminController(service)
        val suspended = VehicleInspectionMechanic(id = "mechanic_1", userId = "user_1", accountId = "account_1", businessName = "Kigali Auto Care", suspended = true)
        every { service.suspendMechanic("mechanic_1") } returns suspended

        When("suspending") {
            val response = controller.suspend("mechanic_1")

            Then("it real-delegates to the service") {
                verify(exactly = 1) { service.suspendMechanic("mechanic_1") }
                response.body?.get("mechanic") shouldBe suspended
            }
        }
    }

    Given("an admin reactivating a real mechanic") {
        val service = mockk<VehicleInspectionService>()
        val controller = VehicleInspectionMechanicModerationAdminController(service)
        val reactivated = VehicleInspectionMechanic(id = "mechanic_1", userId = "user_1", accountId = "account_1", businessName = "Kigali Auto Care", suspended = false)
        every { service.reactivateMechanic("mechanic_1") } returns reactivated

        When("reactivating") {
            val response = controller.reactivate("mechanic_1")

            Then("it real-delegates to the service") {
                verify(exactly = 1) { service.reactivateMechanic("mechanic_1") }
                response.body?.get("mechanic") shouldBe reactivated
            }
        }
    }

    Given("a real MechanicNotFoundException") {
        val service = mockk<VehicleInspectionService>()
        val controller = VehicleInspectionMechanicModerationAdminController(service)

        When("its exception handler maps it to a real HTTP response") {
            val response = controller.handleNotFound(MechanicNotFoundException("Mechanic not found"))

            Then("it maps to 404 with code MECHANIC_NOT_FOUND, not a generic 500") {
                response.statusCode shouldBe HttpStatus.NOT_FOUND
                response.body?.code shouldBe "MECHANIC_NOT_FOUND"
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
