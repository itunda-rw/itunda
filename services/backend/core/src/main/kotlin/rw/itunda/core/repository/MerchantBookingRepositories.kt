package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
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
}
