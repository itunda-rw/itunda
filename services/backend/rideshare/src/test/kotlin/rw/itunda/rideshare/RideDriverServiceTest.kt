package rw.itunda.rideshare

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.RideDriver
import rw.itunda.core.repository.RideDriverRepository
import rw.itunda.core.repository.WalletRepository
import java.time.LocalDate
import java.time.ZoneId

/**
 * Real Uber "Destination Filter" (help.uber.com/en-GB/driving-and-delivering/article/
 * driver-destination-filter) -- see RideDriverService.setDestination's own doc comment.
 * The dispatch-side eligibility restriction has its own coverage in
 * RideTripServiceTest.kt; this file covers the driver-facing set/clear/limit behavior
 * in isolation.
 */
class RideDriverServiceTest : BehaviorSpec({

    fun newService(
        rideDriverRepository: RideDriverRepository = mockk(),
        walletRepository: WalletRepository = mockk(relaxed = true),
        rateLimiter: RateLimiter = mockk(relaxed = true),
    ) = RideDriverService(rideDriverRepository, walletRepository, rateLimiter)

    Given("a real registered driver with no active Destination Filter") {
        val rideDriverRepository = mockk<RideDriverRepository>()
        val service = newService(rideDriverRepository = rideDriverRepository)
        val driver = RideDriver(id = "driver_1", userId = "driver_user_1", walletId = "wallet_1")
        every { rideDriverRepository.findByUserId("driver_user_1") } returns driver
        every { rideDriverRepository.save(any()) } answers { firstArg() }

        When("the driver sets a real destination") {
            val result = service.setDestination("driver_user_1", -1.9300, 30.1300)

            Then("it real-saves the coordinates and consumes one of the real 2 daily uses") {
                result.destinationLatitude shouldBe -1.9300
                result.destinationLongitude shouldBe 30.1300
                result.destinationUsesToday shouldBe 1
                result.destinationUsesResetDate shouldBe LocalDate.now(ZoneId.of("Africa/Kigali"))
            }
        }

        When("the driver clears their destination") {
            driver.destinationLatitude = -1.9300
            driver.destinationLongitude = 30.1300
            driver.destinationUsesToday = 1
            driver.destinationUsesResetDate = LocalDate.now(ZoneId.of("Africa/Kigali"))

            val result = service.clearDestination("driver_user_1")

            Then("it real-clears the coordinates without consuming another real daily use") {
                result.destinationLatitude shouldBe null
                result.destinationLongitude shouldBe null
                result.destinationUsesToday shouldBe 1
            }
        }

        When("the driver tries to set an invalid real coordinate") {
            Then("it rejects the request before touching the repository") {
                try {
                    service.setDestination("driver_user_1", 200.0, 30.0)
                    error("expected InvalidRideDriverLocationException")
                } catch (e: InvalidRideDriverLocationException) {
                    // expected
                }
            }
        }
    }

    Given("a real driver who has already used their real Destination Filter twice today") {
        val rideDriverRepository = mockk<RideDriverRepository>()
        val service = newService(rideDriverRepository = rideDriverRepository)
        val driver = RideDriver(
            id = "driver_2", userId = "driver_user_2", walletId = "wallet_2",
            destinationUsesToday = 2, destinationUsesResetDate = LocalDate.now(ZoneId.of("Africa/Kigali")),
        )
        every { rideDriverRepository.findByUserId("driver_user_2") } returns driver

        When("the driver tries to set a third real destination the same real day") {
            Then("it rejects with the real published Uber limit, not a silent third use") {
                try {
                    service.setDestination("driver_user_2", -1.93, 30.13)
                    error("expected DestinationFilterLimitExceededException")
                } catch (e: DestinationFilterLimitExceededException) {
                    // expected
                }
            }
        }
    }

    Given("a real driver whose Destination Filter uses were last reset on a previous real day") {
        val rideDriverRepository = mockk<RideDriverRepository>()
        val service = newService(rideDriverRepository = rideDriverRepository)
        val driver = RideDriver(
            id = "driver_3", userId = "driver_user_3", walletId = "wallet_3",
            destinationUsesToday = 2, destinationUsesResetDate = LocalDate.now(ZoneId.of("Africa/Kigali")).minusDays(1),
        )
        every { rideDriverRepository.findByUserId("driver_user_3") } returns driver
        every { rideDriverRepository.save(any()) } answers { firstArg() }

        When("the driver sets a real destination today") {
            val result = service.setDestination("driver_user_3", -1.93, 30.13)

            Then("it real-resets the daily counter to 1, not carrying yesterday's 2 uses forward") {
                result.destinationUsesToday shouldBe 1
                result.destinationUsesResetDate shouldBe LocalDate.now(ZoneId.of("Africa/Kigali"))
            }
        }
    }
})
