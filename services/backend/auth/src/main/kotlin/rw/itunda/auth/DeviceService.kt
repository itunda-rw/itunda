package rw.itunda.auth

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.TrustedDevice
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.TrustedDeviceRepository
import rw.itunda.core.repository.UserRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID

class DeviceNotFoundException(message: String) : RuntimeException(message)
class InvalidDeviceVerificationException(message: String) : RuntimeException(message)

/**
 * Real device binding (2026-07-20) -- see [rw.itunda.core.domain.TrustedDevice]'s own
 * doc comment for the full account of why this exists and what it's modeled on (Toss's
 * own real, published Gateway/Passport architecture). This service owns the write path
 * (recording a device, verifying it); [DeviceVerificationFilter] owns the read/enforce
 * path (deciding whether an unverified device gets to move money).
 */
@Service
class DeviceService(
    private val trustedDeviceRepository: TrustedDeviceRepository,
    private val userRepository: UserRepository,
    private val notificationRepository: NotificationRepository,
    private val rateLimiter: RateLimiter,
    private val pushNotificationService: PushNotificationService,
) {
    private val passwordEncoder = BCryptPasswordEncoder()

    /** The device a user registered on already proved password ownership in the same
     * real request -- auto-trusted from the start, matching how Toss's own first-device
     * enrollment works (no separate step-up needed for the device you just signed up on). */
    @Transactional
    fun recordRegistrationDevice(userId: String, deviceId: String?, deviceName: String?) {
        if (deviceId == null) return
        val trimmedId = deviceId.trim().take(128)
        if (trimmedId.isEmpty()) return
        trustedDeviceRepository.save(
            TrustedDevice(
                id = "trusted_device_${UUID.randomUUID()}",
                userId = userId,
                deviceId = trimmedId,
                deviceName = deviceName?.trim()?.take(200)?.ifBlank { null },
                trusted = true,
                verifiedAt = Instant.now(),
            ),
        )
    }

    /** Called on every real login. A previously-unseen device is recorded but NOT
     * trusted -- the caller (AuthService.login) still succeeds either way, matching real
     * bank UX (a new device can sign in and look around; it just can't move money until
     * it proves itself -- see DeviceVerificationFilter). Fires a real notification on
     * the user's own already-trusted devices/history the same way every other
     * security-relevant event in this codebase does. Pushes too (2026-07-28), same
     * urgency as FraudReviewService's confirmed-fraud alert -- a real account owner
     * needs to know the instant an unrecognized device signs in, not next app-open. */
    @Transactional
    fun recordLoginDevice(userId: String, deviceId: String?, deviceName: String?) {
        if (deviceId == null) return
        val trimmedId = deviceId.trim().take(128)
        if (trimmedId.isEmpty()) return
        val existing = trustedDeviceRepository.findByUserIdAndDeviceId(userId, trimmedId)
        if (existing != null) {
            existing.lastSeenAt = Instant.now()
            trustedDeviceRepository.save(existing)
            return
        }
        trustedDeviceRepository.save(
            TrustedDevice(
                id = "trusted_device_${UUID.randomUUID()}",
                userId = userId,
                deviceId = trimmedId,
                deviceName = deviceName?.trim()?.take(200)?.ifBlank { null },
                trusted = false,
            ),
        )
        val title = "New device signed in"
        val body = "A login from a new device (${deviceName ?: "unknown device"}) was detected. It can't send money until verified."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = userId, type = "NEW_DEVICE_LOGIN",
                title = title, body = body,
                isRead = false, createdAt = Instant.now(), dataJson = "{\"deviceId\":\"$trimmedId\"}",
            ),
        )
        // Real push wired in (2026-07-28) -- same real security-alert urgency
        // FraudReviewService.decide's confirmed-fraud push already established: if this
        // wasn't the real account owner, they need to know the instant it happens, not
        // whenever they next happen to open the app.
        sendNewDevicePushAfterCommit(userId, title, body, trimmedId)
    }

    /** An external security alert must not claim a device registration that rolled back. */
    private fun sendNewDevicePushAfterCommit(userId: String, title: String, body: String, deviceId: String) {
        val send = { pushNotificationService.sendToUser(userId, title, body, mapOf("deviceId" to deviceId)) }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
    }

    fun getMyDevices(userId: String): List<TrustedDevice> = trustedDeviceRepository.findByUserIdOrderByLastSeenAtDesc(userId)

    /** Real step-up re-verification -- re-proves password ownership on THIS device
     * (resolved from the caller's own current JWT, never a client-supplied id, so a
     * verified session can only ever trust the device it's actually running on) before
     * marking it trusted for future money-moving calls. */
    @Transactional
    fun verifyDevice(userId: String, deviceId: String?, password: String): TrustedDevice {
        if (deviceId == null) {
            throw InvalidDeviceVerificationException("This session has no device id to verify")
        }
        rateLimiter.checkLimit("auth:device-verify:$userId", limit = 5, window = Duration.ofMinutes(1))
        val user = userRepository.findById(userId).orElseThrow { DeviceNotFoundException("User not found") }
        if (!passwordEncoder.matches(password, user.passwordHash)) {
            throw InvalidDeviceVerificationException("Incorrect password")
        }
        val device = trustedDeviceRepository.findByUserIdAndDeviceId(userId, deviceId)
            ?: throw DeviceNotFoundException("Device not found")
        device.trusted = true
        device.verifiedAt = Instant.now()
        device.lastSeenAt = Instant.now()
        return trustedDeviceRepository.save(device)
    }

    /** Real "forget this device" -- the same self-service device management Toss's own
     * security settings page offers. A stranger can't revoke someone else's device
     * since this is always scoped to the caller's own userId, never a client-supplied one. */
    @Transactional
    fun revokeDevice(userId: String, deviceId: String) {
        val device = trustedDeviceRepository.findByUserIdAndDeviceId(userId, deviceId)
            ?: throw DeviceNotFoundException("Device not found")
        trustedDeviceRepository.delete(device)
    }
}
