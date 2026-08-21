package rw.itunda.eats

import org.springframework.stereotype.Service
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Rider
import rw.itunda.core.domain.AccountType
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.repository.RiderRepository
import rw.itunda.core.repository.AccountRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID

class RiderNoAccountException(message: String) : RuntimeException(message)
class RiderAlreadyRegisteredException(message: String) : RuntimeException(message)
class RiderNotRegisteredException(message: String) : RuntimeException(message)
class InvalidRiderLocationException(message: String) : RuntimeException(message)

/**
 * Real rider registration for Coupang Eats-style delivery -- any itunda user can opt in,
 * no separate onboarding/KYC gate beyond the account they already have (matching how
 * light this session's other self-service registrations are, e.g. marketplace listing
 * creation). Reuses the rider's own existing MAIN account as their payout destination --
 * the same real WALLET-to-WALLET disbursement precedent `PayrollService` already
 * established, no new account type or external payout rail needed.
 */
@Service
class RiderService(
    private val riderRepository: RiderRepository,
    private val accountRepository: AccountRepository,
    private val rateLimiter: RateLimiter,
) {
    fun register(userId: String): Rider {
        if (riderRepository.findByUserId(userId) != null) {
            throw RiderAlreadyRegisteredException("This account is already registered as a rider")
        }
        val account = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw RiderNoAccountException("No account found for this account")
        return riderRepository.save(Rider(id = "rider_${UUID.randomUUID()}", userId = userId, accountId = account.id))
    }

    fun getMyRiderProfile(userId: String): Rider =
        riderRepository.findByUserId(userId) ?: throw RiderNotRegisteredException("This account is not registered as a rider")

    fun setAvailability(userId: String, available: Boolean): Rider {
        val rider = riderRepository.findByUserId(userId)
            ?: throw RiderNotRegisteredException("This account is not registered as a rider")
        rider.available = available
        return riderRepository.save(rider)
    }

    // Real GPS location update (2026-07-19) -- closes the last piece needed for real
    // proximity-based delivery ranking (see EatsOrderService.getAvailableDeliveries).
    // A client is expected to call this periodically while the rider is online, the same
    // "the client owns when to push a fresh reading" model every real ride/delivery app
    // uses -- this backend never polls a device for its location.
    //
    // Real rate limit added 2026-07-20, found in a security sweep after this endpoint
    // got its first actual callers (the new standalone rider apps push a real coordinate
    // every 15s during an active delivery, plus once on going online) -- until then it
    // had zero client integration and so had never been sanity-checked the way every
    // other real, exercised endpoint in this codebase routinely is. 20/min comfortably
    // covers the real client's own cadence (~4/min) with headroom, while still bounding
    // an abusive caller from writing to this row unboundedly.
    fun updateLocation(userId: String, latitude: Double, longitude: Double): Rider {
        rateLimiter.checkLimit("eats:rider-location:$userId", limit = 20, window = Duration.ofMinutes(1))
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidRiderLocationException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        val rider = riderRepository.findByUserId(userId)
            ?: throw RiderNotRegisteredException("This account is not registered as a rider")
        rider.currentLatitude = latitude
        rider.currentLongitude = longitude
        rider.locationUpdatedAt = Instant.now()
        return riderRepository.save(rider)
    }
}
