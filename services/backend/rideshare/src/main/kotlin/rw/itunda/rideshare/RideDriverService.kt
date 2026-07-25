package rw.itunda.rideshare

import org.springframework.stereotype.Service
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.RideDriver
import rw.itunda.core.domain.WalletType
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.repository.RideDriverRepository
import rw.itunda.core.repository.WalletRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID

class RideDriverNoWalletException(message: String) : RuntimeException(message)
class RideDriverAlreadyRegisteredException(message: String) : RuntimeException(message)
class RideDriverNotRegisteredException(message: String) : RuntimeException(message)
class InvalidRideDriverLocationException(message: String) : RuntimeException(message)

/**
 * Real driver registration for Kakao T-style ride-hailing -- any itunda user can opt in,
 * same light self-service registration this codebase's other role opt-ins already use
 * (`RiderService.register`, marketplace listing creation). Reuses the driver's own
 * existing MAIN wallet as their payout destination, no new wallet type or external
 * payout rail needed.
 */
@Service
class RideDriverService(
    private val rideDriverRepository: RideDriverRepository,
    private val walletRepository: WalletRepository,
    private val rateLimiter: RateLimiter,
) {
    fun register(userId: String): RideDriver {
        if (rideDriverRepository.findByUserId(userId) != null) {
            throw RideDriverAlreadyRegisteredException("This account is already registered as a driver")
        }
        val wallet = walletRepository.findByUserIdAndType(userId, WalletType.MAIN)
            ?: throw RideDriverNoWalletException("No wallet found for this account")
        return rideDriverRepository.save(RideDriver(id = "ride_driver_${UUID.randomUUID()}", userId = userId, walletId = wallet.id))
    }

    fun getMyDriverProfile(userId: String): RideDriver =
        rideDriverRepository.findByUserId(userId) ?: throw RideDriverNotRegisteredException("This account is not registered as a driver")

    fun setAvailability(userId: String, available: Boolean): RideDriver {
        val driver = rideDriverRepository.findByUserId(userId)
            ?: throw RideDriverNotRegisteredException("This account is not registered as a driver")
        driver.available = available
        return rideDriverRepository.save(driver)
    }

    // Real GPS location update -- same "client owns when to push a fresh reading, this
    // backend never polls a device" model RiderService.updateLocation already
    // establishes, same rate limit for the same reasoning (a real client pushes a
    // coordinate periodically while online/on a trip, comfortably under 20/min).
    fun updateLocation(userId: String, latitude: Double, longitude: Double): RideDriver {
        rateLimiter.checkLimit("rideshare:driver-location:$userId", limit = 20, window = Duration.ofMinutes(1))
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidRideDriverLocationException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        val driver = rideDriverRepository.findByUserId(userId)
            ?: throw RideDriverNotRegisteredException("This account is not registered as a driver")
        driver.currentLatitude = latitude
        driver.currentLongitude = longitude
        driver.locationUpdatedAt = Instant.now()
        return rideDriverRepository.save(driver)
    }
}
