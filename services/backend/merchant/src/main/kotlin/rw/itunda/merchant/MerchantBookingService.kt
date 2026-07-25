package rw.itunda.merchant

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.MerchantAvailabilityWindow
import rw.itunda.core.domain.MerchantBooking
import rw.itunda.core.domain.MerchantBookingStatus
import rw.itunda.core.domain.Notification
import rw.itunda.core.repository.MerchantAvailabilityWindowRepository
import rw.itunda.core.repository.MerchantBookingRepository
import rw.itunda.core.repository.MerchantProductRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

class InvalidAvailabilityWindowException(message: String) : RuntimeException(message)
class ServiceNotBookableException(message: String) : RuntimeException(message)
class InvalidBookingSlotException(message: String) : RuntimeException(message)
class SlotNoLongerAvailableException(message: String) : RuntimeException(message)
class MerchantBookingNotFoundException(message: String) : RuntimeException(message)
class InvalidBookingStatusTransitionException(message: String) : RuntimeException(message)

data class AvailabilityWindowRequest(val dayOfWeek: DayOfWeek, val startTime: LocalTime, val endTime: LocalTime)
data class BookingSlot(val startTime: LocalTime, val endTime: LocalTime)

/**
 * Real local-business appointment booking on top of the existing `Merchant`/
 * `MerchantProduct` catalog -- see `MerchantBooking.kt`'s own doc comment for the full
 * account (a bookable service IS a `MerchantProduct` with `durationMinutes` set, same
 * "no second catalog system" discipline `EatsOrderService`/`DineInOrderService` already
 * established for restaurants).
 *
 * Slots are generated fresh from the merchant's declared weekly [MerchantAvailabilityWindow]s
 * minus any real REQUESTED/CONFIRMED booking already on that date -- never persisted as
 * their own rows, so a later availability-window edit or duration change is instantly
 * reflected with no stale-slot cleanup needed.
 */
@Service
class MerchantBookingService(
    private val merchantRepository: MerchantRepository,
    private val merchantProductRepository: MerchantProductRepository,
    private val availabilityWindowRepository: MerchantAvailabilityWindowRepository,
    private val merchantBookingRepository: MerchantBookingRepository,
    private val notificationRepository: NotificationRepository,
) {
    private fun getMyMerchant(ownerUserId: String) =
        merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw MerchantNotFoundException("This account is not registered as a merchant")

    private fun today(): LocalDate = LocalDate.now(ZoneOffset.UTC)
    private fun now(): LocalTime = LocalTime.now(ZoneOffset.UTC)

    @Transactional
    fun setAvailability(ownerUserId: String, windows: List<AvailabilityWindowRequest>): List<MerchantAvailabilityWindow> {
        val merchant = getMyMerchant(ownerUserId)
        if (windows.size > 50) {
            throw InvalidAvailabilityWindowException("Too many availability windows -- 50 is the real limit")
        }
        windows.forEach {
            if (!it.startTime.isBefore(it.endTime)) {
                throw InvalidAvailabilityWindowException("An availability window's start time must be before its end time")
            }
        }
        availabilityWindowRepository.deleteByMerchantId(merchant.id)
        val saved = windows.map {
            MerchantAvailabilityWindow(
                id = "merchant_availability_${UUID.randomUUID()}", merchantId = merchant.id,
                dayOfWeek = it.dayOfWeek, startTime = it.startTime, endTime = it.endTime,
            )
        }
        return availabilityWindowRepository.saveAll(saved)
    }

    fun getMyAvailability(ownerUserId: String): List<MerchantAvailabilityWindow> {
        val merchant = getMyMerchant(ownerUserId)
        return availabilityWindowRepository.findByMerchantIdOrderByDayOfWeekAscStartTimeAsc(merchant.id)
    }

    fun getAvailability(merchantId: String): List<MerchantAvailabilityWindow> =
        availabilityWindowRepository.findByMerchantIdOrderByDayOfWeekAscStartTimeAsc(merchantId)

    /** Real free-slot generation for a public/customer date picker -- see this class's
     * own doc comment for the "generated fresh, never persisted" design. */
    fun getAvailableSlots(merchantId: String, serviceId: String, date: LocalDate): List<BookingSlot> {
        if (date.isBefore(today())) {
            throw InvalidBookingSlotException("Cannot book a date in the past")
        }
        val service = merchantProductRepository.findById(serviceId)
            .orElseThrow { MerchantProductNotFoundException("Service not found") }
        if (service.merchantId != merchantId || !service.active) {
            throw MerchantProductNotFoundException("Service not found")
        }
        val durationMinutes = service.durationMinutes
            ?: throw ServiceNotBookableException("${service.name} is not a bookable service")

        val windows = availabilityWindowRepository.findByMerchantIdAndDayOfWeek(merchantId, date.dayOfWeek)
        if (windows.isEmpty()) return emptyList()

        val busy = merchantBookingRepository.findByMerchantIdAndBookingDateAndStatusIn(
            merchantId, date, listOf(MerchantBookingStatus.REQUESTED, MerchantBookingStatus.CONFIRMED),
        )
        val isToday = date.isEqual(today())
        val nowTime = now()

        return windows.flatMap { window ->
            generateSequence(window.startTime) { it.plusMinutes(durationMinutes.toLong()) }
                .takeWhile { !it.plusMinutes(durationMinutes.toLong()).isAfter(window.endTime) }
                .map { slotStart -> BookingSlot(slotStart, slotStart.plusMinutes(durationMinutes.toLong())) }
                .filter { slot -> !isToday || slot.startTime.isAfter(nowTime) }
                .filter { slot -> busy.none { it.startTime.isBefore(slot.endTime) && slot.startTime.isBefore(it.endTime) } }
        }.sortedBy { it.startTime }
    }

    @Transactional
    fun book(customerId: String, merchantId: String, serviceId: String, date: LocalDate, startTime: LocalTime, notes: String? = null): MerchantBooking {
        val merchant = merchantRepository.findById(merchantId)
            .orElseThrow { MerchantNotFoundException("Merchant not found") }
        if (merchant.ownerUserId == customerId) {
            throw InvalidBookingSlotException("Cannot book your own business")
        }
        // Real, honest re-check at write time -- the slot list a customer saw may be
        // stale by the time they submit, and this backend has no DB-level partial unique
        // index to fall back on (MySQL can't express "unique only among non-terminal
        // rows" -- see MerchantBookingRepository's own doc comment), so this
        // application-level re-validation is the real, if imperfect, safeguard.
        val available = getAvailableSlots(merchantId, serviceId, date)
        val slot = available.find { it.startTime == startTime }
            ?: throw SlotNoLongerAvailableException("This time slot is no longer available")
        val service = merchantProductRepository.findById(serviceId).orElseThrow { MerchantProductNotFoundException("Service not found") }
        val trimmedNotes = notes?.trim()?.ifBlank { null }?.take(500)

        val booking = merchantBookingRepository.save(
            MerchantBooking(
                id = "merchant_booking_${UUID.randomUUID()}", merchantId = merchantId, customerId = customerId,
                serviceId = serviceId, serviceName = service.name, bookingDate = date,
                startTime = slot.startTime, endTime = slot.endTime, notes = trimmedNotes,
            ),
        )
        notify(merchant.ownerUserId, booking, "New booking request", "A customer requested ${service.name} on $date at ${slot.startTime}.")
        return booking
    }

    @Transactional
    fun respond(ownerUserId: String, bookingId: String, confirm: Boolean): MerchantBooking {
        val merchant = getMyMerchant(ownerUserId)
        val booking = getOwnedBooking(bookingId, merchant.id)
        if (booking.status != MerchantBookingStatus.REQUESTED) {
            throw InvalidBookingStatusTransitionException("Only a REQUESTED booking can be confirmed or declined")
        }
        booking.status = if (confirm) MerchantBookingStatus.CONFIRMED else MerchantBookingStatus.DECLINED
        booking.updatedAt = Instant.now()
        val saved = merchantBookingRepository.save(booking)
        val title = if (confirm) "Booking confirmed" else "Booking declined"
        notify(booking.customerId, saved, title, "${merchant.businessName}: ${booking.serviceName} on ${booking.bookingDate} at ${booking.startTime} was ${saved.status.name.lowercase()}.")
        return saved
    }

    @Transactional
    fun markCompleted(ownerUserId: String, bookingId: String): MerchantBooking {
        val merchant = getMyMerchant(ownerUserId)
        val booking = getOwnedBooking(bookingId, merchant.id)
        if (booking.status != MerchantBookingStatus.CONFIRMED) {
            throw InvalidBookingStatusTransitionException("Only a CONFIRMED booking can be marked completed")
        }
        booking.status = MerchantBookingStatus.COMPLETED
        booking.updatedAt = Instant.now()
        return merchantBookingRepository.save(booking)
    }

    @Transactional
    fun cancel(requesterId: String, bookingId: String): MerchantBooking {
        val booking = merchantBookingRepository.findById(bookingId)
            .orElseThrow { MerchantBookingNotFoundException("Booking not found") }
        val merchant = merchantRepository.findById(booking.merchantId).orElse(null)
        val isCustomer = booking.customerId == requesterId
        val isMerchant = merchant?.ownerUserId == requesterId
        if (!isCustomer && !isMerchant) {
            throw MerchantBookingNotFoundException("Booking not found")
        }
        if (booking.status != MerchantBookingStatus.REQUESTED && booking.status != MerchantBookingStatus.CONFIRMED) {
            throw InvalidBookingStatusTransitionException("Only a REQUESTED or CONFIRMED booking can be cancelled -- this one is already ${booking.status}")
        }
        booking.status = MerchantBookingStatus.CANCELLED
        booking.updatedAt = Instant.now()
        val saved = merchantBookingRepository.save(booking)
        // Only notify the OTHER party -- same "don't notify someone about their own
        // action" discipline every other Notification call site in this codebase uses.
        if (isCustomer && merchant != null) {
            notify(merchant.ownerUserId, saved, "Booking cancelled", "A customer cancelled their ${booking.serviceName} booking on ${booking.bookingDate} at ${booking.startTime}.")
        } else if (isMerchant) {
            notify(booking.customerId, saved, "Booking cancelled", "${merchant?.businessName ?: "The business"} cancelled your ${booking.serviceName} booking on ${booking.bookingDate} at ${booking.startTime}.")
        }
        return saved
    }

    fun getMyBookings(customerId: String, pageable: Pageable): Page<MerchantBooking> =
        merchantBookingRepository.findByCustomerIdOrderByBookingDateDescStartTimeDesc(customerId, pageable)

    fun getMerchantBookings(ownerUserId: String, pageable: Pageable): Page<MerchantBooking> {
        val merchant = getMyMerchant(ownerUserId)
        return merchantBookingRepository.findByMerchantIdOrderByBookingDateDescStartTimeDesc(merchant.id, pageable)
    }

    private fun getOwnedBooking(bookingId: String, merchantId: String): MerchantBooking {
        val booking = merchantBookingRepository.findById(bookingId)
            .orElseThrow { MerchantBookingNotFoundException("Booking not found") }
        if (booking.merchantId != merchantId) {
            throw MerchantBookingNotFoundException("Booking not found")
        }
        return booking
    }

    private fun notify(userId: String, booking: MerchantBooking, title: String, body: String) {
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = userId, type = "MERCHANT_BOOKING_UPDATE",
                title = title, body = body, isRead = false, createdAt = Instant.now(),
                dataJson = "{\"bookingId\":\"${booking.id}\"}",
            ),
        )
    }
}
