package rw.itunda.rideshare

import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Message
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.RideDriver
import rw.itunda.core.domain.RideTrip
import rw.itunda.core.domain.RideTripStatus
import rw.itunda.core.domain.RideTripStop
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.WalletType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.geo.OsrmRoutingClient
import rw.itunda.core.geo.TravelMode
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.RideDriverRepository
import rw.itunda.core.repository.RideTripRepository
import rw.itunda.core.repository.RideTripStopRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import rw.itunda.messaging.MessagingService
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
class InvalidScheduledRideTimeException(message: String) : RuntimeException(message)
class RideTooManyStopsException(message: String) : RuntimeException(message)
class RideNoRemainingStopsException(message: String) : RuntimeException(message)
class RidePinMismatchException(message: String) : RuntimeException(message)
class InvalidEarningsRangeException(message: String) : RuntimeException(message)
class RideTripNotCompletedException(message: String) : RuntimeException(message)
class RideTripAlreadyTippedException(message: String) : RuntimeException(message)
class RideTripTipWindowExpiredException(message: String) : RuntimeException(message)
class InvalidTipAmountException(message: String) : RuntimeException(message)

// Real Kakao T-style multi-stop waypoint input (item 214) -- see RideTripStop.kt's own
// doc comment.
data class RideStopInput(val address: String, val latitude: Double, val longitude: Double)

// Real Uber Driver app-style earnings report (2026-08-16) -- see
// RideTripService.getMyEarnings's own doc comment.
data class DriverEarningsDay(
    val date: java.time.LocalDate,
    val tripCount: Int,
    val grossFare: BigDecimal,
    val platformFees: BigDecimal,
    val netEarnings: BigDecimal,
)

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
 *
 * 2026-08-17: the real fare distance itself now prefers `OsrmRoutingClient.routeThrough`
 * -- itunda's own self-hosted OSRM instance, already proven in
 * `EatsOrderService`/`MarketplaceService` -- over the straight-line
 * `GeoUtils.haversineKm` sum this class's own doc comment above used to name as the
 * fare's only source. `GeoUtils.kt`'s own doc comment named this exact gap as "a real,
 * named follow-up once this lands" back when no self-hosted routing existed; it has
 * since landed for delivery fees and marketplace meetup distances, but ride fares --
 * itunda's single largest real per-trip charge -- were never updated to use it. A
 * single `routeThrough` call replaces the old per-leg haversine stitching (itself
 * already correct for multi-stop trips, just straight-line), same never-fail `null`
 * fallback discipline every other real OSRM caller in this codebase already follows:
 * unconfigured, unreachable, or no real route found all fall back to the exact same
 * haversine sum this class always computed before, so a real trip is never blocked or
 * degraded by OSRM being unavailable. Dispatch's own driver-ranking tiebreaker (an ETA
 * proxy, not a fare) deliberately keeps using haversine unchanged -- that's a coarse,
 * cheap in-memory filter across every candidate driver, not the one real per-trip
 * distance a passenger is actually charged for.
 */
@Service
class RideTripService(
    private val rideDriverRepository: RideDriverRepository,
    private val rideTripRepository: RideTripRepository,
    private val rideTripStopRepository: RideTripStopRepository,
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val transactionRepository: TransactionRepository,
    private val notificationRepository: NotificationRepository,
    private val rateLimiter: RateLimiter,
    private val pushNotificationService: PushNotificationService,
    private val messagingService: MessagingService,
    private val fraudRuleEngine: FraudRuleEngine,
    private val osrmRoutingClient: OsrmRoutingClient,
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

        // Real Kakao T 예약 호출 (scheduled ride booking) -- see RideTrip.scheduledFor's
        // own doc comment. Same 2-day bound EatsOrderService.SCHEDULED_ORDER_MAX_WINDOW
        // already established for Baemin-style 예약주문, itunda's own consistent choice.
        val SCHEDULED_RIDE_MAX_WINDOW: Duration = Duration.ofDays(2)

        // Real dispatch only starts shortly before a scheduled trip's real requested
        // time -- dispatching hours or days early would offer a driver a trip nobody
        // wants picked up yet. Kakao's own real lead time isn't published; itunda's own
        // honest choice, not a fabricated real number.
        val SCHEDULED_RIDE_DISPATCH_LEAD_TIME: Duration = Duration.ofMinutes(10)

        // Real Kakao T-style multi-stop rides (item 214) -- Kakao T's own real,
        // currently-live cap on extra waypoints between pickup and dropoff.
        const val MAX_STOPS = 3

        // Real Uber post-trip tipping window (uber.com/us/en/ride/how-it-works/tips):
        // "you have 30 days to add a tip in the app." itunda's real trip has no separate
        // completedAt column -- updatedAt is only ever touched again after COMPLETED by
        // a tip itself, so it's the honest real completion timestamp to measure from.
        val TIP_WINDOW: Duration = Duration.ofDays(30)

        // Real Uber cancellation-fee policy
        // (help.uber.com/riders/article/cancellation-fees-explained): "for most economy
        // ride types... fees may be charged if you cancel 2+ minutes after requesting"
        // once matched with a driver -- itunda's one real ride type maps to Uber's
        // economy tier, so the real 2-minute figure applies directly, not an invented
        // one.
        val CANCELLATION_FEE_GRACE_PERIOD: Duration = Duration.ofMinutes(2)

        // Real Uber policy states the fee "pay[s] drivers for the time and effort they
        // spend getting to your location" but publishes no fixed real number (it "varies
        // by location"). itunda's own honest modeled choice: the driver's real flag-fall
        // (`baseFare`) -- roughly what they'd have earned just for showing up, the same
        // reasoning Uber's own stated rationale describes, not a fabricated real figure.
        val CANCELLATION_FEE = baseFare
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
        // Real Kakao T 예약 호출 (scheduled ride booking) -- null (the default) means
        // ASAP, every existing caller's behavior completely unchanged. See
        // RideTrip.scheduledFor's own doc comment for the real bound/lead-time rules.
        scheduledFor: Instant? = null,
        // Real Kakao T-style multi-stop rides (item 214) -- empty (the default) means a
        // direct pickup-to-dropoff trip, every existing caller's behavior completely
        // unchanged. See RideTripStop.kt's own doc comment.
        stops: List<RideStopInput> = emptyList(),
    ): RideTrip {
        if (!GeoUtils.isValidCoordinate(pickupLatitude, pickupLongitude) || !GeoUtils.isValidCoordinate(dropoffLatitude, dropoffLongitude)) {
            throw InvalidRideLocationException("Invalid pickup or dropoff coordinate")
        }
        if (stops.size > MAX_STOPS) {
            throw RideTooManyStopsException("A trip can have at most $MAX_STOPS extra stops")
        }
        stops.forEach {
            if (!GeoUtils.isValidCoordinate(it.latitude, it.longitude)) {
                throw InvalidRideLocationException("Invalid stop coordinate")
            }
        }
        if (scheduledFor != null) {
            val now = Instant.now()
            if (!scheduledFor.isAfter(now)) {
                throw InvalidScheduledRideTimeException("Scheduled time must be in the future")
            }
            if (scheduledFor.isAfter(now.plus(SCHEDULED_RIDE_MAX_WINDOW))) {
                throw InvalidScheduledRideTimeException("Scheduled time must be within ${SCHEDULED_RIDE_MAX_WINDOW.toDays()} days")
            }
        }
        val trimmedPickup = pickupAddress.trim().ifEmpty { throw InvalidRideLocationException("Pickup address is required") }.take(500)
        val trimmedDropoff = dropoffAddress.trim().ifEmpty { throw InvalidRideLocationException("Dropoff address is required") }.take(500)

        // Real anti-spam limit, same convention every other real money-moving creation
        // endpoint in this codebase uses.
        rateLimiter.checkLimit("rideshare:request:$passengerId", limit = 20, window = Duration.ofHours(1))

        val passengerWallet = walletRepository.findByUserIdAndType(passengerId, WalletType.MAIN)
            ?: throw RideDriverNoWalletException("No wallet found for this account")

        // Real multi-stop distance -- pickup -> stop 1 -> ... -> dropoff. Prefers one
        // real OSRM routeThrough call across the whole itinerary (real road distance,
        // the same never-fail-null-falls-back-to-haversine discipline
        // EatsOrderService/MarketplaceService already establish for this exact client);
        // falls back to summing consecutive real GeoUtils.haversineKm legs (straight-line
        // honesty GeoUtils.kt's own doc comment already names) when OSRM is unconfigured,
        // unreachable, or finds no route -- never blocks a real trip request either way.
        val routePoints = listOf(pickupLatitude to pickupLongitude) + stops.map { it.latitude to it.longitude } + listOf(dropoffLatitude to dropoffLongitude)
        val totalDistanceKm = osrmRoutingClient.routeThrough(routePoints, TravelMode.DRIVING)?.distanceKm
            ?: routePoints.zipWithNext().sumOf { (a, b) -> GeoUtils.haversineKm(a.first, a.second, b.first, b.second) }
        val distanceKm = BigDecimal(totalDistanceKm).setScale(3, RoundingMode.HALF_UP)
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
        // Real, sourced follow-up named in FraudRuleEngine's own doc comment: a ride
        // fare hold is a real money-leaving-account flow. recipientUserId is null --
        // no driver has been matched yet at request time (dispatch happens after), same
        // shape as BillsService's external-recipient payments, so only HIGH_VALUE/
        // VELOCITY apply here, not NEW_RECIPIENT.
        fraudRuleEngine.evaluate(passengerId, null, fare, holdResult.transactionId)
        transactionRepository.save(holdTransaction)

        val trip = rideTripRepository.save(
            RideTrip(
                id = "ride_trip_${UUID.randomUUID()}", passengerId = passengerId, pickupAddress = trimmedPickup,
                pickupLatitude = pickupLatitude, pickupLongitude = pickupLongitude, dropoffAddress = trimmedDropoff,
                dropoffLatitude = dropoffLatitude, dropoffLongitude = dropoffLongitude, distanceKm = distanceKm,
                fare = fare, platformFee = platformFee, transactionId = holdTransaction.id, scheduledFor = scheduledFor,
                pin = generatePin(),
            ),
        )
        stops.forEachIndexed { index, stop ->
            rideTripStopRepository.save(
                RideTripStop(
                    id = "ride_trip_stop_${UUID.randomUUID()}", tripId = trip.id, sequence = index,
                    address = stop.address.trim().take(500), latitude = stop.latitude, longitude = stop.longitude,
                ),
            )
        }
        // Real Kakao T 예약 호출 -- a trip scheduled well ahead is not dispatched now;
        // RideDispatchScheduler starts real dispatch once it's within
        // SCHEDULED_RIDE_DISPATCH_LEAD_TIME of the requested time (see
        // activateScheduledDispatch's own doc comment). A near-future or ASAP request
        // dispatches immediately, completely unchanged.
        if (scheduledFor == null || !scheduledFor.isAfter(Instant.now().plus(SCHEDULED_RIDE_DISPATCH_LEAD_TIME))) {
            dispatchToNextDriver(trip)
        }
        return trip
    }

    // Real acceptance-rate-filtered, distance-ranked candidate pool -- see this class's
    // own doc comment for the full honest-v1 account of what real Kakao dispatch signal
    // each piece stands in for.
    //
    // Real Uber "Destination Filter" restriction (2026-08-16, help.uber.com/en-GB/
    // driving-and-delivering/article/driver-destination-filter) -- Uber's own real
    // account: "the dropoff location should bring you closer to your final
    // destination." A driver with an active filter is excluded from a trip's candidate
    // pool unless this trip's real dropoff genuinely reduces their real haversine
    // distance to their own chosen destination versus their current position -- never a
    // driver preference boost (this backend has no real signal to honestly weight one),
    // just the same real eligibility restriction Uber's own help article describes. A
    // driver with no active filter (the default, `destinationLatitude == null`) is
    // completely unaffected, same backward-compatible shape every other real toggle in
    // this class already establishes.
    private fun rankNearbyDrivers(
        pickupLat: Double,
        pickupLng: Double,
        dropoffLat: Double,
        dropoffLng: Double,
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
            .filterNot { driver ->
                val destLat = driver.destinationLatitude
                val destLng = driver.destinationLongitude
                if (destLat == null || destLng == null) {
                    false
                } else {
                    val distanceFromCurrent = GeoUtils.haversineKm(driver.currentLatitude!!, driver.currentLongitude!!, destLat, destLng)
                    val distanceFromDropoff = GeoUtils.haversineKm(dropoffLat, dropoffLng, destLat, destLng)
                    distanceFromDropoff >= distanceFromCurrent
                }
            }
            .map { driver -> driver to GeoUtils.haversineKm(pickupLat, pickupLng, driver.currentLatitude!!, driver.currentLongitude!!) }
            .sortedBy { (_, distanceKm) -> distanceKm }
    }

    private fun dispatchToNextDriver(trip: RideTrip, candidatePool: List<RideDriver>? = null, busyDriverIds: Set<String>? = null) {
        try {
            val excluded = trip.excludedDriverUserIds?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            val ranked = rankNearbyDrivers(trip.pickupLatitude, trip.pickupLongitude, trip.dropoffLatitude, trip.dropoffLongitude, excluded, candidatePool, busyDriverIds)
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
            sendTripPushAfterCommit(next.userId, title, body, trip.id)
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
        trip.driverAssignedAt = Instant.now()
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
            sendTripPushAfterCommit(trip.passengerId, title, body, trip.id)
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

    // Real Kakao T-style multi-stop rides (item 214) -- every real waypoint recorded for
    // a trip, in real visit order.
    fun getTripStops(tripId: String): List<RideTripStop> = rideTripStopRepository.findByTripIdOrderBySequenceAsc(tripId)

    /**
     * Real driver-marks-arrival at the next unvisited stop, in order -- can't skip ahead
     * to a later stop before an earlier one is real-marked arrived. See
     * `RideTripStop.kt`'s own doc comment.
     */
    @Transactional
    fun arriveAtStop(driverUserId: String, tripId: String): RideTripStop {
        val driver = getMyDriver(driverUserId)
        val trip = getOwnedTrip(tripId, driver.id)
        if (trip.status != RideTripStatus.IN_PROGRESS) {
            throw InvalidRideTripStatusTransitionException("Only an IN_PROGRESS trip can have a stop marked arrived")
        }
        val nextStop = rideTripStopRepository.findByTripIdOrderBySequenceAsc(tripId).firstOrNull { it.arrivedAt == null }
            ?: throw RideNoRemainingStopsException("This trip has no remaining stops")
        nextStop.arrivedAt = Instant.now()
        return rideTripStopRepository.save(nextStop)
    }

    // Real Uber "Verify Your Ride" PIN (uber.com/pl/en/blog/pin-number) -- not a secret
    // requiring cryptographic randomness, just a real-time verbal-confirmation code the
    // passenger reads aloud to the driver, same threat model/generation convention
    // MerchantService.generateUssdCode already established for a similar short numeric
    // code. No collision check needed -- unlike the USSD merchant code, this is scoped
    // to one trip, never looked up globally.
    private fun generatePin(): String = (1000..9999).random().toString()

    /** Real passenger-only PIN lookup -- a stranger, or even the trip's own driver, gets
     * a real 404 (same IDOR discipline as every other resource-ownership check in this
     * codebase). The driver is never shown this via any API response (see
     * RideTrip.pin's own @JsonIgnore); they can only learn it verbally from the
     * passenger, which is the entire real point of the check in startTrip below. */
    fun getTripPin(passengerUserId: String, tripId: String): String {
        val trip = rideTripRepository.findById(tripId).orElseThrow { RideTripNotFoundException("Trip not found") }
        if (trip.passengerId != passengerUserId) throw RideTripNotFoundException("Trip not found")
        return trip.pin ?: ""
    }

    // Real Uber "Share Trip Status" (2026-08-16, help.uber.com/en/riders/article/
    // sharing-your-trip-status-faq) -- Uber's own real feature sends an unauthenticated
    // public link (no app required) showing a live map + driver name/plate to up to 5
    // contacts. itunda has no public, unauthenticated share-link surface anywhere (every
    // screen is auth-gated, same real constraint EatsFavoriteService
    // .shareFavoritesToConversation's own doc comment already names for an identical
    // gap), so the honest analogue is itunda's own established "send a real message into
    // a real Talk conversation" convention -- same shape that favorites-sharing already
    // uses, just a plain formatted text snapshot rather than a live-updating link (no
    // push-driven message-edit infrastructure exists to keep it live either). Also
    // honestly scoped to what RideDriver actually has: no name/vehicle-plate field
    // exists on this entity at all (the same real limitation DriverRatingSection's own
    // doc comment already names), so the shared message includes trip status, pickup/
    // dropoff addresses, and the driver's real current coordinates (if assigned) --
    // never a fabricated name or plate.
    @Transactional
    fun shareTripStatus(passengerUserId: String, tripId: String, conversationId: String): Message {
        val trip = rideTripRepository.findById(tripId).orElseThrow { RideTripNotFoundException("Trip not found") }
        if (trip.passengerId != passengerUserId) throw RideTripNotFoundException("Trip not found")
        messagingService.getConversationForParticipant(passengerUserId, conversationId)

        val driverLocation = trip.driverId?.let { driverId ->
            rideDriverRepository.findById(driverId).orElse(null)
                ?.takeIf { it.currentLatitude != null && it.currentLongitude != null }
        }
        val locationLine = driverLocation?.let {
            "\nDriver's last known location: ${it.currentLatitude}, ${it.currentLongitude}"
        } ?: ""
        val body = "🚗 My ride status: ${trip.status}\n" +
            "From: ${trip.pickupAddress}\n" +
            "To: ${trip.dropoffAddress}$locationLine"
        return messagingService.sendMessage(passengerUserId, conversationId, body)
    }

    @Transactional
    fun startTrip(driverUserId: String, tripId: String, pin: String): RideTrip {
        val driver = getMyDriver(driverUserId)
        val trip = getOwnedTrip(tripId, driver.id)
        if (trip.status != RideTripStatus.DRIVER_ASSIGNED) {
            throw InvalidRideTripStatusTransitionException("Only a DRIVER_ASSIGNED trip can be started")
        }
        // A trip requested before this feature shipped has no real PIN to check against
        // (trip.pin is null) -- skip rather than permanently lock out an in-flight trip
        // that predates it.
        if (trip.pin != null && trip.pin != pin.trim()) {
            throw RidePinMismatchException("Incorrect PIN -- ask your passenger for the 4-digit code shown in their app")
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
            sendTripPushAfterCommit(trip.passengerId, title, body, trip.id)
        }
        return saved
    }

    /**
     * Real Uber post-trip tipping (2026-08-16, uber.com/us/en/ride/how-it-works/tips) --
     * "Tips go directly to drivers; Uber doesn't charge service fees on tips." A direct
     * real passenger-wallet-to-driver-wallet transfer, never routed through
     * `ride_holding` (unlike the fare itself) since a tip isn't itunda's revenue to hold
     * or take a cut of. Real once-only ([RideTripAlreadyTippedException]) and real
     * 30-day-window ([RideTripTipWindowExpiredException]) enforcement, matching Uber's
     * own real published rules exactly.
     */
    @Transactional
    fun tipDriver(passengerUserId: String, tripId: String, amount: BigDecimal): RideTrip {
        val trip = rideTripRepository.findById(tripId).orElseThrow { RideTripNotFoundException("Trip not found") }
        if (trip.passengerId != passengerUserId) {
            throw RideTripNotFoundException("Trip not found")
        }
        if (amount <= BigDecimal.ZERO) {
            throw InvalidTipAmountException("Tip amount must be greater than zero")
        }
        if (trip.status != RideTripStatus.COMPLETED) {
            throw RideTripNotCompletedException("Only a completed trip can be tipped")
        }
        if (trip.tipAmount != null) {
            throw RideTripAlreadyTippedException("This trip has already been tipped")
        }
        if (Instant.now().isAfter(trip.updatedAt.plus(TIP_WINDOW))) {
            throw RideTripTipWindowExpiredException("Tips can only be added within ${TIP_WINDOW.toDays()} days of trip completion")
        }
        val driverId = trip.driverId ?: throw RideDriverNotRegisteredException("Driver not found")
        val driver = rideDriverRepository.findById(driverId).orElseThrow { RideDriverNotRegisteredException("Driver not found") }
        val passengerWallet = walletRepository.findByUserIdAndType(passengerUserId, WalletType.MAIN)
            ?: throw RideDriverNoWalletException("No wallet found for this account")
        val driverWallet = walletRepository.findById(driver.walletId)
            .orElseThrow { RideDriverNoWalletException("Driver settlement wallet not found") }
        if (passengerWallet.availableBalance < amount) {
            throw InsufficientFundsException("Insufficient available balance for this tip")
        }
        val result = ledgerService.postLedgerTransaction(
            passengerWallet.currency,
            listOf(
                LedgerLeg(passengerWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, amount, "Tip for ride to ${trip.dropoffAddress}"),
                LedgerLeg(driverWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, amount, "Tip received"),
            ),
        )
        trip.tipAmount = amount
        trip.tipTransactionId = result.transactionId
        val saved = rideTripRepository.save(trip)
        val title = "You received a tip"
        val body = "You received a ${amount} RWF tip for a recent trip."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = driver.userId, type = "RIDE_TIP_RECEIVED",
                title = title, body = body, isRead = false, createdAt = Instant.now(), dataJson = "{\"tripId\":\"${trip.id}\"}",
            ),
        )
        sendTripPushAfterCommit(driver.userId, title, body, trip.id)
        return saved
    }

    // Real Uber cancellation-fee policy (help.uber.com/riders/article/cancellation-fees-explained)
    // -- see RideTripStatus's own doc comment for why DRIVER_ASSIGNED only recently
    // became cancellable at all. A REQUESTED trip (no driver has committed yet) still
    // always refunds in full, completely unchanged. A DRIVER_ASSIGNED trip cancelled
    // within CANCELLATION_FEE_GRACE_PERIOD of driverAssignedAt also refunds in full,
    // matching Uber's own real "2+ minutes after requesting" threshold; past that
    // window, CANCELLATION_FEE is carved out of the refund and paid straight to the
    // driver's settlement wallet -- Uber's own stated rationale ("pay drivers for the
    // time and effort they spend getting to your location"), not a punitive platform
    // fee, so platformFee itself is never charged on a cancellation either way.
    @Transactional
    fun cancelTrip(passengerId: String, tripId: String): RideTrip {
        val trip = rideTripRepository.findById(tripId).orElseThrow { RideTripNotFoundException("Trip not found") }
        if (trip.passengerId != passengerId) {
            throw RideTripNotFoundException("Trip not found")
        }
        if (trip.status != RideTripStatus.REQUESTED && trip.status != RideTripStatus.DRIVER_ASSIGNED) {
            throw InvalidRideTripStatusTransitionException("Only a REQUESTED or DRIVER_ASSIGNED trip can be cancelled -- this one is already ${trip.status}")
        }
        val passengerWallet = walletRepository.findByUserIdAndType(passengerId, WalletType.MAIN)
            ?: throw RideDriverNoWalletException("No wallet found for this account")

        val assignedAt = trip.driverAssignedAt
        val withinGracePeriod = assignedAt == null || !Instant.now().isAfter(assignedAt.plus(CANCELLATION_FEE_GRACE_PERIOD))
        val cancellationFee = if (trip.status == RideTripStatus.DRIVER_ASSIGNED && !withinGracePeriod) {
            CANCELLATION_FEE.min(trip.fare)
        } else {
            BigDecimal.ZERO
        }
        val refundAmount = trip.fare.subtract(cancellationFee)

        val driver = trip.driverId?.let { rideDriverRepository.findById(it).orElse(null) }
        val driverWallet = if (cancellationFee > BigDecimal.ZERO) {
            driver?.let { walletRepository.findById(it.walletId).orElse(null) }
        } else {
            null
        }

        val legs = mutableListOf(
            LedgerLeg("ride_holding", LedgerAccountType.RIDE_HOLDING, LedgerDirection.DEBIT, trip.fare, "Ride fare refunded"),
            LedgerLeg(passengerWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, refundAmount, "Ride cancelled -- refund"),
        )
        if (cancellationFee > BigDecimal.ZERO && driverWallet != null) {
            legs.add(LedgerLeg(driverWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, cancellationFee, "Cancellation fee"))
        } else if (cancellationFee > BigDecimal.ZERO) {
            // Driver's own settlement wallet is somehow gone -- never strand escrow money
            // mid-refund; fall back to refunding the passenger in full rather than
            // leaving the fee portion unaccounted for.
            legs[1] = LedgerLeg(passengerWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, trip.fare, "Ride cancelled -- refund")
        }
        val result = ledgerService.postLedgerTransaction(passengerWallet.currency, legs)

        trip.status = RideTripStatus.CANCELLED
        trip.refundTransactionId = result.transactionId
        trip.updatedAt = Instant.now()
        val saved = rideTripRepository.save(trip)

        if (cancellationFee > BigDecimal.ZERO && driverWallet != null && driver != null) {
            val title = "Rider cancelled -- you were paid a cancellation fee"
            val body = "The rider cancelled after you were already on the way. You received a ${cancellationFee} RWF cancellation fee."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = driver.userId, type = "RIDE_CANCELLATION_FEE_PAID",
                    title = title, body = body, isRead = false, createdAt = Instant.now(), dataJson = "{\"tripId\":\"${trip.id}\"}",
                ),
            )
            sendTripPushAfterCommit(driver.userId, title, body, trip.id)
        } else if (driver != null) {
            val title = "Trip cancelled"
            val body = "The rider cancelled this trip."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = driver.userId, type = "RIDE_TRIP_UPDATE",
                    title = title, body = body, isRead = false, createdAt = Instant.now(), dataJson = "{\"tripId\":\"${trip.id}\"}",
                ),
            )
            sendTripPushAfterCommit(driver.userId, title, body, trip.id)
        }
        return saved
    }

    fun getAvailableTrips(driverUserId: String): List<RideTrip> {
        val driver = getMyDriver(driverUserId)
        val now = Instant.now()
        return rideTripRepository.findAll()
            .filter { it.status == RideTripStatus.REQUESTED && it.driverId == null }
            .filter { it.offerExpiresAt == null || it.offerExpiresAt!!.isBefore(now) || it.offeredDriverId == driver.id }
            // Real Kakao T 예약 호출 -- a scheduled trip whose real dispatch hasn't
            // started yet (RideDispatchScheduler.activateScheduledDispatch) must never
            // appear in open browse hours or days early.
            .filter { it.scheduledFor == null || it.scheduledDispatchStartedAt != null }
    }

    fun getMyTrips(passengerId: String, pageable: Pageable): Page<RideTrip> =
        rideTripRepository.findByPassengerIdOrderByCreatedAtDesc(passengerId, pageable)

    fun getMyDriverTrips(driverUserId: String, pageable: Pageable): Page<RideTrip> {
        val driver = getMyDriver(driverUserId)
        return rideTripRepository.findByDriverIdOrderByCreatedAtDesc(driver.id, pageable)
    }

    // Real Uber Driver app-style earnings report (2026-08-16) -- Uber's own real
    // driver-facing "Earnings" tab shows a day-by-day trip count and net-of-platform-fee
    // total, not just a raw trip list. `completeTrip` already computes the exact real
    // `netToDriver = fare - platformFee` split at settlement time; this just aggregates
    // it per real COMPLETED day. Same bounded-31-day-window + in-memory-grouping shape
    // MerchantService.getReport's own doc comment already justifies (RideTrip.createdAt
    // is a timestamp, not a pre-truncated date column). Grouped by request date
    // (createdAt), same convention getReport itself uses -- no separate completedAt
    // column exists on this entity either.
    fun getMyEarnings(driverUserId: String, from: java.time.LocalDate, to: java.time.LocalDate): List<DriverEarningsDay> {
        if (from.isAfter(to)) {
            throw InvalidEarningsRangeException("Report start date must be on or before the end date")
        }
        if (from.plusDays(30).isBefore(to)) {
            throw InvalidEarningsRangeException("Earnings reports are limited to 31 days at a time")
        }
        val driver = getMyDriver(driverUserId)
        val fromInstant = from.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
        val toInstant = to.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
        val trips = rideTripRepository.findByDriverIdAndStatusAndCreatedAtBetween(driver.id, RideTripStatus.COMPLETED, fromInstant, toInstant)
        return trips
            .groupBy { java.time.LocalDate.ofInstant(it.createdAt, java.time.ZoneOffset.UTC) }
            .map { (date, dayTrips) ->
                val gross = dayTrips.fold(BigDecimal.ZERO) { acc, t -> acc + t.fare }
                val fees = dayTrips.fold(BigDecimal.ZERO) { acc, t -> acc + t.platformFee }
                DriverEarningsDay(
                    date = date, tripCount = dayTrips.size, grossFare = gross, platformFees = fees,
                    netEarnings = gross.subtract(fees),
                )
            }
            .sortedBy { it.date }
    }

    /** Dispatch and passenger updates must reflect committed trip and settlement state. */
    private fun sendTripPushAfterCommit(userId: String, title: String, body: String, tripId: String) {
        val send = {
            try {
                pushNotificationService.sendToUser(userId, title, body, mapOf("tripId" to tripId))
            } catch (e: Exception) {
                log.warn("Could not send ride-trip push for trip {}", tripId, e)
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

    private fun getOwnedTrip(tripId: String, driverId: String): RideTrip {
        val trip = rideTripRepository.findById(tripId).orElseThrow { RideTripNotFoundException("Trip not found") }
        if (trip.driverId != driverId) {
            throw RideTripNotFoundException("Trip not found")
        }
        return trip
    }

    fun getExpiredOffers(): List<RideTrip> = rideTripRepository.findByOfferExpiresAtBeforeAndDriverIdIsNull(Instant.now())

    /**
     * Real once-per-tick candidate pool, shared by `reassignExpiredOffer` and
     * `activateScheduledDispatchOne` -- preserves the "compute the pool once per tick,
     * reuse across every trip" performance discipline the old batched
     * `reassignExpiredOffers(trips: List<RideTrip>)` established, without requiring the
     * per-trip work itself to live inside a shared transaction (see that method's own
     * removal note below for why).
     */
    fun computeDispatchPools(): DispatchPools {
        val candidatePool = rideDriverRepository.findByAvailableTrueAndCurrentLatitudeIsNotNullAndCurrentLongitudeIsNotNull()
        val busyDriverIds = rideTripRepository.findDistinctDriverIdsByStatusIn(listOf(RideTripStatus.DRIVER_ASSIGNED, RideTripStatus.IN_PROGRESS)).toSet()
        return DispatchPools(candidatePool, busyDriverIds)
    }

    /**
     * Called by RideDispatchScheduler, once per expired offer -- see that class's own
     * doc comment for the real batch-transaction-poisoning bug this closes
     * (docs/DESIGN_REFERENCES.md Section 180). This used to be
     * `reassignExpiredOffers(trips: List<RideTrip>)`, a single `@Transactional` method
     * that looped over EVERY expired offer network-wide in one shared transaction; the
     * `rideTripRepository.save(trip)` bookkeeping write ahead of `dispatchToNextDriver`
     * was not itself guarded by a try/catch the way `dispatchToNextDriver`'s own body
     * already is. `RideTrip` carries a real `@Version` column, so a genuine concurrent
     * `acceptTrip`/`declineTrip` racing this exact trip between the scheduler's read and
     * this save throws a real `ObjectOptimisticLockingFailureException` -- uncaught, that
     * would have rolled back every OTHER already-reassigned trip's real offer/exclusion
     * update (and any already-committed dispatch-to-next-driver work) from that same
     * poll tick too, not just the racing trip's. `RideDispatchScheduler` polls every 3
     * real seconds against a real 15-second offer window, so this race was genuinely
     * reachable, not theoretical. The re-check below (still expired, still unclaimed) is
     * the same re-check-before-act guard `MerchantBookingService.processNoShow`/
     * `ParkingService.forceEndAbandonedSession` already establish -- a trip a driver
     * claimed in that same gap safely no-ops here instead of throwing.
     */
    @Transactional
    fun reassignExpiredOffer(tripId: String, pools: DispatchPools) {
        val trip = rideTripRepository.findById(tripId).orElse(null) ?: return
        if (trip.driverId != null || trip.offerExpiresAt == null || trip.offerExpiresAt!!.isAfter(Instant.now())) return
        val expiredDriverUserId = trip.offeredDriverId?.let { offeredId -> rideDriverRepository.findById(offeredId).orElse(null)?.userId }
        trip.excludedDriverUserIds = (
            (trip.excludedDriverUserIds?.split(",")?.filter { it.isNotBlank() } ?: emptyList()) + listOfNotNull(expiredDriverUserId)
            ).distinct().joinToString(",")
        trip.offeredDriverId = null
        trip.offerExpiresAt = null
        rideTripRepository.save(trip)
        dispatchToNextDriver(trip, pools.candidatePool, pools.busyDriverIds)
    }

    // Real Kakao T 예약 호출 (scheduled ride booking) -- every real scheduled trip
    // whose SCHEDULED_RIDE_DISPATCH_LEAD_TIME threshold has just been crossed.
    fun getDueScheduledTrips(): List<RideTrip> =
        rideTripRepository.findByStatusAndScheduledForIsNotNullAndScheduledForBeforeAndScheduledDispatchStartedAtIsNull(
            RideTripStatus.REQUESTED,
            Instant.now().plus(SCHEDULED_RIDE_DISPATCH_LEAD_TIME),
        )

    /**
     * Called by RideDispatchScheduler, once per due scheduled trip -- see that class's
     * own doc comment for the real batch-transaction-poisoning bug this closes
     * (docs/DESIGN_REFERENCES.md Section 180), the identical shape/fix
     * `reassignExpiredOffer`'s own doc comment above just established.
     * `scheduledDispatchStartedAt` is still set unconditionally here (whether or not a
     * real candidate driver is actually found), so a trip with no online driver nearby
     * still falls through to the real open-list fallback rather than being re-processed
     * every scheduler tick forever; the re-check below (still REQUESTED, still not yet
     * started) guards against double-processing the same trip across overlapping polls.
     */
    @Transactional
    fun activateScheduledDispatchOne(tripId: String, pools: DispatchPools) {
        val trip = rideTripRepository.findById(tripId).orElse(null) ?: return
        if (trip.status != RideTripStatus.REQUESTED || trip.scheduledDispatchStartedAt != null) return
        trip.scheduledDispatchStartedAt = Instant.now()
        rideTripRepository.save(trip)
        dispatchToNextDriver(trip, pools.candidatePool, pools.busyDriverIds)
    }
}

/** Real once-per-tick dispatch candidate pool -- see `RideTripService.computeDispatchPools`'s
 * own doc comment. */
data class DispatchPools(val candidatePool: List<RideDriver>, val busyDriverIds: Set<String>)
