package rw.itunda.marketplace

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.VehicleInspectionBooking
import java.math.BigDecimal
import java.time.Instant

/**
 * Same loop-resilience contract every other per-item scheduler in this codebase
 * proves -- see VehicleInspectionNoShowScheduler's own doc comment for why a bare
 * loop with no try/catch here would have been a real transaction-poisoning bug.
 */
class VehicleInspectionNoShowSchedulerTest : BehaviorSpec({
    fun booking(id: String) = VehicleInspectionBooking(
        id = id,
        listingId = "listing_1",
        buyerId = "buyer_1",
        mechanicId = "mechanic_1",
        fee = BigDecimal("50000"),
        platformFee = BigDecimal("5000"),
        scheduledFor = Instant.now().minusSeconds(3600),
        holdTransactionId = "txn_hold",
    )

    Given("3 past-due vehicle inspection bookings, where the middle one's payout fails") {
        val service = mockk<VehicleInspectionService>()
        val bookings = listOf(booking("insp_1"), booking("insp_2"), booking("insp_3"))
        every { service.findDueNoShows() } returns bookings
        every { service.processNoShow("insp_1") } returns bookings[0]
        every { service.processNoShow("insp_2") } throws IllegalStateException("mechanic account not found")
        every { service.processNoShow("insp_3") } returns bookings[2]
        val scheduler = VehicleInspectionNoShowScheduler(service)

        When("the scheduler sweeps due no-shows") {
            scheduler.run()

            Then("all 3 bookings are still attempted, not just the ones before the failure") {
                verify(exactly = 1) { service.processNoShow("insp_1") }
                verify(exactly = 1) { service.processNoShow("insp_2") }
                verify(exactly = 1) { service.processNoShow("insp_3") }
            }
        }
    }

    Given("a due booking that was already resolved by the time the sweep runs") {
        val service = mockk<VehicleInspectionService>()
        every { service.findDueNoShows() } returns listOf(booking("insp_stale"))
        every { service.processNoShow("insp_stale") } returns null
        val scheduler = VehicleInspectionNoShowScheduler(service)

        When("the scheduler sweeps due no-shows") {
            scheduler.run()

            Then("it is a safe no-op") {
                verify(exactly = 1) { service.processNoShow("insp_stale") }
            }
        }
    }

    Given("no due bookings") {
        val service = mockk<VehicleInspectionService>()
        every { service.findDueNoShows() } returns emptyList()
        val scheduler = VehicleInspectionNoShowScheduler(service)

        When("the scheduler sweeps due no-shows") {
            scheduler.run()

            Then("it does nothing") {
                verify(exactly = 0) { service.processNoShow(any()) }
            }
        }
    }
})
