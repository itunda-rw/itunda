package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.BookingDeposit
import rw.itunda.core.domain.MerchantAvailabilityWindow
import rw.itunda.core.domain.MerchantBooking
import rw.itunda.core.domain.MerchantBookingStatus
import java.time.DayOfWeek
import java.time.LocalDate

interface MerchantAvailabilityWindowRepository : JpaRepository<MerchantAvailabilityWindow, String> {
    fun findByMerchantIdOrderByDayOfWeekAscStartTimeAsc(merchantId: String): List<MerchantAvailabilityWindow>
    fun findByMerchantIdAndDayOfWeek(merchantId: String, dayOfWeek: DayOfWeek): List<MerchantAvailabilityWindow>
    fun deleteByMerchantId(merchantId: String)
}

interface MerchantBookingRepository : JpaRepository<MerchantBooking, String> {
    fun findByCustomerIdOrderByBookingDateDescStartTimeDesc(customerId: String, pageable: Pageable): Page<MerchantBooking>
    fun findByMerchantIdOrderByBookingDateDescStartTimeDesc(merchantId: String, pageable: Pageable): Page<MerchantBooking>

    // Real overlap check for slot generation/booking-time re-validation (see
    // MerchantBookingService's own doc comment) -- every non-terminal booking on the
    // target date for this merchant, checked in-app rather than via a DB constraint since
    // MySQL has no real partial/filtered unique index to express "unique only among
    // REQUESTED/CONFIRMED rows."
    fun findByMerchantIdAndBookingDateAndStatusIn(
        merchantId: String,
        bookingDate: LocalDate,
        statuses: List<MerchantBookingStatus>,
    ): List<MerchantBooking>

    // Real no-show detection (BookingNoShowScheduler, 2026-07-25) -- a coarse DB-level
    // filter (every still-CONFIRMED booking on or before today); the scheduler does the
    // exact date+endTime-vs-now check in application code (same "coarse DB filter, exact
    // check in the service" pattern MerchantBookingService.getAvailableSlots already uses
    // for its own "isToday" filtering), since MySQL has no direct way to compare a
    // LocalDate+LocalTime pair against "now" in one column expression here.
    fun findByStatusAndBookingDateLessThanEqual(status: MerchantBookingStatus, bookingDate: LocalDate): List<MerchantBooking>
}

interface BookingDepositRepository : JpaRepository<BookingDeposit, String> {
    fun findByBookingId(bookingId: String): BookingDeposit?
}
