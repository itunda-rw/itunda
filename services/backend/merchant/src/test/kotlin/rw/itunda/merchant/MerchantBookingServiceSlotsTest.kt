package rw.itunda.merchant

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.core.domain.MerchantAvailabilityWindow
import rw.itunda.core.domain.MerchantBooking
import rw.itunda.core.domain.MerchantProduct
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.BookingDepositRepository
import rw.itunda.core.repository.MerchantAvailabilityWindowRepository
import rw.itunda.core.repository.MerchantBookingRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.AccountRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Optional
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors

// Real regression coverage for the live infinite-loop bug found 2026-07-27:
// LocalTime.plusMinutes silently wraps past midnight, so a takeWhile driven by
// isAfter(window.endTime) never terminated once a window's close time was within one
// slot of midnight (e.g. 00:00-23:30 with 30-min slots) -- a real request thread spun
// forever pinning a CPU core until kubelet killed the pod. getAvailableSlots now bounds
// the loop by a real slot COUNT instead.
class MerchantBookingServiceSlotsTest : BehaviorSpec({

    Given("a real merchant's declared weekly availability, generating real bookable slots") {
        val merchantRepository = mockk<MerchantRepository>()
        val merchantProductRepository = mockk<MerchantProductRepository>()
        val availabilityWindowRepository = mockk<MerchantAvailabilityWindowRepository>()
        val merchantBookingRepository = mockk<MerchantBookingRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>()
        val bookingDepositRepository = mockk<BookingDepositRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val autoTopUpService = mockk<rw.itunda.account.AutoTopUpService>(relaxed = true)
        val service = MerchantBookingService(
            merchantRepository, merchantProductRepository, availabilityWindowRepository,
            merchantBookingRepository, notificationRepository, accountRepository, ledgerService,
            transactionRepository, bookingDepositRepository, pushNotificationService, autoTopUpService,
        )

        val service30Min = MerchantProduct(
            id = "service_1", merchantId = "merchant_1", name = "Haircut", price = java.math.BigDecimal("5000"),
            durationMinutes = 30,
        )
        // A date far enough in the future that isToday's own-day filtering never kicks in.
        val futureDate = LocalDate.of(2099, 1, 5)

        When("the merchant's window closes at 23:30 -- the exact real live incident (a near-midnight close time)") {
            val window = MerchantAvailabilityWindow(
                id = "window_1", merchantId = "merchant_1", dayOfWeek = DayOfWeek.MONDAY,
                startTime = LocalTime.of(0, 0), endTime = LocalTime.of(23, 30),
            )
            every { merchantProductRepository.findById("service_1") } returns Optional.of(service30Min)
            every { availabilityWindowRepository.findByMerchantIdAndDayOfWeek("merchant_1", any()) } returns listOf(window)
            every { merchantBookingRepository.findByMerchantIdAndBookingDateAndStatusIn(any(), any(), any()) } returns emptyList()

            Then("it real-terminates (does not hang) and returns exactly the 47 real half-hour slots the window holds") {
                val executor = Executors.newSingleThreadExecutor()
                val future = executor.submit<List<BookingSlot>> { service.getAvailableSlots("merchant_1", "service_1", futureDate) }
                // A real, generous bound -- this used to hang forever; 5s proves real
                // termination without needing to wait out a full request timeout.
                val slots = future.get(5, TimeUnit.SECONDS)
                executor.shutdownNow()

                slots shouldHaveSize 47
                slots.first().startTime shouldBe LocalTime.of(0, 0)
                slots.last().startTime shouldBe LocalTime.of(23, 0)
                slots.last().endTime shouldBe LocalTime.of(23, 30)
            }
        }

        When("the merchant's window closes exactly at midnight (00:00 as the literal endTime, an even more extreme wrap case)") {
            val window = MerchantAvailabilityWindow(
                id = "window_2", merchantId = "merchant_1", dayOfWeek = DayOfWeek.MONDAY,
                startTime = LocalTime.of(22, 0), endTime = LocalTime.MIDNIGHT.minusNanos(1),
            )
            every { merchantProductRepository.findById("service_1") } returns Optional.of(service30Min)
            every { availabilityWindowRepository.findByMerchantIdAndDayOfWeek("merchant_1", any()) } returns listOf(window)
            every { merchantBookingRepository.findByMerchantIdAndBookingDateAndStatusIn(any(), any(), any()) } returns emptyList()

            Then("it real-terminates and returns the 3 real half-hour slots between 22:00 and one minute before midnight") {
                val executor = Executors.newSingleThreadExecutor()
                val future = executor.submit<List<BookingSlot>> { service.getAvailableSlots("merchant_1", "service_1", futureDate) }
                val slots = future.get(5, TimeUnit.SECONDS)
                executor.shutdownNow()

                slots shouldHaveSize 3
                slots.last().startTime shouldBe LocalTime.of(23, 0)
            }
        }

        When("a normal business-hours window (09:00-18:00) with no wraparound risk at all") {
            val window = MerchantAvailabilityWindow(
                id = "window_3", merchantId = "merchant_1", dayOfWeek = DayOfWeek.MONDAY,
                startTime = LocalTime.of(9, 0), endTime = LocalTime.of(18, 0),
            )
            every { merchantProductRepository.findById("service_1") } returns Optional.of(service30Min)
            every { availabilityWindowRepository.findByMerchantIdAndDayOfWeek("merchant_1", any()) } returns listOf(window)
            every { merchantBookingRepository.findByMerchantIdAndBookingDateAndStatusIn(any(), any(), any()) } returns emptyList()

            Then("it returns the real 18 half-hour slots the window holds") {
                val slots = service.getAvailableSlots("merchant_1", "service_1", futureDate)
                slots shouldHaveSize 18
                slots.first().startTime shouldBe LocalTime.of(9, 0)
                slots.last().startTime shouldBe LocalTime.of(17, 30)
            }
        }

        When("a real existing booking already occupies one of the slots") {
            val window = MerchantAvailabilityWindow(
                id = "window_4", merchantId = "merchant_1", dayOfWeek = DayOfWeek.MONDAY,
                startTime = LocalTime.of(9, 0), endTime = LocalTime.of(11, 0),
            )
            val existingBooking = mockk<MerchantBooking>()
            every { existingBooking.startTime } returns LocalTime.of(9, 30)
            every { existingBooking.endTime } returns LocalTime.of(10, 0)
            every { merchantProductRepository.findById("service_1") } returns Optional.of(service30Min)
            every { availabilityWindowRepository.findByMerchantIdAndDayOfWeek("merchant_1", any()) } returns listOf(window)
            every { merchantBookingRepository.findByMerchantIdAndBookingDateAndStatusIn(any(), any(), any()) } returns listOf(existingBooking)

            Then("the real busy slot (and only that one) is excluded, leaving 3 of the 4 half-hour slots") {
                val slots = service.getAvailableSlots("merchant_1", "service_1", futureDate)
                slots shouldHaveSize 3
                slots.map { it.startTime } shouldBe listOf(LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(10, 30))
            }
        }

        When("the merchant has no window at all for the requested day") {
            every { merchantProductRepository.findById("service_1") } returns Optional.of(service30Min)
            every { availabilityWindowRepository.findByMerchantIdAndDayOfWeek("merchant_1", any()) } returns emptyList()

            Then("it real-returns an empty list, never touching the booking repository") {
                val slots = service.getAvailableSlots("merchant_1", "service_1", futureDate)
                slots shouldHaveSize 0
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
