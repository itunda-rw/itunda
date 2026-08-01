package rw.itunda.rideshare

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.ParkingSession
import rw.itunda.core.domain.ParkingSessionStatus
import rw.itunda.core.domain.ParkingSpot
import rw.itunda.core.domain.WalletType
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.ParkingSessionRepository
import rw.itunda.core.repository.ParkingSpotRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.util.UUID

class ParkingSpotNotFoundException(message: String) : RuntimeException(message)
class ParkingSpotNotAvailableException(message: String) : RuntimeException(message)
class ParkingSelfRentalException(message: String) : RuntimeException(message)
class ParkingNoWalletException(message: String) : RuntimeException(message)
class InvalidParkingLocationException(message: String) : RuntimeException(message)
class ParkingSessionNotFoundException(message: String) : RuntimeException(message)
class ParkingSessionAlreadyEndedException(message: String) : RuntimeException(message)

/**
 * Real Kakao T 주차 (Kakao T Parking) -- see `ParkingSpot.kt`/`ParkingSession.kt`'s own
 * doc comments for the full sourced account and the real peer-to-peer v1 adaptation
 * this makes. Same shape `BikeRentalService` already establishes: no fare is known or
 * held at session start -- a real Kakao T Parking session bills by elapsed TIME,
 * computed only once the renter actually checks out, so there's nothing to escrow up
 * front; a single real ledger transaction settles the whole session at `endSession`.
 * Billed hourly (not per-minute like Bike) since parking sessions genuinely run
 * longer, rounding UP to the next full hour -- the same honest "never under-bill for
 * elapsed time" convention real hourly parking billing already uses.
 */
@Service
class ParkingService(
    private val parkingSpotRepository: ParkingSpotRepository,
    private val parkingSessionRepository: ParkingSessionRepository,
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val rateLimiter: RateLimiter,
) {
    companion object {
        // itunda's own honest per-hour rate bound -- no real published Kakao T Parking
        // fare schedule exists to source (pricing varies live by lot/city/demand in the
        // real market), same reasoning BikeRentalService's own fare constants already
        // give. Owner sets their own rate within a sane bound, since a real private
        // parking spot's value varies far more by location than a pooled bike does.
        val minHourlyRate: BigDecimal = BigDecimal("200")
        val maxHourlyRate: BigDecimal = BigDecimal("5000")
        private val platformFeeRate = BigDecimal("0.15")
    }

    @Transactional
    fun registerSpot(ownerUserId: String, address: String, latitude: Double, longitude: Double, hourlyRate: BigDecimal): ParkingSpot {
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidParkingLocationException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        val trimmedAddress = address.trim().ifEmpty { throw InvalidParkingLocationException("Address is required") }.take(500)
        if (hourlyRate < minHourlyRate || hourlyRate > maxHourlyRate) {
            throw InvalidParkingLocationException("Hourly rate must be between $minHourlyRate and $maxHourlyRate")
        }
        val wallet = walletRepository.findByUserIdAndType(ownerUserId, WalletType.MAIN)
            ?: throw ParkingNoWalletException("No wallet found for this account")
        return parkingSpotRepository.save(
            ParkingSpot(
                id = "parking_spot_${UUID.randomUUID()}", ownerUserId = ownerUserId, walletId = wallet.id,
                address = trimmedAddress, latitude = latitude, longitude = longitude, hourlyRate = hourlyRate,
            ),
        )
    }

    fun getMySpots(ownerUserId: String): List<ParkingSpot> = parkingSpotRepository.findByOwnerUserId(ownerUserId)

    private fun getOwnedSpot(spotId: String, ownerUserId: String): ParkingSpot {
        val spot = parkingSpotRepository.findById(spotId).orElseThrow { ParkingSpotNotFoundException("Parking spot not found") }
        if (spot.ownerUserId != ownerUserId) throw ParkingSpotNotFoundException("Parking spot not found")
        return spot
    }

    @Transactional
    fun setAvailability(ownerUserId: String, spotId: String, available: Boolean): ParkingSpot {
        val spot = getOwnedSpot(spotId, ownerUserId)
        spot.available = available
        return parkingSpotRepository.save(spot)
    }

    // Real nearby-browse -- available spots with no active session, ranked by real
    // straight-line distance, same GeoUtils.haversineKm-over-a-bounded-candidate-set
    // honesty this backend's every other distance feature already establishes.
    fun getNearbySpots(latitude: Double, longitude: Double, radiusKm: Double): List<ParkingSpot> {
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidParkingLocationException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        return parkingSpotRepository.findAll()
            .filter { it.available && parkingSessionRepository.findBySpotIdAndStatus(it.id, ParkingSessionStatus.ACTIVE) == null }
            .filter { GeoUtils.haversineKm(latitude, longitude, it.latitude, it.longitude) <= radiusKm }
            .sortedBy { GeoUtils.haversineKm(latitude, longitude, it.latitude, it.longitude) }
    }

    @Transactional
    fun startSession(renterUserId: String, spotId: String): ParkingSession {
        rateLimiter.checkLimit("parking:start-session:$renterUserId", limit = 20, window = Duration.ofHours(1))

        val spot = parkingSpotRepository.findById(spotId).orElseThrow { ParkingSpotNotFoundException("Parking spot not found") }
        if (spot.ownerUserId == renterUserId) throw ParkingSelfRentalException("Cannot rent your own parking spot")
        if (!spot.available) throw ParkingSpotNotAvailableException("This parking spot is not currently available")
        if (parkingSessionRepository.findBySpotIdAndStatus(spotId, ParkingSessionStatus.ACTIVE) != null) {
            throw ParkingSpotNotAvailableException("This parking spot is already occupied")
        }
        // A real renter needs a wallet to be billed at checkout -- checked up front so
        // a renter without one never gets to "check in" to a spot they can't pay for,
        // same defensive-but-real reasoning every other opt-in feature in this backend
        // already gives for this exact check.
        walletRepository.findByUserIdAndType(renterUserId, WalletType.MAIN)
            ?: throw ParkingNoWalletException("No wallet found for this account")

        return parkingSessionRepository.save(
            ParkingSession(id = "parking_session_${UUID.randomUUID()}", spotId = spotId, renterUserId = renterUserId),
        )
    }

    private fun getOwnedSession(sessionId: String, renterUserId: String): ParkingSession {
        val session = parkingSessionRepository.findById(sessionId).orElseThrow { ParkingSessionNotFoundException("Parking session not found") }
        if (session.renterUserId != renterUserId) throw ParkingSessionNotFoundException("Parking session not found")
        return session
    }

    /** Real settlement -- the only point this feature ever touches the ledger, since
     * the fare genuinely isn't known until the renter checks out. One real
     * transaction: renter wallet DEBIT the full fare, owner wallet CREDIT net of
     * itunda's real platform fee, `fee_revenue` CREDIT the fee -- the same real
     * 3-leg settlement shape `BikeRentalService.endRental` already establishes. */
    @Transactional
    fun endSession(renterUserId: String, sessionId: String): ParkingSession {
        val session = getOwnedSession(sessionId, renterUserId)
        if (session.status != ParkingSessionStatus.ACTIVE) {
            throw ParkingSessionAlreadyEndedException("This parking session has already ended")
        }
        val spot = parkingSpotRepository.findById(session.spotId).orElseThrow { ParkingSpotNotFoundException("Parking spot not found") }

        val renterWallet = walletRepository.findByUserIdAndType(renterUserId, WalletType.MAIN)
            ?: throw ParkingNoWalletException("No wallet found for this account")
        val ownerWallet = walletRepository.findById(spot.walletId)
            .orElseThrow { ParkingNoWalletException("Parking spot owner's settlement wallet not found") }

        val endedAt = Instant.now()
        val elapsedSeconds = Duration.between(session.startedAt, endedAt).seconds.coerceAtLeast(0)
        val durationMinutes = ((elapsedSeconds + 59) / 60).toInt().coerceAtLeast(1)
        // Real hourly billing rounds UP to the next full hour -- a real 61-minute
        // session bills 2 hours, not 1 hour + a fractional remainder, matching real
        // hourly parking-lot billing practice (and BikeRentalService's own identical
        // "round up, never under-bill" reasoning, just at hour instead of minute
        // granularity).
        val billedHours = ((durationMinutes + 59) / 60).coerceAtLeast(1)
        val totalFare = spot.hourlyRate.multiply(BigDecimal(billedHours)).setScale(2, RoundingMode.HALF_UP)
        val platformFee = totalFare.multiply(platformFeeRate).setScale(2, RoundingMode.HALF_UP)
        val netToOwner = totalFare.subtract(platformFee)

        val result = ledgerService.postLedgerTransaction(
            renterWallet.currency,
            listOf(
                LedgerLeg(renterWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, totalFare, "Parking (${spot.address})"),
                LedgerLeg(ownerWallet.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, netToOwner, "Parking spot payout"),
                LedgerLeg("fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, platformFee, "Parking platform fee"),
            ),
        )

        session.endedAt = endedAt
        session.durationMinutes = durationMinutes
        session.totalFare = totalFare
        session.platformFee = platformFee
        session.payoutTransactionId = result.transactionId
        session.status = ParkingSessionStatus.COMPLETED

        return parkingSessionRepository.save(session)
    }

    fun getMyRentalHistory(renterUserId: String, pageable: Pageable): Page<ParkingSession> =
        parkingSessionRepository.findByRenterUserIdOrderByStartedAtDesc(renterUserId, pageable)
}
