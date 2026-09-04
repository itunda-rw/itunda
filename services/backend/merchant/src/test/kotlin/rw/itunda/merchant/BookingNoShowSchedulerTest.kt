package rw.itunda.merchant

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.MerchantBooking
import java.time.LocalDate
import java.time.LocalTime

/**
 * Same loop-resilience contract every other per-item scheduler in this codebase
 * proves -- see BookingNoShowScheduler's own doc comment.
 */
class BookingNoShowSchedulerTest : BehaviorSpec({
    fun booking(id: String) = MerchantBooking(
        id = id,
        merchantId = "merchant_1",
        customerId = "customer_1",
        serviceId = "service_1",
        serviceName = "Haircut",
        bookingDate = LocalDate.now().minusDays(1),
        startTime = LocalTime.of(10, 0),
        endTime = LocalTime.of(11, 0),
    )

    Given("3 past-due confirmed bookings, where the middle one's no-show processing fails") {
        val service = mockk<MerchantBookingService>()
        val bookings = listOf(booking("bk_1"), booking("bk_2"), booking("bk_3"))
        every { service.getDueNoShows() } returns bookings
        every { service.processNoShow("bk_1") } returns bookings[0]
        every { service.processNoShow("bk_2") } throws IllegalStateException("deposit account not found")
        every { service.processNoShow("bk_3") } returns bookings[2]
        val scheduler = BookingNoShowScheduler(service)

        When("the scheduler sweeps due no-shows") {
            scheduler.run()

            Then("all 3 bookings are still attempted, not just the ones before the failure") {
                verify(exactly = 1) { service.processNoShow("bk_1") }
                verify(exactly = 1) { service.processNoShow("bk_2") }
                verify(exactly = 1) { service.processNoShow("bk_3") }
            }
        }
    }

    Given("no past-due confirmed bookings") {
        val service = mockk<MerchantBookingService>()
        every { service.getDueNoShows() } returns emptyList()
        val scheduler = BookingNoShowScheduler(service)

        When("the scheduler sweeps due no-shows") {
            scheduler.run()

            Then("it does nothing") {
                verify(exactly = 0) { service.processNoShow(any()) }
            }
        }
    }
})
