package rw.itunda.rideshare

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.BikeRentalSession

/**
 * Same loop-resilience contract every other per-item scheduler in this codebase
 * proves -- see BikeRentalAbandonedSessionScheduler's own doc comment.
 */
class BikeRentalAbandonedSessionSchedulerTest : BehaviorSpec({
    fun session(id: String) = BikeRentalSession(id = id, bikeId = "bike_1", riderUserId = "rider_1", startLatitude = -1.95, startLongitude = 30.06)

    Given("3 abandoned bike rentals, where the middle one's force-end fails") {
        val service = mockk<BikeRentalService>()
        val sessions = listOf(session("bike_ses_1"), session("bike_ses_2"), session("bike_ses_3"))
        every { service.getAbandonedRentals() } returns sessions
        every { service.forceEndAbandonedRental("bike_ses_1") } returns sessions[0]
        every { service.forceEndAbandonedRental("bike_ses_2") } throws IllegalStateException("owner account not found")
        every { service.forceEndAbandonedRental("bike_ses_3") } returns sessions[2]
        val scheduler = BikeRentalAbandonedSessionScheduler(service)

        When("the scheduler sweeps abandoned rentals") {
            scheduler.run()

            Then("all 3 rentals are still attempted, not just the ones before the failure") {
                verify(exactly = 1) { service.forceEndAbandonedRental("bike_ses_1") }
                verify(exactly = 1) { service.forceEndAbandonedRental("bike_ses_2") }
                verify(exactly = 1) { service.forceEndAbandonedRental("bike_ses_3") }
            }
        }
    }

    Given("no abandoned bike rentals") {
        val service = mockk<BikeRentalService>()
        every { service.getAbandonedRentals() } returns emptyList()
        val scheduler = BikeRentalAbandonedSessionScheduler(service)

        When("the scheduler sweeps abandoned rentals") {
            scheduler.run()

            Then("it does nothing") {
                verify(exactly = 0) { service.forceEndAbandonedRental(any()) }
            }
        }
    }
})
