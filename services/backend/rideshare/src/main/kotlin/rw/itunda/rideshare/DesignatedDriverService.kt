package rw.itunda.rideshare

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.DesignatedDriver
import rw.itunda.core.domain.DesignatedDriverTrip
import rw.itunda.core.domain.DesignatedDriverTripStatus
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.WalletType
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.DesignatedDriverRepository
import rw.itunda.core.repository.DesignatedDriverTripRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.util.UUID

class DesignatedDriverNoWalletException(message: String) : RuntimeException(message)
class DesignatedDriverAlreadyRegisteredException(message: String) : RuntimeException(message)
class DesignatedDriverNotRegisteredException(message: String) : RuntimeException(message)
class InvalidDesignatedDriverLocationException(message: String) : RuntimeException(message)
class DesignatedDriverSelfTripException(message: String) : RuntimeException(message)
class DesignatedDriverTripNotFoundException(message: String) : RuntimeException(message)
class DesignatedDriverTripAlreadyClaimedException(message: String) : RuntimeException(message)
class InvalidDesignatedDriverTripStatusTransitionException(message: String) : RuntimeException(message)

/**
 * Real Kakao T 대리운전 (designated driver) -- see `DesignatedDriver.kt`/
 * `DesignatedDriverTrip.kt`'s own doc comments for the full sourced account and the
 * real escrow/payout shape this reuses from `RideTripService`.
 *
 * **Honest v1 scope**: no ranked auto-dispatch (unlike `RideTripService`'s own
 * acceptance-rate-filtered, distance-ranked candidate pool) -- a real open-list claim
 * instead, the same simpler shape `VehicleInspectionService` already establishes:
 * every REQUESTED trip is visible to every online driver, first to accept gets it.
 * Fits this feature's real scale better than `RideTripService`'s dispatch machinery
 * would (대리운전 in Korea is typically requested with more lead time than a taxi
 * hail, so a 15-second exclusive-offer window isn't the same real fit).
 */
@Service
class DesignatedDriverService(
    private val designatedDriverRepository: DesignatedDriverRepository,
    private val designatedDriverTripRepository: DesignatedDriverTripRepository,
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val transactionRepository: TransactionRepository,
    private val rateLimiter: RateLimiter,
) {
    companion object {
        // itunda's own honest fare-structure choice, same reasoning RideTripService's
        // own doc comment gives for its fare constants -- no real 대리운전 fare
        // schedule to source (pricing varies live by demand/distance/time-of-day in
        // the real market), reuses the same real per-km rate RideTripService already
        // establishes rather than inventing a second one, with a higher base fare
        // reflecting a real designated-driver call-out's higher flag-fall cost.
        private val platformFeeRate = BigDecimal("0.015")
        private val baseFare = BigDecimal("3000")
        private val perKmRate = BigDecimal("250")
        private val minFare = BigDecimal("4000")

        // Real Uber cancellation-fee policy
        // (help.uber.com/riders/article/cancellation-fees-explained), ported from
        // `RideTripService`'s own identical constants -- see that class's own doc
        // comment for the full sourced account. A REQUESTED trip (no driver has
        // committed yet) always refunds in full, unchanged. An ACCEPTED trip cancelled
        // within this grace period of driverAcceptedAt also refunds in full; past it,
        // CANCELLATION_FEE is carved out of the refund and paid straight to the
        // driver's settlement wallet to compensate them for committing to the job.
        val CANCELLATION_FEE_GRACE_PERIOD: Duration = Duration.ofMinutes(2)
        val CANCELLATION_FEE = baseFare
    }

    /** Any itunda user can register, no admin approval gate -- same real light
     * self-service opt-in `RideDriverService.register`/`VehicleInspectionService.
     * registerAsMechanic` already establish for this codebase's other opt-in service
     * roles. */
    @Transactional
    fun register(userId: String, licenseNumber: String): DesignatedDriver {
        if (designatedDriverRepository.findByUserId(userId) != null) {
            throw DesignatedDriverAlreadyRegisteredException("This account is already registered as a designated driver")
        }
        val trimmedLicense = licenseNumber.trim().ifEmpty { throw InvalidDesignatedDriverLocationException("License number is required") }.take(100)
        val wallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN)
            ?: throw DesignatedDriverNoWalletException("No wallet found for this account")
        return designatedDriverRepository.save(
            DesignatedDriver(id = "designated_driver_${UUID.randomUUID()}", userId = userId, walletId = wallet.id, licenseNumber = trimmedLicense),
        )
    }

    fun getMyDriverProfile(userId: String): DesignatedDriver? = designatedDriverRepository.findByUserId(userId)

    private fun getMyDriver(userId: String) =
        designatedDriverRepository.findByUserId(userId)
            ?: throw DesignatedDriverNotRegisteredException("This account is not registered as a designated driver")

    @Transactional
    fun setAvailability(userId: String, available: Boolean): DesignatedDriver {
        val driver = getMyDriver(userId)
        driver.available = available
        return designatedDriverRepository.save(driver)
    }

    // Same "client owns when to push a fresh reading" model RideDriverService.
    // updateLocation already establishes, same rate limit for the same reasoning.
    @Transactional
    fun updateLocation(userId: String, latitude: Double, longitude: Double): DesignatedDriver {
        rateLimiter.checkLimit("designated-driver:location:$userId", limit = 20, window = Duration.ofMinutes(1))
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidDesignatedDriverLocationException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        val driver = getMyDriver(userId)
        driver.currentLatitude = latitude
        driver.currentLongitude = longitude
        return designatedDriverRepository.save(driver)
    }

    /** Real escrow hold -- the customer's real fare leaves their wallet right now,
     * held until the trip completes, same "hold, don't move directly" shape
     * `RideTripService.requestTrip` already establishes. */
    @Transactional
    fun requestTrip(
        customerId: String,
        pickupAddress: String,
        pickupLatitude: Double,
        pickupLongitude: Double,
        dropoffAddress: String,
        dropoffLatitude: Double,
        dropoffLongitude: Double,
        vehicleMake: String,
        vehicleModel: String,
        vehiclePlate: String,
    ): DesignatedDriverTrip {
        if (!GeoUtils.isValidCoordinate(pickupLatitude, pickupLongitude) || !GeoUtils.isValidCoordinate(dropoffLatitude, dropoffLongitude)) {
            throw InvalidDesignatedDriverLocationException("Invalid pickup or dropoff coordinate")
        }
        val trimmedPickup = pickupAddress.trim().ifEmpty { throw InvalidDesignatedDriverLocationException("Pickup address is required") }.take(500)
        val trimmedDropoff = dropoffAddress.trim().ifEmpty { throw InvalidDesignatedDriverLocationException("Dropoff address is required") }.take(500)
        val trimmedMake = vehicleMake.trim().ifEmpty { throw InvalidDesignatedDriverLocationException("Vehicle make is required") }.take(100)
        val trimmedModel = vehicleModel.trim().ifEmpty { throw InvalidDesignatedDriverLocationException("Vehicle model is required") }.take(100)
        val trimmedPlate = vehiclePlate.trim().ifEmpty { throw InvalidDesignatedDriverLocationException("Vehicle plate is required") }.take(20)

        rateLimiter.checkLimit("designated-driver:request:$customerId", limit = 20, window = Duration.ofHours(1))

        val customerWallet = walletRepository.findByUserIdAndType(customerId, WalletType.MAIN)
            ?: throw DesignatedDriverNoWalletException("No wallet found for this account")

        val distanceKm = BigDecimal(GeoUtils.haversineKm(pickupLatitude, pickupLongitude, dropoffLatitude, dropoffLongitude)).setScale(3, RoundingMode.HALF_UP)
        val fare = baseFare.add(perKmRate.multiply(distanceKm)).setScale(2, RoundingMode.HALF_UP).max(minFare)
        val platformFee = fare.multiply(platformFeeRate).setScale(2, RoundingMode.HALF_UP)

        if (customerWallet.availableBalance < fare) {
            throw InsufficientFundsException("Insufficient available balance for this trip")
        }

        val holdResult = ledgerService.postLedgerTransaction(
            customerWallet.currency,
            listOf(
                LedgerLeg(customerWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, fare, "Designated driver requested"),
                LedgerLeg("designated_driver_holding", LedgerAccountType.DESIGNATED_DRIVER_HOLDING, LedgerDirection.CREDIT, fare, "Designated driver fare held in escrow"),
            ),
        )
        val holdTransaction = Transaction(
            id = holdResult.transactionId,
            referenceNumber = "DDRIVE${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
            senderId = customerId,
            recipientId = customerId,
            fromWalletId = customerWallet.id,
            amount = fare,
            fee = platformFee,
            currency = customerWallet.currency,
            type = TransactionType.TRANSFER,
            status = TransactionStatus.COMPLETED,
            description = "Designated driver requested",
            completedAt = Instant.now(),
        )
        transactionRepository.save(holdTransaction)

        return designatedDriverTripRepository.save(
            DesignatedDriverTrip(
                id = "designated_trip_${UUID.randomUUID()}", customerId = customerId, pickupAddress = trimmedPickup,
                pickupLatitude = pickupLatitude, pickupLongitude = pickupLongitude, dropoffAddress = trimmedDropoff,
                dropoffLatitude = dropoffLatitude, dropoffLongitude = dropoffLongitude, vehicleMake = trimmedMake,
                vehicleModel = trimmedModel, vehiclePlate = trimmedPlate, distanceKm = distanceKm, fare = fare,
                platformFee = platformFee, holdTransactionId = holdTransaction.id,
            ),
        )
    }

    // Real open-list -- every REQUESTED trip with no driver assigned yet, same v1
    // scope `VehicleInspectionService.getAvailableMechanics`-adjacent simplicity this
    // class's own doc comment names.
    fun getAvailableTrips(): List<DesignatedDriverTrip> =
        designatedDriverTripRepository.findAll().filter { it.status == DesignatedDriverTripStatus.REQUESTED && it.driverId == null }

    @Transactional
    fun acceptTrip(driverUserId: String, tripId: String): DesignatedDriverTrip {
        val driver = getMyDriver(driverUserId)
        val trip = designatedDriverTripRepository.findById(tripId).orElseThrow { DesignatedDriverTripNotFoundException("Trip not found") }
        if (trip.status != DesignatedDriverTripStatus.REQUESTED || trip.driverId != null) {
            throw DesignatedDriverTripAlreadyClaimedException("This trip is no longer available")
        }
        if (trip.customerId == driverUserId) throw DesignatedDriverSelfTripException("Cannot accept your own trip")

        trip.driverId = driver.id
        trip.status = DesignatedDriverTripStatus.ACCEPTED
        trip.driverAcceptedAt = Instant.now()
        trip.updatedAt = Instant.now()
        return designatedDriverTripRepository.save(trip)
    }

    private fun getOwnedTrip(tripId: String, driverId: String): DesignatedDriverTrip {
        val trip = designatedDriverTripRepository.findById(tripId).orElseThrow { DesignatedDriverTripNotFoundException("Trip not found") }
        if (trip.driverId != driverId) {
            throw DesignatedDriverTripNotFoundException("Trip not found")
        }
        return trip
    }

    @Transactional
    fun startDriving(driverUserId: String, tripId: String): DesignatedDriverTrip {
        val driver = getMyDriver(driverUserId)
        val trip = getOwnedTrip(tripId, driver.id)
        if (trip.status != DesignatedDriverTripStatus.ACCEPTED) {
            throw InvalidDesignatedDriverTripStatusTransitionException("Only an ACCEPTED trip can start driving")
        }
        trip.status = DesignatedDriverTripStatus.DRIVING
        trip.updatedAt = Instant.now()
        return designatedDriverTripRepository.save(trip)
    }

    /** Real release -- the driver delivered the customer home, paid out of holding
     * net of itunda's real platform fee, matching `RideTripService.completeTrip`'s
     * own release-on-completion shape.
     *
     * Real fix (concurrency audit, 2026-08-21): reads the trip locked (not via the
     * shared `getOwnedTrip`, which stays a plain unlocked ownership check for
     * non-money-moving transitions like `startDriving`) -- this method checks
     * `status == DRIVING`, posts real ledger payout money, then writes
     * `status = COMPLETED`, so a second concurrent completeTrip call for the same
     * trip (a real double-tap, or a client retry) must block, re-read fresh state,
     * and correctly no-op through the status check before ever touching the ledger
     * again. Same shape `GroupEatsOrderService.finalizeOrder`/
     * `EatsOrderService.tipRider` already fixed. */
    @Transactional
    fun completeTrip(driverUserId: String, tripId: String): DesignatedDriverTrip {
        val driver = getMyDriver(driverUserId)
        val trip = designatedDriverTripRepository.findByIdForUpdate(tripId).orElseThrow { DesignatedDriverTripNotFoundException("Trip not found") }
        if (trip.driverId != driver.id) {
            throw DesignatedDriverTripNotFoundException("Trip not found")
        }
        if (trip.status != DesignatedDriverTripStatus.DRIVING) {
            throw InvalidDesignatedDriverTripStatusTransitionException("Only a DRIVING trip can be completed")
        }
        val driverWallet = walletRepository.findById(driver.walletId).orElseThrow { DesignatedDriverNoWalletException("Driver settlement wallet not found") }
        val netToDriver = trip.fare.subtract(trip.platformFee)

        val result = ledgerService.postLedgerTransaction(
            driverWallet.currency,
            listOf(
                LedgerLeg("designated_driver_holding", LedgerAccountType.DESIGNATED_DRIVER_HOLDING, LedgerDirection.DEBIT, trip.fare, "Designated driver fare released"),
                LedgerLeg(driverWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, netToDriver, "Designated driver fare payout"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, trip.platformFee, "Designated driver platform fee"),
            ),
        )
        trip.status = DesignatedDriverTripStatus.COMPLETED
        trip.payoutTransactionId = result.transactionId
        trip.updatedAt = Instant.now()
        return designatedDriverTripRepository.save(trip)
    }

    /** Real gap closed 2026-08-18: previously only a still-REQUESTED trip (no driver
     * assigned yet) could be cancelled at all -- once a driver accepted, there was no
     * cancel path whatsoever, so a driver who accepted and then simply never started
     * driving left the customer's fare permanently stuck in
     * `designated_driver_holding` with zero recourse (no admin controller exists for
     * this feature either). Now mirrors `RideTripService.cancelTrip`'s own real Uber
     * cancellation-fee policy exactly: a REQUESTED trip always refunds in full; an
     * ACCEPTED trip cancelled within `CANCELLATION_FEE_GRACE_PERIOD` of
     * `driverAcceptedAt` also refunds in full, past that window `CANCELLATION_FEE` is
     * carved out and paid to the driver for having already committed. A DRIVING trip
     * (driver has already arrived and is driving the customer's car) still cannot be
     * cancelled -- same real-world reasoning `RideTripService` never allows an
     * IN_PROGRESS ride to be cancelled either. */
    @Transactional
    fun cancelTrip(customerId: String, tripId: String): DesignatedDriverTrip {
        val trip = designatedDriverTripRepository.findById(tripId).orElseThrow { DesignatedDriverTripNotFoundException("Trip not found") }
        if (trip.customerId != customerId) {
            throw DesignatedDriverTripNotFoundException("Trip not found")
        }
        if (trip.status != DesignatedDriverTripStatus.REQUESTED && trip.status != DesignatedDriverTripStatus.ACCEPTED) {
            throw InvalidDesignatedDriverTripStatusTransitionException("Only a REQUESTED or ACCEPTED trip can be cancelled -- this one is already ${trip.status}")
        }
        val customerWallet = walletRepository.findByUserIdAndType(customerId, WalletType.MAIN)
            ?: throw DesignatedDriverNoWalletException("No wallet found for this account")

        val acceptedAt = trip.driverAcceptedAt
        val withinGracePeriod = acceptedAt == null || !Instant.now().isAfter(acceptedAt.plus(CANCELLATION_FEE_GRACE_PERIOD))
        val cancellationFee = if (trip.status == DesignatedDriverTripStatus.ACCEPTED && !withinGracePeriod) {
            CANCELLATION_FEE.min(trip.fare)
        } else {
            BigDecimal.ZERO
        }
        val refundAmount = trip.fare.subtract(cancellationFee)

        val driverWallet = if (cancellationFee > BigDecimal.ZERO) {
            trip.driverId?.let { designatedDriverRepository.findById(it).orElse(null) }
                ?.let { walletRepository.findById(it.walletId).orElse(null) }
        } else {
            null
        }

        val legs = mutableListOf(
            LedgerLeg("designated_driver_holding", LedgerAccountType.DESIGNATED_DRIVER_HOLDING, LedgerDirection.DEBIT, trip.fare, "Designated driver fare refunded"),
            LedgerLeg(customerWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, refundAmount, "Designated driver cancelled -- refund"),
        )
        if (cancellationFee > BigDecimal.ZERO && driverWallet != null) {
            legs.add(LedgerLeg(driverWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, cancellationFee, "Designated driver cancellation fee"))
        } else if (cancellationFee > BigDecimal.ZERO) {
            // Driver's own settlement wallet is somehow gone -- never strand escrow
            // money mid-refund; fall back to refunding the customer in full rather
            // than leaving the fee portion unaccounted for, same fallback
            // RideTripService.cancelTrip already establishes.
            legs[1] = LedgerLeg(customerWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, trip.fare, "Designated driver cancelled -- refund")
        }
        val result = ledgerService.postLedgerTransaction(customerWallet.currency, legs)

        trip.status = DesignatedDriverTripStatus.CANCELLED
        trip.refundTransactionId = result.transactionId
        trip.updatedAt = Instant.now()
        return designatedDriverTripRepository.save(trip)
    }

    fun getMyTrips(customerId: String, pageable: Pageable): Page<DesignatedDriverTrip> =
        designatedDriverTripRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)

    fun getMyDriverTrips(driverUserId: String, pageable: Pageable): Page<DesignatedDriverTrip> {
        val driver = getMyDriver(driverUserId)
        return designatedDriverTripRepository.findByDriverIdOrderByCreatedAtDesc(driver.id, pageable)
    }
}
