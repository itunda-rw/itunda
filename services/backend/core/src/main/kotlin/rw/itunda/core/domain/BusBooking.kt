package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

enum class BusBookingStatus { BOOKED, CANCELLED }

/** Real Kakao T 시외버스 seat booking -- see `BusTrip.kt`'s own doc comment for the
 * full sourced account. Paid in full at booking time (`paymentTransactionId`),
 * refunded in full on a real pre-departure cancellation (`refundTransactionId`). */
@Entity
@Table(name = "bus_bookings")
class BusBooking(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "trip_id", nullable = false, length = 64)
    val tripId: String,

    @Column(name = "rider_user_id", nullable = false, length = 64)
    val riderUserId: String,

    @Column(name = "seat_count", nullable = false)
    val seatCount: Int,

    @Column(name = "total_fare", nullable = false, precision = 18, scale = 2)
    val totalFare: BigDecimal,

    @Column(name = "platform_fee", nullable = false, precision = 18, scale = 2)
    val platformFee: BigDecimal,

    @Column(name = "payment_transaction_id", nullable = false, length = 64)
    val paymentTransactionId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: BusBookingStatus = BusBookingStatus.BOOKED,

    @Column(name = "refund_transaction_id", length = 64)
    var refundTransactionId: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(
        id = "", tripId = "", riderUserId = "", seatCount = 0, totalFare = BigDecimal.ZERO,
        platformFee = BigDecimal.ZERO, paymentTransactionId = "",
    )
}
