package rw.itunda.vehicle

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Vehicle
import rw.itunda.core.repository.VehicleRepository
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Real Toss 내 차 시세-style vehicle value estimator -- see VehicleValuationService's
 * own doc comment for the full sourced account and the honest, documented depreciation
 * curve this pins down.
 */
class VehicleValuationServiceTest : BehaviorSpec({

    Given("a real registered vehicle") {
        val vehicleRepository = mockk<VehicleRepository>(relaxed = true)
        every { vehicleRepository.save(any()) } answers { firstArg() }
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = VehicleValuationService(vehicleRepository, rateLimiter)

        When("registering a vehicle with valid details") {
            val vehicle = service.registerVehicle(
                "user_1", "Toyota", "RAV4", 2022, BigDecimal("20000000"), LocalDate.now().minusYears(1), 15000,
            )

            Then("it saves a real vehicle row") {
                vehicle.make shouldBe "Toyota"
                vehicle.model shouldBe "RAV4"
                vehicle.purchasePrice shouldBe BigDecimal("20000000")
            }
        }

        When("the model year is nonsensical") {
            Then("far in the future is rejected") {
                try {
                    service.registerVehicle("user_1", "Toyota", "RAV4", 2099, BigDecimal("20000000"), LocalDate.now(), 0)
                    throw AssertionError("expected InvalidVehicleException")
                } catch (e: InvalidVehicleException) {
                    // expected
                }
            }

            Then("before cars existed is rejected") {
                try {
                    service.registerVehicle("user_1", "Toyota", "RAV4", 1900, BigDecimal("20000000"), LocalDate.now(), 0)
                    throw AssertionError("expected InvalidVehicleException")
                } catch (e: InvalidVehicleException) {
                    // expected
                }
            }
        }

        When("the purchase date is in the future") {
            Then("it's rejected") {
                try {
                    service.registerVehicle("user_1", "Toyota", "RAV4", 2022, BigDecimal("20000000"), LocalDate.now().plusDays(1), 0)
                    throw AssertionError("expected InvalidVehicleException")
                } catch (e: InvalidVehicleException) {
                    // expected
                }
            }
        }

        When("the purchase price is zero or negative") {
            Then("it's rejected") {
                try {
                    service.registerVehicle("user_1", "Toyota", "RAV4", 2022, BigDecimal.ZERO, LocalDate.now(), 0)
                    throw AssertionError("expected InvalidVehicleException")
                } catch (e: InvalidVehicleException) {
                    // expected
                }
            }
        }
    }

    // Real repo-wide rate-limiter-verify sweep (2026-09-09) -- rateLimiter was mocked
    // relaxed = true everywhere else in this file, with no test anywhere exercising
    // registerVehicle's own real rateLimiter.checkLimit call -- a real regression (the
    // check silently deleted) would have gone undetected. Same throw-and-catch
    // convention this codebase's other rate-limited services already establish.
    Given("a user who has exceeded the real vehicle-registration rate limit") {
        val vehicleRepository = mockk<VehicleRepository>()
        val rateLimiter = mockk<RateLimiter>()
        val service = VehicleValuationService(vehicleRepository, rateLimiter)
        every { rateLimiter.checkLimit("vehicle:register:user_1", limit = 10, window = any()) } throws RateLimitExceededException("Too many requests")

        When("registering a vehicle") {
            Then("a real RateLimitExceededException fires before ever saving a real vehicle row") {
                try {
                    service.registerVehicle("user_1", "Toyota", "RAV4", 2022, BigDecimal("20000000"), LocalDate.now().minusYears(1), 15000)
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { vehicleRepository.save(any()) }
                }
            }
        }
    }

    Given("a real vehicle exactly at purchase time (age zero)") {
        val vehicleRepository = mockk<VehicleRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = VehicleValuationService(vehicleRepository, rateLimiter)
        val vehicle = Vehicle(
            id = "vehicle_1", userId = "user_1", make = "Toyota", model = "RAV4", modelYear = 2026,
            purchasePrice = BigDecimal("20000000"), purchaseDate = LocalDate.now(), mileageKm = 0,
        )
        every { vehicleRepository.findByIdAndUserId("vehicle_1", "user_1") } returns vehicle

        When("getting its valuation on day one") {
            val valuation = service.getValuation("user_1", "vehicle_1")

            Then("the current estimated value is real -- at or extremely close to the full purchase price, no depreciation applied yet") {
                (valuation.currentEstimatedValue >= BigDecimal("19900000")) shouldBe true
                (valuation.currentEstimatedValue <= BigDecimal("20000000")) shouldBe true
            }

            Then("the 1-year projection is real and lower than today's value -- a genuine depreciation curve, not a flat number") {
                (valuation.estimatedValueIn1Year < valuation.currentEstimatedValue) shouldBe true
            }

            Then("the 3-year projection is lower than the 1-year one -- depreciation keeps compounding forward, never reverses") {
                (valuation.estimatedValueIn3Years < valuation.estimatedValueIn1Year) shouldBe true
            }
        }
    }

    Given("two identical vehicles that differ only in real mileage") {
        val vehicleRepository = mockk<VehicleRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = VehicleValuationService(vehicleRepository, rateLimiter)

        val lowMileage = Vehicle(
            id = "vehicle_low", userId = "user_1", make = "Toyota", model = "RAV4", modelYear = 2024,
            purchasePrice = BigDecimal("20000000"), purchaseDate = LocalDate.now().minusYears(2), mileageKm = 5000,
        )
        val highMileage = Vehicle(
            id = "vehicle_high", userId = "user_1", make = "Toyota", model = "RAV4", modelYear = 2024,
            purchasePrice = BigDecimal("20000000"), purchaseDate = LocalDate.now().minusYears(2), mileageKm = 90000,
        )
        every { vehicleRepository.findByIdAndUserId("vehicle_low", "user_1") } returns lowMileage
        every { vehicleRepository.findByIdAndUserId("vehicle_high", "user_1") } returns highMileage

        When("comparing their current valuations") {
            val lowValuation = service.getValuation("user_1", "vehicle_low")
            val highValuation = service.getValuation("user_1", "vehicle_high")

            Then("the real high-mileage car is worth honestly less than the real low-mileage one, same age and purchase price") {
                (highValuation.currentEstimatedValue < lowValuation.currentEstimatedValue) shouldBe true
            }
        }
    }

    Given("an old real vehicle, well past the year-5 curve") {
        val vehicleRepository = mockk<VehicleRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = VehicleValuationService(vehicleRepository, rateLimiter)
        val vehicle = Vehicle(
            id = "vehicle_old", userId = "user_1", make = "Toyota", model = "Corolla", modelYear = 2005,
            purchasePrice = BigDecimal("15000000"), purchaseDate = LocalDate.now().minusYears(30), mileageKm = 300000,
        )
        every { vehicleRepository.findByIdAndUserId("vehicle_old", "user_1") } returns vehicle

        When("getting its valuation") {
            val valuation = service.getValuation("user_1", "vehicle_old")

            Then("it's floored, never real-negative or worth literally nothing") {
                (valuation.currentEstimatedValue >= BigDecimal("1500000")) shouldBe true
                (valuation.currentEstimatedValue > BigDecimal.ZERO) shouldBe true
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
