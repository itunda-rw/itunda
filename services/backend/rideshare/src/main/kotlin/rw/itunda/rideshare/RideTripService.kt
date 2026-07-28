package rw.itunda.rideshare

import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.RideDriver
import rw.itunda.core.domain.RideTrip
import rw.itunda.core.domain.RideTripStatus
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.WalletType
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.RideDriverRepository
import rw.itunda.core.repository.RideTripRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.util.UUID

class InvalidRideLocationException(message: String) : RuntimeException(message)
class RideSelfTripException(message: String) : RuntimeException(message)
class RideTripNotFoundException(message: String) : RuntimeException(message)
class RideTripAlreadyClaimedException(message: String) : RuntimeException(message)
class RideDriverAlreadyOnTripException(message: String) : RuntimeException(message)
class RideDriverNotAvailableException(message: String) : RuntimeException(message)
class InvalidRideTripStatusTransitionException(message: String) : RuntimeException(message)
class RideNoActiveOfferException(message: String) : RuntimeException(message)

/**
 * Real Kakao T-style ride-hailing (2026-07-26) -- closes `docs/DESIGN_REFERENCES.md`'s
 * own remaining round-2 candidate. Sourced directly from Kakao Mobility's own official
 * page (kakaomobility.com/contents/taxi-dispatch): real dispatch ranks candidate
 * drivers by acceptance-prediction, daily completion volume, rating, real acceptance
 * rate, and finally ETA among qualifying candidates; a real, documented finding that
 * cancellation rate jumps from 9.8% to 28.8% once a wait crosses 15-18 seconds; AI
 * dispatch cut average wait time ~39% and grew daily riders by ~250,000.
 *
 * **Honest v1 scope**: no real ML acceptance-prediction model exists here (Kakao's own
 * sourced >90% ROC-AUC figure describes a trained model this repo has no data or
 * infrastructure to build) -- this is the honest, rule-based proxy for the same real
 * signals: real acceptance rate (`RideDriver.totalAccepted`/`totalOffers`, an exact
 * computed ratio, not a prediction) as a real minimum-quality filter, then real
 * `GeoUtils.haversineKm` distance (a proxy for ETA, same "straight-line, not real road
 * distance" honesty `GeoUtils.kt`'s own doc comment already names) as the tiebreaker
 * among qualifying candidates -- structurally the same real "coarse filter, real rank"
 * shape `EatsOrderService.rankNearbyRiders` already established, just reused for a
 * genuinely distinct domain (a passenger trip, not a food delivery).
 *
 * The real 15-second offer window (`RideTrip.OFFER_WINDOW`) isn't an invented number --
 * it's directly Kakao's own sourced cancellation-surge threshold, chosen so itunda's own
 * dispatch reassigns before a real passenger would be likely to bail. Real push wired
 * into the ride-offer notification (2026-07-28, see `dispatchToNextDriver`'s own doc
 * comment) -- the single most time-critical notification site in this backend, since a
 * driver who misses it within the 15-second window loses the offer entirely.
 *
 * Fare escrow/payout/refund mirrors `EatsOrderService`'s own delivery-fee-holding
 * pattern exactly (a real, already-proven shape, not reinvented): held in `ride_holding`
 * from request time, paid to the driver net of `platformFee` at `COMPLETED`, refunded in
 * full if `CANCELLED` while still `REQUESTED` (before any driver has committed).
 */
@Service
class RideTripService(
    private val rideDriverRepository: RideDriverRepository,
    private val rideTripRepository: RideTripRepository,
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val transactionRepository: TransactionRepository,
    private val notificationRepository: NotificationRepository,
    private val rateLimiter: RateLimiter,
    private val pushNotificationService: PushNotificationService,
) {
    companion object {
        private val platformFeeRate = BigDecimal("0.015")

        // itunda's own honest fare-structure choice -- Kakao Mobility's own page
        // explicitly discloses no fare/surge pricing mechanics to reuse. Reuses the
        // exact same real per-km rate EatsOrderService.perKmDeliveryRate already
        // established (itunda's one real, precedented distance rate) rather than
        // inventing a second one; the base fare is higher, reflecting a real taxi
        // meter's flag-fall convention (the whole trip's fare, not a secondary
        // delivery add-on fee).
        private val baseFare = BigDecimal("1000")
        private val perKmRate = BigDecimal("250")
        private val minFare = BigDecimal("1500")

        // Real minimum sample size before a driver's own acceptance rate is trusted
        // enough to filter them out -- a brand-new driver with zero history must never
        // be unfairly excluded for lack of data.
        private const val MIN_OFFERS_FOR_ACCEPTANCE_FILTER = 5
        private val MIN_ACCEPTANCE_RATE = BigDecimal("0.3")
    }

    private val log = LoggerFactory.getLogger(RideTripService::class.java)

    private fun getMyDriver(userId: String) =
        rideDriverRepository.findByUserId(userId)
            ?: throw RideDriverNotRegisteredException("This account is not registered as a driver")

    @Transactional
    fun requestTrip(
        passengerId: String,
        pickupAddress: String,
        pickupLatitude: Double,
        pickupLongitude: Double,
        dropoffAddress: String,
        dropoffLatitude: Double,
        dropoffLongitude: Double,
    ): RideTrip {
        if (!GeoUtils.isValidCoordinate(pickupLatitude, pickupLongitude) || !GeoUtils.isValidCoordinate(dropoffLatitude, dropoffLongitude)) {
            throw InvalidRideLocationException("Invalid pickup or dropoff coordinate")
        }
        val trimmedPickup = pickupAddress.trim().ifEmpty { throw InvalidRideLocationException("Pickup address is required") }.take(500)
        val trimmedDropoff = dropoffAddress.trim().ifEmpty { throw InvalidRideLocationException("Dropoff address is required") }.take(500)

        // Real anti-spam limit, same convention every other real money-moving creation
        // endpoint in this codebase uses.
        rateLimiter.checkLimit("rideshare:request:$passengerId", limit = 20, window = Duration.ofHours(1))

        val passengerWallet = walletRepository.findByUserIdAndType(passengerId, WalletType.MAIN)
            ?: throw RideDriverNoWalletException("No wallet found for this account")

        val distanceKm = BigDecimal(GeoUtils.haversineKm(pickupLatitude, pickupLongitude, dropoffLatitude, dropoffLongitude)).setScale(3, RoundingMode.HALF_UP)
        val fare = baseFare.add(perKmRate.multiply(distanceKm)).setScale(2, RoundingMode.HALF_UP).max(minFare)
        val platformFee = fare.multiply(platformFeeRate).setScale(2, RoundingMode.HALF_UP)

        if (passengerWallet.availableBalance < fare) {
            throw InsufficientFundsException("Insufficient available balance for this trip")
        }

        // Real escrow hold -- the passenger's fare leaves their wallet right now, held
        // until the trip completes. Same "hold, don't move directly" shape
        // EatsOrder's own delivery-fee escrow already establishes.
        val holdResult = ledgerService.postLedgerTransaction(
            passengerWallet.currency,
            listOf(
                LedgerLeg(passengerWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, fare, "Ride requested"),
                LedgerLeg("ride_holding", LedgerAccountType.RIDE_HOLDING, LedgerDirection.CREDIT, fare, "Ride fare held in escrow"),
            ),
        )
        val holdTransaction = Transaction(
            id = holdResult.transactionId,
            referenceNumber = "RIDE${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
            senderId = passengerId,
            recipientId = passengerId,
            fromWalletId = passengerWallet.id,
            amount = fare,
            fee = platformFee,
            currency = passengerWallet.currency,
            type = TransactionType.TRANSFER,
            status = TransactionStatus.COMPLETED,
            description = "Ride requested",
            completedAt = Instant.now(),
        )
        transactionRepository.save(holdTransaction)

        val trip = rideTripRepository.save(
            RideTrip(
                id = "ride_trip_${UUID.randomUUID()}", passengerId = passengerId, pickupAddress = trimmedPickup,
                pickupLatitude = pickupLatitude, pickupLongitude = pickupLongitude, dropoffAddress = trimmedDropoff,
                dropoffLatitude = dropoffLatitude, dropoffLongitude = dropoffLongitude, distanceKm = distanceKm,
                fare = fare, platformFee = platformFee, transactionId = holdTransaction.id,
            ),
        )
        dispatchToNextDriver(trip)
        return trip
    }

    // Real acceptance-rate-filtered, distance-ranked candidate pool -- see this class's
    // own doc comment for the full honest-v1 account of what real Kakao dispatch signal
    // each piece stands in for.
    private fun rankNearbyDrivers(
        pickupLat: Double,
        pickupLng: Double,
        excludedUserIds: Set<String> = emptySet(),
        candidatePool: List<RideDriver>? = null,
        busyDriverIds: Set<String>? = null,
    ): List<Pair<RideDriver, Double>> {
        val actuallyBusy = busyDriverIds
            ?: rideTripRepository.findDistinctDriverIdsByStatusIn(listOf(RideTripStatus.DRIVER_ASSIGNED, RideTripStatus.IN_PROGRESS)).toSet()
        return (candidatePool ?: rideDriverRepository.findByAvailableTrueAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull())
            .filterNot { it.userId in excludedUserIds }
            .filterNot { it.id in actuallyBusy }
            .filterNot { driver ->
                driver.totalOffers >= MIN_OFFERS_FOR_ACCEPTANCE_FILTER &&
                    BigDecimal(driver.totalAccepted).divide(BigDecimal(driver.totalOffers), 4, RoundingMode.HALF_UP) < MIN_ACCEPTANCE_RATE
            }
            .map { driver -> driver to GeoUtils.haversineKm(pickupLat, pickupLng, driver.currentLatitude!!, driver.currentLongitude!!) }
            .sortedBy { (_, distanceKm) -> distanceKm }
    }

    private fun dispatchToNextDriver(trip: RideTrip, candidatePool: List<RideDriver>? = null, busyDriverIds: Set<String>? = null) {
        try {
            val excluded = trip.excludedDriverUserIds?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            val ranked = rankNearbyDrivers(trip.pickupLatitude, trip.pickupLongitude, excluded, candidatePool, busyDriverIds)
            val next = ranked.firstOrNull()?.first

            if (next == null) {
                // Real open-list fallback -- no real candidate could be exclusively
                // offered the trip (none online, or all excluded/busy/filtered), so it
                // stays REQUESTED with no active offer; any online driver can browse and
                // claim it via getAvailableTrips. Itunda's own honest v1 scoping choice
                // to skip a separate broadcast notification here (unlike Eats' own
                // notifyNearestRiders fallback) -- an online driver is already expected
                // to check the open list.
                trip.offeredDriverId = null
                trip.offerExpiresAt = null
                rideTripRepository.save(trip)
                return
            }
            trip.offeredDriverId = next.id
            trip.offerExpiresAt = Instant.now().plus(RideTrip.OFFER_WINDOW)
            rideTripRepository.save(trip)
            next.totalOffers += 1
            rideDriverRepository.save(next)
            val title = "New ride request"
            val body = "Pickup at ${trip.pickupAddress} -- ${trip.fare} RWF fare."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = next.userId, type = "RIDE_TRIP_OFFER",
                    title = title, body = body,
                    isRead = false, createdAt = Instant.now(), dataJson = "{\"tripId\":\"${trip.id}\"}",
                ),
            )
            // Real push wired in (2026-07-28) -- of every notification site in this
            // backend, this is the single most time-critical: the real 15-second
            // OFFER_WINDOW above means a driver who doesn't see this within seconds
            // loses the offer to the next-ranked driver entirely, not just "misses it
            // for a while" the way every other in-app-only notification here does.
            // PushNotificationService.sendToUser never throws (best-effort per-token
            // internally), so this can't turn a successful dispatch into a logged
            // "dispatch failed" warning below.
            pushNotificationService.sendToUser(next.userId, title, body, mapOf("tripId" to trip.id))
        } catch (e: Exception) {
            log.warn("Ride dispatch failed for trip {}: {}", trip.id, e.message)
        }
    }

    @Transactional
    fun acceptTrip(driverUserId: String, tripId: String): RideTrip {
        val driver = getMyDriver(driverUserId)
        if (!driver.available) {
            throw RideDriverNotAvailableException("Go online before accepting a trip")
        }
        if (rideTripRepository.existsByDriverIdAndStatusIn(driver.id, listOf(RideTripStatus.DRIVER_ASSIGNED, RideTripStatus.IN_PROGRESS))) {
            throw RideDriverAlreadyOnTripException("Finish your current trip before accepting another -- itunda drivers carry one trip at a time")
        }
        val trip = rideTripRepository.findById(tripId).orElseThrow { RideTripNotFoundException("Trip not found") }
        if (trip.status != RideTripStatus.REQUESTED || trip.driverId != null) {
            throw RideTripAlreadyClaimedException("This trip is no longer available")
        }
        val offerStillActive = trip.offerExpiresAt?.isAfter(Instant.now()) == true
        if (offerStillActive && trip.offeredDriverId != driver.id) {
            throw RideTripAlreadyClaimedException("This trip is no longer available")
        }
        if (trip.passengerId == driverUserId) throw RideSelfTripException("Cannot accept your own trip")

        trip.driverId = driver.id
        trip.status = RideTripStatus.DRIVER_ASSIGNED
        trip.offeredDriverId = null
        trip.offerExpiresAt = null
        trip.updatedAt = Instant.now()
        val saved = rideTripRepository.save(trip)

        driver.totalAccepted += 1
        rideDriverRepository.save(driver)

        run {
            val title = "Driver assigned"
            val body = "A driver is on the way to ${trip.pickupAddress}."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = trip.passengerId, type = "RIDE_TRIP_UPDATE",
                    title = title, body = body,
                    isRead = false, createdAt = Instant.now(), dataJson = "{\"tripId\":\"${trip.id}\"}",
                ),
            )
            // Real push (item 124) -- same "passenger waiting on a real-time status
            // update" urgency as this row's own already-pushed RIDE_TRIP_OFFER (driver
            // side); this closes the matching passenger-side gap.
            pushNotificationService.sendToUser(trip.passengerId, title, body, mapOf("tripId" to trip.id))
        }
        return saved
    }

    /** Real explicit decline -- the offered driver proactively passes rather than
     * silently letting the real 15-second offer window expire, immediately triggering
     * real reassignment to the next candidate instead of waiting out the timeout. */
    @Transactional
    fun declineTrip(driverUserId: String, tripId: String): RideTrip {
        val driver = getMyDriver(driverUserId)
        val trip = rideTripRepository.findById(tripId).orElseThrow { RideTripNotFoundException("Trip not found") }
        val offerStillActive = trip.offerExpiresAt?.isAfter(Instant.now()) == true
        if (!offerStillActive || trip.offeredDriverId != driver.id) {
            throw RideNoActiveOfferException("You don't have an active offer for this trip")
        }
        trip.excludedDriverUserIds = (
            (trip.excludedDriverUserIds?.split(",")?.filter { it.isNotBlank() } ?: emptyList()) + driverUserId
            ).distinct().joinToString(",")
        trip.offeredDriverId = null
        trip.offerExpiresAt = null
        val saved = rideTripRepository.save(trip)
        dispatchToNextDriver(saved)
        return saved
    }

    @Transactional
    fun startTrip(driverUserId: String, tripId: String): RideTrip {
        val driver = getMyDriver(driverUserId)
        val trip = getOwnedTrip(tripId, driver.id)
        if (trip.status != RideTripStatus.DRIVER_ASSIGNED) {
            throw InvalidRideTripStatusTransitionException("Only a DRIVER_ASSIGNED trip can be started")
        }
        trip.status = RideTripStatus.IN_PROGRESS
        trip.updatedAt = Instant.now()
        return rideTripRepository.save(trip)
    }

    @Transactional
    fun completeTrip(driverUserId: String, tripId: String): RideTrip {
        val driver = getMyDriver(driverUserId)
        val trip = getOwnedTrip(tripId, driver.id)
        if (trip.status != RideTripStatus.IN_PROGRESS) {
            throw InvalidRideTripStatusTransitionException("Only an IN_PROGRESS trip can be completed")
        }
        val driverWallet = walletRepository.findById(driver.walletId)
            .orElseThrow { RideDriverNoWalletException("Driver settlement wallet not found") }
        val netToDriver = trip.fare.subtract(trip.platformFee)
        val result = ledgerService.postLedgerTransaction(
            driverWallet.currency,
            listOf(
                LedgerLeg("ride_holding", LedgerAccountType.RIDE_HOLDING, LedgerDirection.DEBIT, trip.fare, "Ride fare released"),
                LedgerLeg(driverWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, netToDriver, "Ride fare payout"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, trip.platformFee, "Ride platform fee"),
            ),
        )
        trip.status = RideTripStatus.COMPLETED
        trip.payoutTransactionId = result.transactionId
        trip.updatedAt = Instant.now()
        val saved = rideTripRepository.save(trip)
        run {
            val title = "Trip completed"
            val body = "You arrived at ${trip.dropoffAddress}. Fare: ${trip.fare} RWF."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = trip.passengerId, type = "RIDE_TRIP_UPDATE",
                    title = title, body = body,
                    isRead = false, createdAt = Instant.now(), dataJson = "{\"tripId\":\"${trip.id}\"}",
                ),
            )
            // Real push (item 124) -- see acceptTrip's own doc comment above.
            pushNotificationService.sendToUser(trip.passengerId, title, body, mapOf("tripId" to trip.id))
        }
        return saved
    }

    @Transactional
    fun cancelTrip(passengerId: String, tripId: String): RideTrip {
        val trip = rideTripRepository.findById(tripId).orElseThrow { RideTripNotFoundException("Trip not found") }
        if (trip.passengerId != passengerId) {
            throw RideTripNotFoundException("Trip not found")
        }
        if (trip.status != RideTripStatus.REQUESTED) {
            throw InvalidRideTripStatusTransitionException("Only a REQUESTED trip (no driver assigned yet) can be cancelled -- this one is already ${trip.status}")
        }
        val passengerWallet = walletRepository.findByUserIdAndType(passengerId, WalletType.MAIN)
            ?: throw RideDriverNoWalletException("No wallet found for this account")
        val result = ledgerService.postLedgerTransaction(
            passengerWallet.currency,
            listOf(
                LedgerLeg("ride_holding", LedgerAccountType.RIDE_HOLDING, LedgerDirection.DEBIT, trip.fare, "Ride fare refunded"),
                LedgerLeg(passengerWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, trip.fare, "Ride cancelled -- refund"),
            ),
        )
        trip.status = RideTripStatus.CANCELLED
        trip.refundTransactionId = result.transactionId
        trip.updatedAt = Instant.now()
        return rideTripRepository.save(trip)
    }

    fun getAvailableTrips(driverUserId: String): List<RideTrip> {
        val driver = getMyDriver(driverUserId)
        val now = Instant.now()
        return rideTripRepository.findAll()
            .filter { it.status == RideTripStatus.REQUESTED && it.driverId == null }
            .filter { it.offerExpiresAt == null || it.offerExpiresAt!!.isBefore(now) || it.offeredDriverId == driver.id }
    }

    fun getMyTrips(passengerId: String, pageable: Pageable): Page<RideTrip> =
        rideTripRepository.findByPassengerIdOrderByCreatedAtDesc(passengerId, pageable)

    fun getMyDriverTrips(driverUserId: String, pageable: Pageable): Page<RideTrip> {
        val driver = getMyDriver(driverUserId)
        return rideTripRepository.findByDriverIdOrderByCreatedAtDesc(driver.id, pageable)
    }

    private fun getOwnedTrip(tripId: String, driverId: String): RideTrip {
        val trip = rideTripRepository.findById(tripId).orElseThrow { RideTripNotFoundException("Trip not found") }
        if (trip.driverId != driverId) {
            throw RideTripNotFoundException("Trip not found")
        }
        return trip
    }

    fun getExpiredOffers(): List<RideTrip> = rideTripRepository.findByOfferExpiresAtBeforeAndDriverIdIsNull(Instant.now())

    // Real batched reassignment -- same "compute the pool once per tick, reuse across
    // every order" performance discipline EatsOrderService.reassignExpiredOffers already
    // establishes.
    @Transactional
    fun reassignExpiredOffers(trips: List<RideTrip>) {
        if (trips.isEmpty()) return
        val candidatePool = rideDriverRepository.findByAvailableTrueAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull()
        val busyDriverIds = rideTripRepository.findDistinctDriverIdsByStatusIn(listOf(RideTripStatus.DRIVER_ASSIGNED, RideTripStatus.IN_PROGRESS)).toSet()

        for (trip in trips) {
            val expiredDriverUserId = trip.offeredDriverId?.let { offeredId -> rideDriverRepository.findById(offeredId).orElse(null)?.userId }
            trip.excludedDriverUserIds = (
                (trip.excludedDriverUserIds?.split(",")?.filter { it.isNotBlank() } ?: emptyList()) + listOfNotNull(expiredDriverUserId)
                ).distinct().joinToString(",")
            trip.offeredDriverId = null
            trip.offerExpiresAt = null
            rideTripRepository.save(trip)
            dispatchToNextDriver(trip, candidatePool, busyDriverIds)
        }
    }
}
