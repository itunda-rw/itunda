package rw.itunda.rideshare

import org.springframework.stereotype.Service
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.RideDriver
import rw.itunda.core.domain.AccountType
import rw.itunda.core.geo.GeoUtils
import rw.itunda.core.repository.RideDriverRepository
import rw.itunda.core.repository.AccountRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID

class RideDriverNoAccountException(message: String) : RuntimeException(message)
class RideDriverAlreadyRegisteredException(message: String) : RuntimeException(message)
class RideDriverNotRegisteredException(message: String) : RuntimeException(message)
class InvalidRideDriverLocationException(message: String) : RuntimeException(message)
class InvalidRideDriverLicenseException(message: String) : RuntimeException(message)
class DestinationFilterLimitExceededException(message: String) : RuntimeException(message)

/**
 * Real driver registration for Kakao T-style ride-hailing -- any itunda user can opt in,
 * same light self-service registration this codebase's other role opt-ins already use
 * (`RiderService.register`, marketplace listing creation). Reuses the driver's own
 * existing MAIN account as their payout destination, no new account type or external
 * payout rail needed.
 *
 * Real gap found live (2026-08-31, market-readiness audit): this, the biggest and most
 * central driver-role registration in the backend (dispatched to strangers' real trip
 * requests, unlike a single booked designated-driver job), had zero identity/license
 * info at all -- a smaller, adjacent feature in this exact module,
 * `DesignatedDriverService.register`, already required a real license number. See
 * `RideDriver.licenseNumber`'s own doc comment for the honest scope: a self-declared
 * informational text field, not a real license-verification gate this backend has no
 * path to check.
 */
@Service
class RideDriverService(
    private val rideDriverRepository: RideDriverRepository,
    private val accountRepository: AccountRepository,
    private val rateLimiter: RateLimiter,
) {
    fun register(userId: String, licenseNumber: String): RideDriver {
        if (rideDriverRepository.findByUserId(userId) != null) {
            throw RideDriverAlreadyRegisteredException("This account is already registered as a driver")
        }
        val trimmedLicense = licenseNumber.trim().ifEmpty { throw InvalidRideDriverLicenseException("License number is required") }.take(100)
        val account = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw RideDriverNoAccountException("No account found for this account")
        return rideDriverRepository.save(
            RideDriver(id = "ride_driver_${UUID.randomUUID()}", userId = userId, accountId = account.id, licenseNumber = trimmedLicense),
        )
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

    // Real Uber "Destination Filter" (help.uber.com/en-GB/driving-and-delivering/
    // article/driver-destination-filter) -- Uber's own real published limit is "up to
    // twice a day", resetting at midnight local; itunda's own honest choice of "local"
    // is Africa/Kigali, same real-timezone convention Merchant.isClosedToday() already
    // established. See RideTripService.rankNearbyDrivers's own doc comment for how a
    // driver with an active filter is actually preferred in dispatch.
    private val maxDestinationUsesPerDay = 2

    fun setDestination(userId: String, latitude: Double, longitude: Double): RideDriver {
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw InvalidRideDriverLocationException("Latitude must be between -90 and 90, longitude between -180 and 180")
        }
        val driver = rideDriverRepository.findByUserId(userId)
            ?: throw RideDriverNotRegisteredException("This account is not registered as a driver")
        val today = java.time.LocalDate.now(java.time.ZoneId.of("Africa/Kigali"))
        if (driver.destinationUsesResetDate != today) {
            driver.destinationUsesToday = 0
            driver.destinationUsesResetDate = today
        }
        if (driver.destinationUsesToday >= maxDestinationUsesPerDay) {
            throw DestinationFilterLimitExceededException("Destination filter can only be set $maxDestinationUsesPerDay times per day")
        }
        driver.destinationLatitude = latitude
        driver.destinationLongitude = longitude
        driver.destinationUsesToday += 1
        return rideDriverRepository.save(driver)
    }

    // Real Uber "cancel Destination Filter" counterpart -- clearing does not consume a
    // real daily use, same "undo your own choice is always free" shape this backend's
    // other real toggles (MerchantService.setAcceptingOrders) already establish.
    fun clearDestination(userId: String): RideDriver {
        val driver = rideDriverRepository.findByUserId(userId)
            ?: throw RideDriverNotRegisteredException("This account is not registered as a driver")
        driver.destinationLatitude = null
        driver.destinationLongitude = null
        return rideDriverRepository.save(driver)
    }
}
