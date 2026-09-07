package rw.itunda.vehicle.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.domain.Vehicle
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import rw.itunda.vehicle.InvalidVehicleException
import rw.itunda.vehicle.VehicleNotFoundException
import rw.itunda.vehicle.VehicleValuationService
import java.math.BigDecimal
import java.time.LocalDate

/**
 * First test coverage for VehicleController's REST layer -- the same gap class the
 * Certificate product-completeness pass already found and fixed once
 * (CertificateControllerTest.kt). What this file protects: the real idempotency
 * wiring added 2026-09-07 (same "lost response -> resubmit -> duplicate row" bug
 * class VehicleInspectionControllerTest.kt already guards for
 * registerAsMechanic), controller-to-service delegation, and the exception-to-
 * HTTP-status mapping, none of which had any test before this pass.
 */
class VehicleControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")
    val request = RegisterVehicleRequest(
        make = "Toyota", model = "RAV4", modelYear = 2020,
        purchasePrice = BigDecimal("15000000"), purchaseDate = LocalDate.of(2020, 1, 1), mileageKm = 30000,
    )

    Given("a first-time vehicle registration request") {
        val service = mockk<VehicleValuationService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = VehicleController(service, idempotencyService)

        val vehicle = mockk<Vehicle>(relaxed = true)
        every {
            service.registerVehicle("user_1", "Toyota", "RAV4", 2020, BigDecimal("15000000"), LocalDate.of(2020, 1, 1), 30000)
        } returns vehicle

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/vehicles", "key-1", request, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("registering the vehicle") {
            val response = controller.register(request, "key-1", currentUser)

            Then("it routes through the real idempotency service, keyed to this exact route and the real request body") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/vehicles", "key-1", request, any()) }
                verify(exactly = 1) { service.registerVehicle("user_1", "Toyota", "RAV4", 2020, BigDecimal("15000000"), LocalDate.of(2020, 1, 1), 30000) }
                response.statusCode shouldBe HttpStatus.CREATED
                response.body?.get("vehicle") shouldBe vehicle
            }
        }
    }

    Given("a retried vehicle registration request using the same Idempotency-Key as a completed one") {
        val service = mockk<VehicleValuationService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = VehicleController(service, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/vehicles", "key-1", request, any())
        } returns (201 to mapOf("success" to true, "vehicle" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.register(request, "key-1", currentUser)

            Then("the cached response is returned and a duplicate vehicle is never registered") {
                response.body?.get("vehicle") shouldBe "cached-result"
                verify(exactly = 0) { service.registerVehicle(any(), any(), any(), any(), any(), any(), any()) }
            }
        }
    }

    Given("a request for the caller's own vehicles") {
        val service = mockk<VehicleValuationService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = VehicleController(service, idempotencyService)

        val vehicles = listOf(mockk<Vehicle>(relaxed = true))
        every { service.getMyVehicles("user_1") } returns vehicles

        When("listing them") {
            val response = controller.getMine(currentUser)

            Then("it queries only by the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { service.getMyVehicles("user_1") }
                response.body?.get("vehicles") shouldBe vehicles
            }
        }
    }

    Given("a mileage update request") {
        val service = mockk<VehicleValuationService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = VehicleController(service, idempotencyService)

        val vehicle = mockk<Vehicle>(relaxed = true)
        every { service.updateMileage("user_1", "vehicle_1", 45000) } returns vehicle

        When("updating") {
            val response = controller.updateMileage("vehicle_1", UpdateVehicleMileageRequest(45000), currentUser)

            Then("it delegates to the service scoped to the caller's own userId and vehicleId") {
                verify(exactly = 1) { service.updateMileage("user_1", "vehicle_1", 45000) }
                response.body?.get("vehicle") shouldBe vehicle
            }
        }
    }

    Given("a remove request") {
        val service = mockk<VehicleValuationService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = VehicleController(service, idempotencyService)
        every { service.removeVehicle("user_1", "vehicle_1") } returns Unit

        When("removing") {
            val response = controller.remove("vehicle_1", currentUser)

            Then("it delegates to the service scoped to the caller's own userId and vehicleId") {
                verify(exactly = 1) { service.removeVehicle("user_1", "vehicle_1") }
                response.body?.get("success") shouldBe true
            }
        }
    }

    listOf(
        Pair(VehicleNotFoundException("Not found") as RuntimeException, HttpStatus.NOT_FOUND to "VEHICLE_NOT_FOUND"),
        Pair(InvalidVehicleException("Bad request"), HttpStatus.BAD_REQUEST to "INVALID_VEHICLE"),
        Pair(RateLimitExceededException("Too many requests"), HttpStatus.TOO_MANY_REQUESTS to "RATE_LIMITED"),
    ).forEach { (exception, expected) ->
        val (expectedStatus, expectedCode) = expected
        Given("a real ${exception::class.simpleName}") {
            val service = mockk<VehicleValuationService>()
            val idempotencyService = mockk<IdempotencyService>()
            val controller = VehicleController(service, idempotencyService)

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is VehicleNotFoundException -> controller.handleNotFound(exception)
                    is InvalidVehicleException -> controller.handleInvalid(exception)
                    is RateLimitExceededException -> controller.handleRateLimit(exception)
                    else -> error("unexpected exception type")
                }

                Then("it maps to $expectedStatus with code $expectedCode, not a generic 500") {
                    response.statusCode shouldBe expectedStatus
                    response.body?.code shouldBe expectedCode
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
