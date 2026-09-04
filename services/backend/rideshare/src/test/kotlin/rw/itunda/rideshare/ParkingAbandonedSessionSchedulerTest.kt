package rw.itunda.rideshare

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.ParkingSession

/**
 * Same loop-resilience contract every other per-item scheduler in this codebase
 * proves -- see ParkingAbandonedSessionScheduler's own doc comment.
 */
class ParkingAbandonedSessionSchedulerTest : BehaviorSpec({
    fun session(id: String) = ParkingSession(id = id, spotId = "spot_1", renterUserId = "renter_1")

    Given("3 abandoned parking sessions, where the middle one's force-end fails") {
        val service = mockk<ParkingService>()
        val sessions = listOf(session("park_1"), session("park_2"), session("park_3"))
        every { service.getAbandonedSessions() } returns sessions
        every { service.forceEndAbandonedSession("park_1") } returns sessions[0]
        every { service.forceEndAbandonedSession("park_2") } throws IllegalStateException("owner account not found")
        every { service.forceEndAbandonedSession("park_3") } returns sessions[2]
        val scheduler = ParkingAbandonedSessionScheduler(service)

        When("the scheduler sweeps abandoned sessions") {
            scheduler.run()

            Then("all 3 sessions are still attempted, not just the ones before the failure") {
                verify(exactly = 1) { service.forceEndAbandonedSession("park_1") }
                verify(exactly = 1) { service.forceEndAbandonedSession("park_2") }
                verify(exactly = 1) { service.forceEndAbandonedSession("park_3") }
            }
        }
    }

    Given("no abandoned parking sessions") {
        val service = mockk<ParkingService>()
        every { service.getAbandonedSessions() } returns emptyList()
        val scheduler = ParkingAbandonedSessionScheduler(service)

        When("the scheduler sweeps abandoned sessions") {
            scheduler.run()

            Then("it does nothing") {
                verify(exactly = 0) { service.forceEndAbandonedSession(any()) }
            }
        }
    }
})
