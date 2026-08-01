package rw.itunda.rideshare

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.BusBooking
import rw.itunda.core.domain.BusBookingStatus
import rw.itunda.core.domain.BusTrip
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.BusBookingRepository
import rw.itunda.core.repository.BusTripRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.util.UUID

class BusTripNotFoundException(message: String) : RuntimeException(message)
class InvalidBusTripException(message: String) : RuntimeException(message)
class BusNoWalletException(message: String) : RuntimeException(message)
class InsufficientSeatsException(message: String) : RuntimeException(message)
class BusBookingNotFoundException(message: String) : RuntimeException(message)
class BusBookingAlreadyCancelledException(message: String) : RuntimeException(message)
class BusTripAlreadyDepartedException(message: String) : RuntimeException(message)

/**
 * Real Kakao T 시외버스 (intercity bus booking) -- see `BusTrip.kt`/`BusBooking.kt`'s
 * own doc comments for the full sourced account and the real peer-to-peer v1
 * adaptation this makes (any itunda user self-registers as a coach operator and posts
 * a scheduled trip, no real transport-licensing gate).
 *
 * Unlike `ParkingService`/`BikeRentalService` (fare unknown until checkout, settled at
 * session end), a real bus ticket's fare IS known at booking time (seatCount *
 * farePerSeat) -- this bills the full fare immediately at booking, the same direct
 * wallet-to-wallet-at-purchase shape `MerchantService.collect` already establishes for
 * a real point-of-sale payment, not the settle-at-end escrow-free pattern its two
 * sibling services use.
 */
@Service
class BusService(
    private val busTripRepository: BusTripRepository,
    private val busBookingRepository: BusBookingRepository,
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val rateLimiter: RateLimiter,
) {
    companion object {
        // itunda's own honest fare bound -- no real published Kakao T Bus fare
        // schedule exists to source (real intercity fares vary by route/distance/
        // operator), same reasoning ParkingService's own hourly-rate bound already
        // gives for why an owner sets their own rate within a sane bound.
        val minFarePerSeat: BigDecimal = BigDecimal("500")
        val maxFarePerSeat: BigDecimal = BigDecimal("50000")
        private val platformFeeRate = BigDecimal("0.05")
        const val MAX_SEATS_PER_BOOKING = 10
    }

    @Transactional
    fun postTrip(
        operatorUserId: String, origin: String, destination: String, departureTime: Instant,
        totalSeats: Int, farePerSeat: BigDecimal,
    ): BusTrip {
        val trimmedOrigin = origin.trim().ifEmpty { throw InvalidBusTripException("Origin is required") }.take(200)
        val trimmedDestination = destination.trim().ifEmpty { throw InvalidBusTripException("Destination is required") }.take(200)
        if (!departureTime.isAfter(Instant.now())) {
            throw InvalidBusTripException("Departure time must be in the future")
        }
        if (totalSeats <= 0) {
            throw InvalidBusTripException("Total seats must be greater than zero")
        }
        if (farePerSeat < minFarePerSeat || farePerSeat > maxFarePerSeat) {
            throw InvalidBusTripException("Fare per seat must be between $minFarePerSeat and $maxFarePerSeat")
        }
        val wallet = walletRepository.findByUserIdAndType(operatorUserId, WalletType.MAIN)
            ?: throw BusNoWalletException("No wallet found for this account")
        return busTripRepository.save(
            BusTrip(
                id = "bus_trip_${UUID.randomUUID()}", operatorUserId = operatorUserId, walletId = wallet.id,
                origin = trimmedOrigin, destination = trimmedDestination, departureTime = departureTime,
                totalSeats = totalSeats, availableSeats = totalSeats, farePerSeat = farePerSeat,
            ),
        )
    }

    fun getMyTrips(operatorUserId: String): List<BusTrip> = busTripRepository.findByOperatorUserId(operatorUserId)

    fun getTripBookings(operatorUserId: String, tripId: String): List<BusBooking> {
        val trip = busTripRepository.findById(tripId).orElseThrow { BusTripNotFoundException("Trip not found") }
        if (trip.operatorUserId != operatorUserId) throw BusTripNotFoundException("Trip not found")
        return busBookingRepository.findByTripIdOrderByCreatedAtDesc(tripId)
    }

    // Real search -- future departures only, simple substring match on origin/
    // destination (no fuzzy geocoding), same honest simplicity ParkingService's own
    // nearby-browse establishes for this backend's smaller-scale peer-to-peer features.
    fun searchTrips(origin: String?, destination: String?): List<BusTrip> {
        val now = Instant.now()
        return busTripRepository.findAll()
            .filter { it.departureTime.isAfter(now) && it.availableSeats > 0 }
            .filter { origin.isNullOrBlank() || it.origin.contains(origin.trim(), ignoreCase = true) }
            .filter { destination.isNullOrBlank() || it.destination.contains(destination.trim(), ignoreCase = true) }
            .sortedBy { it.departureTime }
    }

    /** Real immediate settlement -- the fare is fully known at booking time, so unlike
     * `ParkingService`/`BikeRentalService` this posts one real ledger transaction right
     * here, not at some later "session end". `@Version`-backed optimistic locking on
     * `BusTrip.availableSeats` means a real concurrent over-book on the last seats
     * conflicts and this whole transaction rolls back rather than silently
     * double-selling a seat. */
    @Transactional
    fun bookSeats(riderUserId: String, tripId: String, seatCount: Int): BusBooking {
        rateLimiter.checkLimit("bus:book:$riderUserId", limit = 20, window = Duration.ofHours(1))

        if (seatCount <= 0 || seatCount > MAX_SEATS_PER_BOOKING) {
            throw InvalidBusTripException("Seat count must be between 1 and $MAX_SEATS_PER_BOOKING")
        }
        val trip = busTripRepository.findById(tripId).orElseThrow { BusTripNotFoundException("Trip not found") }
        if (!trip.departureTime.isAfter(Instant.now())) {
            throw BusTripAlreadyDepartedException("This trip has already departed")
        }
        if (trip.availableSeats < seatCount) {
            throw InsufficientSeatsException("Only ${trip.availableSeats} seat(s) remaining on this trip")
        }

        val riderWallet = walletRepository.findByUserIdAndType(riderUserId, WalletType.MAIN)
            ?: throw BusNoWalletException("No wallet found for this account")
        val operatorWallet = walletRepository.findById(trip.walletId)
            .orElseThrow { BusNoWalletException("Operator settlement wallet not found") }

        val totalFare = trip.farePerSeat.multiply(BigDecimal(seatCount)).setScale(2, RoundingMode.HALF_UP)
        val platformFee = totalFare.multiply(platformFeeRate).setScale(2, RoundingMode.HALF_UP)
        val netToOperator = totalFare.subtract(platformFee)

        val result = ledgerService.postLedgerTransaction(
            riderWallet.currency,
            listOf(
                LedgerLeg(riderWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, totalFare, "Bus ticket (${trip.origin} -> ${trip.destination})"),
                LedgerLeg(operatorWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, netToOperator, "Bus ticket sale"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, platformFee, "Bus ticket platform fee"),
            ),
        )

        trip.availableSeats -= seatCount
        busTripRepository.save(trip)

        return busBookingRepository.save(
            BusBooking(
                id = "bus_booking_${UUID.randomUUID()}", tripId = tripId, riderUserId = riderUserId, seatCount = seatCount,
                totalFare = totalFare, platformFee = platformFee, paymentTransactionId = result.transactionId,
            ),
        )
    }

    private fun getOwnedBooking(bookingId: String, riderUserId: String): BusBooking {
        val booking = busBookingRepository.findById(bookingId).orElseThrow { BusBookingNotFoundException("Booking not found") }
        if (booking.riderUserId != riderUserId) throw BusBookingNotFoundException("Booking not found")
        return booking
    }

    /** Real full refund, seats restored -- only before the trip's real departure time,
     * matching every other "an explicit cancel always refunds" rule this backend's
     * booking-shaped features already establish (`RideTripService`,
     * `VehicleInspectionService`). A reverse ledger entry, not a second real payment
     * flow -- the simplest, most honest way to undo an already-settled purchase. */
    @Transactional
    fun cancelBooking(riderUserId: String, bookingId: String): BusBooking {
        val booking = getOwnedBooking(bookingId, riderUserId)
        if (booking.status != BusBookingStatus.BOOKED) {
            throw BusBookingAlreadyCancelledException("This booking has already been cancelled")
        }
        val trip = busTripRepository.findById(booking.tripId).orElseThrow { BusTripNotFoundException("Trip not found") }
        if (!trip.departureTime.isAfter(Instant.now())) {
            throw BusTripAlreadyDepartedException("Cannot cancel a booking after the trip has departed")
        }

        val riderWallet = walletRepository.findByUserIdAndType(riderUserId, WalletType.MAIN)
            ?: throw BusNoWalletException("No wallet found for this account")
        val operatorWallet = walletRepository.findById(trip.walletId)
            .orElseThrow { BusNoWalletException("Operator settlement wallet not found") }

        val result = ledgerService.postLedgerTransaction(
            riderWallet.currency,
            listOf(
                LedgerLeg(operatorWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, booking.totalFare.subtract(booking.platformFee), "Bus booking cancelled -- refund"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.DEBIT, booking.platformFee, "Bus ticket platform fee reversed"),
                LedgerLeg(riderWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, booking.totalFare, "Bus booking cancelled -- refund"),
            ),
        )

        trip.availableSeats += booking.seatCount
        busTripRepository.save(trip)

        booking.status = BusBookingStatus.CANCELLED
        booking.refundTransactionId = result.transactionId
        return busBookingRepository.save(booking)
    }

    fun getMyBookings(riderUserId: String, pageable: Pageable): Page<BusBooking> =
        busBookingRepository.findByRiderUserIdOrderByCreatedAtDesc(riderUserId, pageable)
}
