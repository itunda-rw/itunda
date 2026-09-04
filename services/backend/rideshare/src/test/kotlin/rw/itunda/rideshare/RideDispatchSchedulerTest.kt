package rw.itunda.rideshare

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.RideTrip
import rw.itunda.core.domain.RideTripStatus
import java.math.BigDecimal

/**
 * First test coverage for RideDispatchScheduler -- 2 independent real sections
 * (expired-offer reassignment, due-scheduled-trip activation), each with its own
 * "one bad row can't poison the sweep" resilience loop and its own real
 * once-per-tick `computeDispatchPools()` optimization (only paid for a section
 * that has real work to do).
 */
class RideDispatchSchedulerTest : BehaviorSpec({

    fun trip(id: String, scheduled: Boolean = false) = RideTrip(
        id = id, passengerId = "passenger_$id", pickupAddress = "A", pickupLatitude = -1.9536, pickupLongitude = 30.0605,
        dropoffAddress = "B", dropoffLatitude = -1.9506, dropoffLongitude = 30.0925, distanceKm = BigDecimal("3.5"),
        fare = BigDecimal("1875"), platformFee = BigDecimal("28.13"), transactionId = "ledgertxn_$id",
        status = if (scheduled) RideTripStatus.REQUESTED else RideTripStatus.IN_PROGRESS,
    )

    Given("3 expired offers, where reassigning the middle one fails, and no due scheduled trips") {
        val rideTripService = mockk<RideTripService>()
        val pools = DispatchPools(candidatePool = emptyList(), busyDriverIds = emptySet())
        every { rideTripService.getExpiredOffers() } returns listOf(trip("t1"), trip("t2"), trip("t3"))
        every { rideTripService.computeDispatchPools() } returns pools
        every { rideTripService.reassignExpiredOffer("t1", pools) } returns Unit
        every { rideTripService.reassignExpiredOffer("t2", pools) } throws RuntimeException("no driver available")
        every { rideTripService.reassignExpiredOffer("t3", pools) } returns Unit
        every { rideTripService.getDueScheduledTrips() } returns emptyList()
        val scheduler = RideDispatchScheduler(rideTripService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 expired offers are still attempted -- the middle failure doesn't stop the sweep") {
                verify(exactly = 1) { rideTripService.reassignExpiredOffer("t1", pools) }
                verify(exactly = 1) { rideTripService.reassignExpiredOffer("t2", pools) }
                verify(exactly = 1) { rideTripService.reassignExpiredOffer("t3", pools) }
            }
            Then("the scheduled-trip section never computes its own pools -- nothing due this tick") {
                verify(exactly = 1) { rideTripService.computeDispatchPools() }
                verify(exactly = 0) { rideTripService.activateScheduledDispatchOne(any(), any()) }
            }
        }
    }

    Given("no expired offers, but 2 due scheduled trips where activating the first fails") {
        val rideTripService = mockk<RideTripService>()
        val pools = DispatchPools(candidatePool = emptyList(), busyDriverIds = emptySet())
        every { rideTripService.getExpiredOffers() } returns emptyList()
        every { rideTripService.getDueScheduledTrips() } returns listOf(trip("s1", scheduled = true), trip("s2", scheduled = true))
        every { rideTripService.computeDispatchPools() } returns pools
        every { rideTripService.activateScheduledDispatchOne("s1", pools) } throws RuntimeException("no driver available")
        every { rideTripService.activateScheduledDispatchOne("s2", pools) } returns Unit
        val scheduler = RideDispatchScheduler(rideTripService)

        When("the sweep runs") {
            scheduler.run()

            Then("the expired-offer section never computes its own pools -- nothing expired this tick") {
                verify(exactly = 0) { rideTripService.reassignExpiredOffer(any(), any()) }
            }
            Then("both scheduled trips are still attempted -- the first failure doesn't stop the second") {
                verify(exactly = 1) { rideTripService.activateScheduledDispatchOne("s1", pools) }
                verify(exactly = 1) { rideTripService.activateScheduledDispatchOne("s2", pools) }
            }
        }
    }

    Given("nothing due at all this tick") {
        val rideTripService = mockk<RideTripService>()
        every { rideTripService.getExpiredOffers() } returns emptyList()
        every { rideTripService.getDueScheduledTrips() } returns emptyList()
        val scheduler = RideDispatchScheduler(rideTripService)

        When("the sweep runs") {
            scheduler.run()

            Then("dispatch pools are never computed at all -- a real cost avoided when there's no work") {
                verify(exactly = 0) { rideTripService.computeDispatchPools() }
            }
        }
    }
})
