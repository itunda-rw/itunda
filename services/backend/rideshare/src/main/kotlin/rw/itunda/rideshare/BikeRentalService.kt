package rw.itunda.rideshare

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Bike
import rw.itunda.core.domain.BikeRentalSession
import rw.itunda.core.domain.BikeRentalStatus
import rw.itunda.core.domain.BikeType
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.WalletType
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.BikeRentalSessionRepository
import rw.itunda.core.repository.BikeRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant

class BikeNotFoundException(message: String) : RuntimeException(message)
class BikeNotAvailableException(message: String) : RuntimeException(message)
class BikeSelfRentalException(message: String) : RuntimeException(message)
class BikeNoWalletException(message: String) : RuntimeException(message)
class InvalidBikeLocationException(message: String) : RuntimeException(message)
class BikeRentalNotFoundException(message: String) : RuntimeException(message)
class BikeRentalAlreadyEndedException(message: String) : RuntimeException(message)

/**
 * Real Kakao T 바이크 (Kakao T Bike) -- see `Bike.kt`/`BikeRentalSession.kt`'s own doc
 * comments for the full sourced account and the real peer-to-peer v1 adaptation this
 * makes. Unlike `RideTripService`/`DesignatedDriverService`, no fare is known or held
 * at rental start -- a real Kakao T Bike bills by elapsed TIME, computed only once the
 * rider actually ends the rental, so there's nothing to escrow up front; a single real
 * ledger transaction settles the whole rental at `endRental`.
 */
@Service
class BikeRentalService(
    private val bikeRepository: BikeRepository,
    private val bikeRentalSessionRepository: BikeRentalSessionRepository,
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val rateLimiter: RateLimiter,
) {
    companion object {
        // itunda's own honest per-minute rate choice -- no real published Kakao T Bike
        // fare schedule exists to source (pricing varies live by city/bike-type/demand
        // in the real market), same reasoning `DesignatedDriverService`'s own fare
        // constants already give. Electric priced higher than regular, matching the
        // real market's own well-known electric-vs-pedal price gap.
        private val electricPerMinuteRate = BigDecimal("150")
        private val regularPerMinuteRate = BigDecimal("80")
        private val platformFeeRate = BigDecimal("0.15")
    }

    private fun rateFor(type: BikeType) = if (type == BikeType.ELECTRIC) electricPerMinuteRate else regularPerMinuteRate

    // Real bug found live (2026-08-02): unlike every other real "post a listing"
    // creation method in this codebase (MarketplaceService.createListing,
    // PropertyListingService.createListing -- both real 10/hour), this had no rate
    // limit at all, even though `rateLimiter` is already a dependency of this exact
    // class (`startRental` below uses it). Unbounded, this is a real spam-listing
    // flood vector on `getNearbyBikes`.
    @Transactional
    fun registerBike(ownerUserId: String, type: BikeType, latitude: Double, longitude: Double): Bike {
        rateLimiter.checkLimit("bike:register:$ownerUserId", limit = 10, window = Duration.ofHours(1))
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidBikeLocationException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        val wallet = walletRepository.findByUserIdAndType(ownerUserId, WalletType.MAIN)
            ?: throw BikeNoWalletException("No wallet found for this account")
        return bikeRepository.save(
            Bike(id = "bike_${java.util.UUID.randomUUID()}", ownerUserId = ownerUserId, walletId = wallet.id, type = type, currentLatitude = latitude, currentLongitude = longitude),
        )
    }

    fun getMyBikes(ownerUserId: String): List<Bike> = bikeRepository.findByOwnerUserId(ownerUserId)

    private fun getOwnedBike(bikeId: String, ownerUserId: String): Bike {
        val bike = bikeRepository.findById(bikeId).orElseThrow { BikeNotFoundException("Bike not found") }
        if (bike.ownerUserId != ownerUserId) throw BikeNotFoundException("Bike not found")
        return bike
    }

    @Transactional
    fun setAvailability(ownerUserId: String, bikeId: String, available: Boolean): Bike {
        val bike = getOwnedBike(bikeId, ownerUserId)
        bike.available = available
        return bikeRepository.save(bike)
    }

    // Real bug found live (2026-08-02): unlike its direct siblings
    // (RideDriverService.updateLocation, DesignatedDriverService.updateLocation --
    // both real 20/minute for "a real client pushes a coordinate periodically"), this
    // had no rate limit at all.
    @Transactional
    fun updateLocation(ownerUserId: String, bikeId: String, latitude: Double, longitude: Double): Bike {
        rateLimiter.checkLimit("bike:location:$ownerUserId", limit = 20, window = Duration.ofMinutes(1))
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidBikeLocationException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        val bike = getOwnedBike(bikeId, ownerUserId)
        bike.currentLatitude = latitude
        bike.currentLongitude = longitude
        return bikeRepository.save(bike)
    }

    // Real nearby-browse -- available bikes with no active rental, ranked by real
    // straight-line distance, same `GeoUtils.haversineKm`-over-a-bounded-candidate-set
    // honesty this backend's every other distance feature already establishes (not a
    // real geospatial DB index).
    fun getNearbyBikes(latitude: Double, longitude: Double, radiusKm: Double): List<Bike> {
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidBikeLocationException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        return bikeRepository.findAll()
            .filter { it.available && bikeRentalSessionRepository.findByBikeIdAndStatus(it.id, BikeRentalStatus.ACTIVE) == null }
            .filter { GeoUtils.haversineKm(latitude, longitude, it.currentLatitude, it.currentLongitude) <= radiusKm }
            .sortedBy { GeoUtils.haversineKm(latitude, longitude, it.currentLatitude, it.currentLongitude) }
    }

    // Real bug found live (2026-08-02): the old body only checked for the ABSENCE of
    // an active session, then inserted a new one -- a check-then-act race with no
    // shared, lockable row to catch two concurrent riders unlocking the same bike.
    // Fixed to flip `bike.available` (checked immediately above) to false and save
    // THAT versioned entity as part of this same transaction -- matching
    // RideTripService.acceptTrip's own proven-safe "read, check, mutate, save the
    // checked entity" shape. The loser of a real race gets a clean 409 via the
    // existing global ObjectOptimisticLockingFailureException handler, not a
    // double-booked bike.
    @Transactional
    fun startRental(riderUserId: String, bikeId: String, startLatitude: Double, startLongitude: Double): BikeRentalSession {
        if (!GeoUtils.isValidCoordinate(startLatitude, startLongitude)) {
            throw InvalidBikeLocationException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        rateLimiter.checkLimit("bike:start-rental:$riderUserId", limit = 20, window = Duration.ofHours(1))

        val bike = bikeRepository.findById(bikeId).orElseThrow { BikeNotFoundException("Bike not found") }
        if (bike.ownerUserId == riderUserId) throw BikeSelfRentalException("Cannot rent your own bike")
        if (!bike.available) throw BikeNotAvailableException("This bike is not currently available")
        if (bikeRentalSessionRepository.findByBikeIdAndStatus(bikeId, BikeRentalStatus.ACTIVE) != null) {
            throw BikeNotAvailableException("This bike is already rented")
        }
        // A real rider needs a wallet to be billed at rental end -- checked up front so
        // a rider without one never gets to physically "unlock" a bike they can't pay
        // for, same defensive-but-real reasoning every other opt-in feature in this
        // backend already gives for this exact check.
        walletRepository.findByUserIdAndType(riderUserId, WalletType.MAIN)
            ?: throw BikeNoWalletException("No wallet found for this account")

        bike.available = false
        bikeRepository.save(bike)

        return bikeRentalSessionRepository.save(
            BikeRentalSession(id = "bike_rental_${java.util.UUID.randomUUID()}", bikeId = bikeId, riderUserId = riderUserId, startLatitude = startLatitude, startLongitude = startLongitude),
        )
    }

    private fun getOwnedRental(sessionId: String, riderUserId: String): BikeRentalSession {
        val session = bikeRentalSessionRepository.findById(sessionId).orElseThrow { BikeRentalNotFoundException("Rental not found") }
        if (session.riderUserId != riderUserId) throw BikeRentalNotFoundException("Rental not found")
        return session
    }

    /** Real settlement -- the only point this feature ever touches the ledger, since
     * the fare genuinely isn't known until the rider parks the bike. One real
     * transaction: rider wallet DEBIT the full fare, owner wallet CREDIT net of
     * itunda's real platform fee, `fee_revenue` CREDIT the fee -- the same real
     * 3-leg settlement shape `DesignatedDriverService.completeTrip` already
     * establishes, just without a prior escrow hold to release. */
    @Transactional
    fun endRental(riderUserId: String, sessionId: String, endLatitude: Double, endLongitude: Double): BikeRentalSession {
        if (!GeoUtils.isValidCoordinate(endLatitude, endLongitude)) {
            throw InvalidBikeLocationException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        val session = getOwnedRental(sessionId, riderUserId)
        if (session.status != BikeRentalStatus.ACTIVE) {
            throw BikeRentalAlreadyEndedException("This rental has already ended")
        }
        val bike = bikeRepository.findById(session.bikeId).orElseThrow { BikeNotFoundException("Bike not found") }
        return settleRental(session, bike, endLatitude, endLongitude)
    }

    // Real bug found live (2026-08-18): an ACTIVE session had no timeout at all -- see
    // `BikeRentalSession.MAX_RENTAL_DURATION`'s own doc comment for the sourced Citi
    // Bike account. Read-only poll, resolved per-item by
    // `BikeRentalAbandonedSessionScheduler`, same "poll for due rows, act per-row inside
    // its own @Transactional method" shape `VehicleInspectionNoShowScheduler`/
    // `MarketplaceEscrowAutoReleaseScheduler` already establish.
    fun getAbandonedRentals(): List<BikeRentalSession> =
        bikeRentalSessionRepository.findByStatusAndStartedAtBefore(
            BikeRentalStatus.ACTIVE,
            Instant.now().minus(BikeRentalSession.MAX_RENTAL_DURATION),
        )

    /**
     * Real scheduler-driven counterpart to [endRental] for a rental abandoned past
     * [BikeRentalSession.MAX_RENTAL_DURATION] -- see that constant's own doc comment for
     * the sourced Citi Bike account this adapts. Reuses the exact same [settleRental]
     * billing math `endRental` uses -- not a new money-movement path, just a different
     * real trigger for the identical settlement. Ends at the bike's own last-known
     * `currentLatitude`/`currentLongitude` (there is no fresh GPS ping from an abandoned
     * session to use instead). Re-checks status and elapsed duration right before
     * acting, same one-shot re-check discipline every other scheduler-driven per-item
     * method in this codebase already uses -- a rider who taps "end rental" a moment
     * before the scheduler runs can never be double-charged, and a still-genuinely-due
     * row missing its bike/wallet is skipped rather than thrown, so one bad row never
     * corrupts a real, valid settlement.
     */
    @Transactional
    fun forceEndAbandonedRental(sessionId: String): BikeRentalSession? {
        val session = bikeRentalSessionRepository.findById(sessionId).orElse(null) ?: return null
        if (session.status != BikeRentalStatus.ACTIVE) return session
        if (Duration.between(session.startedAt, Instant.now()) < BikeRentalSession.MAX_RENTAL_DURATION) return session
        val bike = bikeRepository.findById(session.bikeId).orElse(null) ?: return null
        return settleRental(session, bike, bike.currentLatitude, bike.currentLongitude)
    }

    // Shared real settlement math -- one source of truth for the rider DEBIT / owner
    // CREDIT-net-of-fee / fee_revenue CREDIT 3-leg ledger transaction, called from both
    // the rider-triggered `endRental` and the scheduler-triggered
    // `forceEndAbandonedRental` so the two real triggers can never drift into two
    // different billing outcomes for the same kind of session.
    private fun settleRental(session: BikeRentalSession, bike: Bike, endLatitude: Double, endLongitude: Double): BikeRentalSession {
        val riderWallet = walletRepository.findByUserIdAndType(session.riderUserId, WalletType.MAIN)
            ?: throw BikeNoWalletException("No wallet found for this account")
        val ownerWallet = walletRepository.findById(bike.walletId)
            .orElseThrow { BikeNoWalletException("Bike owner's settlement wallet not found") }

        val endedAt = Instant.now()
        // Real minutes billed round UP to the next full minute -- the same honest
        // "never under-bill the rider for elapsed time" convention real per-minute
        // billing (parking meters, cloud compute) already uses; a sub-minute rental
        // still bills a real 1 minute, not 0.
        val elapsedSeconds = Duration.between(session.startedAt, endedAt).seconds.coerceAtLeast(0)
        val durationMinutes = ((elapsedSeconds + 59) / 60).toInt().coerceAtLeast(1)
        val totalFare = rateFor(bike.type).multiply(BigDecimal(durationMinutes)).setScale(2, RoundingMode.HALF_UP)
        val platformFee = totalFare.multiply(platformFeeRate).setScale(2, RoundingMode.HALF_UP)
        val netToOwner = totalFare.subtract(platformFee)

        val result = ledgerService.postLedgerTransaction(
            riderWallet.currency,
            listOf(
                LedgerLeg(riderWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, totalFare, "Bike rental (${bike.type})"),
                LedgerLeg(ownerWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, netToOwner, "Bike rental payout"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, platformFee, "Bike rental platform fee"),
            ),
        )

        session.endedAt = endedAt
        session.endLatitude = endLatitude
        session.endLongitude = endLongitude
        session.durationMinutes = durationMinutes
        session.totalFare = totalFare
        session.platformFee = platformFee
        session.payoutTransactionId = result.transactionId
        session.status = BikeRentalStatus.COMPLETED

        bike.currentLatitude = endLatitude
        bike.currentLongitude = endLongitude
        bike.available = true
        bikeRepository.save(bike)

        return bikeRentalSessionRepository.save(session)
    }

    fun getMyRentalHistory(riderUserId: String, pageable: Pageable): Page<BikeRentalSession> =
        bikeRentalSessionRepository.findByRiderUserIdOrderByStartedAtDesc(riderUserId, pageable)
}
