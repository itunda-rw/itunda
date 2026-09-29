package rw.itunda.rideshare

import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.BusBooking
import rw.itunda.core.domain.BusBookingStatus
import rw.itunda.core.domain.BusTrip
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.AccountType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.BusBookingRepository
import rw.itunda.core.repository.BusTripRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.util.UUID

class BusTripNotFoundException(message: String) : RuntimeException(message)
class InvalidBusTripException(message: String) : RuntimeException(message)
class BusNoAccountException(message: String) : RuntimeException(message)
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
 * account-to-account-at-purchase shape `MerchantService.collect` already establishes for
 * a real point-of-sale payment, not the settle-at-end escrow-free pattern its two
 * sibling services use.
 */
@Service
class BusService(
    private val busTripRepository: BusTripRepository,
    private val busBookingRepository: BusBookingRepository,
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService,
    private val rateLimiter: RateLimiter,
    private val fraudRuleEngine: FraudRuleEngine,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
) {
    private val log = LoggerFactory.getLogger(BusService::class.java)

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

    // Real bug found live (2026-08-02): unlike every other real "post a listing"
    // creation method in this codebase (MarketplaceService.createListing,
    // PropertyListingService.createListing -- both real 10/hour), this had no rate
    // limit at all, even though `rateLimiter` is already a dependency of this exact
    // class (`bookSeats` below uses it). Unbounded, this is a real spam-listing flood
    // vector on `searchTrips`.
    @Transactional
    fun postTrip(
        operatorUserId: String, origin: String, destination: String, departureTime: Instant,
        totalSeats: Int, farePerSeat: BigDecimal,
    ): BusTrip {
        rateLimiter.checkLimit("bus:post-trip:$operatorUserId", limit = 10, window = Duration.ofHours(1))
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
        val account = accountRepository.findByUserIdAndType(operatorUserId, AccountType.MAIN)
            ?: throw BusNoAccountException("No account found for this account")
        return busTripRepository.save(
            BusTrip(
                id = "bus_trip_${UUID.randomUUID()}", operatorUserId = operatorUserId, accountId = account.id,
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

        val riderAccount = accountRepository.findByUserIdAndType(riderUserId, AccountType.MAIN)
            ?: throw BusNoAccountException("No account found for this account")
        val operatorAccount = accountRepository.findById(trip.accountId)
            .orElseThrow { BusNoAccountException("Operator settlement account not found") }

        val totalFare = trip.farePerSeat.multiply(BigDecimal(seatCount)).setScale(2, RoundingMode.HALF_UP)
        val platformFee = totalFare.multiply(platformFeeRate).setScale(2, RoundingMode.HALF_UP)
        val netToOperator = totalFare.subtract(platformFee)

        val result = ledgerService.postLedgerTransaction(
            riderAccount.currency,
            listOf(
                LedgerLeg(riderAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, totalFare, "Bus ticket (${trip.origin} -> ${trip.destination})"),
                LedgerLeg(operatorAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, netToOperator, "Bus ticket sale"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, platformFee, "Bus ticket platform fee"),
            ),
        )
        // Real gap found live (2026-09-14, FraudRuleEngine-verify sweep): this class's
        // own doc comment says it bills "the same direct account-to-account-at-purchase
        // shape MerchantService.collect already establishes" -- but never copied
        // collect()'s real fraudRuleEngine.evaluate call. A direct, known-counterparty
        // (trip.operatorUserId) WALLET-to-WALLET payment with zero fraud coverage.
        fraudRuleEngine.evaluate(riderUserId, trip.operatorUserId, totalFare, result.transactionId)

        trip.availableSeats -= seatCount
        busTripRepository.save(trip)

        val saved = busBookingRepository.save(
            BusBooking(
                id = "bus_booking_${UUID.randomUUID()}", tripId = tripId, riderUserId = riderUserId, seatCount = seatCount,
                totalFare = totalFare, platformFee = platformFee, paymentTransactionId = result.transactionId,
            ),
        )

        // Real gap found live (2026-09-14, sibling-asymmetry sweep): this class had
        // zero notification wiring at all -- the same "someone else's asset earned
        // money while they weren't looking" gap DesignatedDriverService's own
        // completeTrip already closed earlier the same day. The operator has no way
        // to know a real rider booked seats on their trip otherwise.
        val title = "New bus ticket sale"
        val body = "$seatCount seat(s) sold for ${trip.origin} -> ${trip.destination}. You earned $netToOperator RWF."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = trip.operatorUserId, type = "BUS_TICKET_SOLD",
                title = title, body = body, isRead = false, createdAt = Instant.now(), dataJson = "{\"bookingId\":\"${saved.id}\"}",
            ),
        )
        sendBookingPushAfterCommit(trip.operatorUserId, title, body, saved.id)

        return saved
    }

    // Real gap found 2026-09-05 (concurrency audit): cancelBooking (this method's own
    // caller) mutates status/refundTransactionId after posting a real refund -- a
    // locked read here, not a plain findById, is what makes that safe under real
    // concurrent cancel attempts. See BusBookingRepository.findByIdForUpdate's own
    // doc comment.
    private fun getOwnedBooking(bookingId: String, riderUserId: String): BusBooking {
        val booking = busBookingRepository.findByIdForUpdate(bookingId).orElseThrow { BusBookingNotFoundException("Booking not found") }
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

        val riderAccount = accountRepository.findByUserIdAndType(riderUserId, AccountType.MAIN)
            ?: throw BusNoAccountException("No account found for this account")
        val operatorAccount = accountRepository.findById(trip.accountId)
            .orElseThrow { BusNoAccountException("Operator settlement account not found") }

        val result = ledgerService.postLedgerTransaction(
            riderAccount.currency,
            listOf(
                LedgerLeg(operatorAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, booking.totalFare.subtract(booking.platformFee), "Bus booking cancelled -- refund"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.DEBIT, booking.platformFee, "Bus ticket platform fee reversed"),
                LedgerLeg(riderAccount.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, booking.totalFare, "Bus booking cancelled -- refund"),
            ),
        )

        trip.availableSeats += booking.seatCount
        busTripRepository.save(trip)

        booking.status = BusBookingStatus.CANCELLED
        booking.refundTransactionId = result.transactionId
        val saved = busBookingRepository.save(booking)

        val title = "A bus booking was cancelled"
        val body = "A rider cancelled ${booking.seatCount} seat(s) on ${trip.origin} -> ${trip.destination}. The sale was refunded."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = trip.operatorUserId, type = "BUS_BOOKING_CANCELLED",
                title = title, body = body, isRead = false, createdAt = Instant.now(), dataJson = "{\"bookingId\":\"${booking.id}\"}",
            ),
        )
        sendBookingPushAfterCommit(trip.operatorUserId, title, body, booking.id)

        return saved
    }

    /** A sale/cancellation alert must never announce a booking whose enclosing
     * transaction rolled back. */
    private fun sendBookingPushAfterCommit(userId: String, title: String, body: String, bookingId: String) {
        val send = {
            try {
                pushNotificationService.sendToUser(userId, title, body, mapOf("bookingId" to bookingId))
            } catch (e: Exception) {
                log.warn("Could not send bus-booking push for booking {}", bookingId, e)
            }
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
    }

    fun getMyBookings(riderUserId: String, pageable: Pageable): Page<BusBooking> =
        busBookingRepository.findByRiderUserIdOrderByCreatedAtDesc(riderUserId, pageable)
}
