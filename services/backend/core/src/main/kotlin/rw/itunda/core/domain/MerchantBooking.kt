package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * A merchant's real recurring weekly availability -- e.g. "Tuesdays 09:00-17:00." Real
 * open slots (see MerchantBookingService.getAvailableSlots) are generated from these
 * windows minus any already-REQUESTED/CONFIRMED booking on the target date -- itunda has
 * no calendar-sync integration to pull a merchant's real external schedule from, so this
 * is the honest v1: a merchant-declared recurring schedule, not a synced one.
 */
@Entity
@Table(name = "merchant_availability_windows")
class MerchantAvailabilityWindow(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "merchant_id", nullable = false, length = 64)
    val merchantId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 16)
    val dayOfWeek: DayOfWeek,

    @Column(name = "start_time", nullable = false)
    val startTime: LocalTime,

    @Column(name = "end_time", nullable = false)
    val endTime: LocalTime,
) {
    protected constructor() : this(id = "", merchantId = "", dayOfWeek = DayOfWeek.MONDAY, startTime = LocalTime.MIN, endTime = LocalTime.MIN)
}

// Forward-driven, restaurant/owner-analogous to EatsOrderStatus/DineInOrderStatus: a
// customer REQUESTs a slot, the merchant CONFIRMs or DECLINEs it (real 당근비즈프로필-style
// request-then-confirm, not an auto-accept), either side can CANCEL a REQUESTED/CONFIRMED
// booking, and the merchant marks a CONFIRMED booking COMPLETED once served.
// NO_SHOW added 2026-07-25 -- see BookingDeposit.kt's own doc comment. A CONFIRMED
// booking whose scheduled time passed with no explicit action from either side
// (BookingNoShowScheduler) is genuinely distinct from a CANCELLED one (an explicit,
// timely choice by either side, always refundable) or COMPLETED (the service was
// rendered) -- collapsing it into either would misrepresent what actually happened.
enum class MerchantBookingStatus { REQUESTED, CONFIRMED, DECLINED, CANCELLED, COMPLETED, NO_SHOW }

/**
 * A real customer appointment against a bookable [MerchantProduct] (one with
 * `durationMinutes` set) -- closes the "local business profile + real booking" gap
 * independently converged on by Naver Smart Place, Kakao Hair Shop, and Karrot's
 * Business Profile research (docs/DESIGN_REFERENCES.md). No payment/deposit at booking
 * time -- real local service businesses (salons, tutors, repair) overwhelmingly settle
 * in person, and no research source described a mandatory booking deposit, so this
 * doesn't invent one.
 *
 * `serviceName`/`durationMinutes` are snapshotted at booking time (same
 * snapshot-at-purchase-time convention as `EatsOrderItem.productName`/`unitPrice`), so a
 * later service-catalog edit never retroactively alters an already-made appointment.
 */
@Entity
@Table(name = "merchant_bookings")
class MerchantBooking(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "merchant_id", nullable = false, length = 64)
    val merchantId: String,

    @Column(name = "customer_id", nullable = false, length = 64)
    val customerId: String,

    @Column(name = "service_id", nullable = false, length = 64)
    val serviceId: String,

    @Column(name = "service_name", nullable = false)
    val serviceName: String,

    @Column(name = "booking_date", nullable = false)
    val bookingDate: LocalDate,

    @Column(name = "start_time", nullable = false)
    val startTime: LocalTime,

    @Column(name = "end_time", nullable = false)
    val endTime: LocalTime,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: MerchantBookingStatus = MerchantBookingStatus.REQUESTED,

    @Column(length = 500)
    val notes: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(
        id = "", merchantId = "", customerId = "", serviceId = "", serviceName = "",
        bookingDate = LocalDate.MIN, startTime = LocalTime.MIN, endTime = LocalTime.MIN,
    )
}
