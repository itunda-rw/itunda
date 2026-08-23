package rw.itunda.auth

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.TrustedDevice
import rw.itunda.core.domain.User
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.TrustedDeviceRepository
import rw.itunda.core.repository.UserRepository
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.time.Duration
import java.time.Instant
import java.util.Base64
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
    private val redisTemplate: StringRedisTemplate,
) {
    private val passwordEncoder = BCryptPasswordEncoder()

    // Atomic get-and-delete, same idiom as RateLimiter's own INCR+EXPIRE script -- a
    // plain GET-then-DEL from application code would let two concurrent verify-signature
    // requests both read the same not-yet-deleted challenge and both pass, letting a
    // signature be replayed once. One EVAL removes that window.
    private val consumeChallengeScript = DefaultRedisScript(
        """
        local v = redis.call('GET', KEYS[1])
        if v then redis.call('DEL', KEYS[1]) end
        return v
        """.trimIndent(),
        String::class.java,
    )

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

    /** Real Keystore/Secure-Enclave-signed-challenge device verification (item 246) --
     * see TrustedDevice.publicKey's own doc comment. Deliberately requires the SAME
     * password proof as [verifyDevice] above, not just a valid JWT: a stolen JWT alone
     * must never be enough to plant an attacker-controlled key and immediately sign a
     * challenge with it, which would be a strictly worse outcome than the password-only
     * design this is extending. Because registering a key already costs a correct
     * password, a successful call here is exactly as strong a trust decision as
     * verifyDevice, so it marks the device trusted immediately -- the point of the key
     * is to make the NEXT step-up cheaper (a biometric signature, no retyping), not to
     * dodge this one-time password cost. */
    @Transactional
    fun registerDeviceKey(userId: String, deviceId: String?, publicKeyBase64: String, password: String): TrustedDevice {
        if (deviceId == null) {
            throw InvalidDeviceVerificationException("This session has no device id to verify")
        }
        rateLimiter.checkLimit("auth:device-verify:$userId", limit = 5, window = Duration.ofMinutes(1))
        val user = userRepository.findById(userId).orElseThrow { DeviceNotFoundException("User not found") }
        if (!passwordEncoder.matches(password, user.passwordHash)) {
            throw InvalidDeviceVerificationException("Incorrect password")
        }
        // Fail fast on a malformed key rather than storing garbage that only breaks on
        // the next step-up attempt.
        parsePublicKey(publicKeyBase64)
        val device = trustedDeviceRepository.findByUserIdAndDeviceId(userId, deviceId)
            ?: throw DeviceNotFoundException("Device not found")
        device.publicKey = publicKeyBase64
        device.trusted = true
        device.verifiedAt = Instant.now()
        device.lastSeenAt = Instant.now()
        return trustedDeviceRepository.save(device)
    }

    /** A fresh, single-use, short-lived nonce for the caller's current device to sign.
     * Not persisted via JPA -- this is exactly the kind of short-lived, high-churn state
     * Redis (already the store behind RateLimiter above) is for, not the primary DB. */
    fun issueChallenge(userId: String, deviceId: String?): String {
        if (deviceId == null) {
            throw InvalidDeviceVerificationException("This session has no device id to verify")
        }
        rateLimiter.checkLimit("auth:device-challenge:$userId", limit = 10, window = Duration.ofMinutes(1))
        val nonce = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val challenge = Base64.getEncoder().encodeToString(nonce)
        redisTemplate.opsForValue().set("device-challenge:$userId:$deviceId", challenge, Duration.ofMinutes(2))
        return challenge
    }

    /** Verifies a signature over the challenge issued by [issueChallenge] against the
     * public key registered by [registerDeviceKey] -- the cheap, password-free step-up
     * path a biometric prompt can drive once a key exists. */
    @Transactional
    fun verifyDeviceBySignature(userId: String, deviceId: String?, signatureBase64: String): TrustedDevice {
        if (deviceId == null) {
            throw InvalidDeviceVerificationException("This session has no device id to verify")
        }
        rateLimiter.checkLimit("auth:device-verify:$userId", limit = 5, window = Duration.ofMinutes(1))
        val challenge = redisTemplate.execute(consumeChallengeScript, listOf("device-challenge:$userId:$deviceId"))
            ?: throw InvalidDeviceVerificationException("No pending challenge for this device, or it expired -- request a new one")
        val device = trustedDeviceRepository.findByUserIdAndDeviceId(userId, deviceId)
            ?: throw DeviceNotFoundException("Device not found")
        val publicKeyBase64 = device.publicKey
            ?: throw InvalidDeviceVerificationException("No key registered for this device")
        verifySignature(publicKeyBase64, challenge, signatureBase64)
        device.trusted = true
        device.verifiedAt = Instant.now()
        device.lastSeenAt = Instant.now()
        return trustedDeviceRepository.save(device)
    }

    // Real passwordless-login rollout (2026-08-24, direct user follow-up after a real
    // Toss login-flow research pass: registration is phone+OTP then a 6-digit PIN,
    // but every REPEAT open of the app is biometric/PIN-pad against an already-
    // recognized device, never re-typing the full credential) -- see AuthService's
    // own doc comment for the full account. This is the real "no PIN needed on a
    // recognized device" login path, distinct from [issueChallenge]/
    // [verifyDeviceBySignature] above (which are STEP-UP re-verification for an
    // ALREADY-authenticated session, not usable here since there's no JWT yet).

    /** Unauthenticated counterpart to [issueChallenge]. Requires the device to already
     * have a registered key (set during a prior password/PIN login via
     * [registerKeyDuringAuth]) -- there's nothing to sign a challenge with otherwise.
     * Deliberately returns the SAME generic error whether the phone number doesn't
     * exist or exists with no keyed device -- the same bounded account-enumeration
     * trade-off [AuthService.checkPhoneExists]'s own doc comment already accepts for
     * phone-number-identified accounts, not a new privacy leak class. */
    fun issueLoginChallenge(phoneNumber: String, deviceId: String): String {
        rateLimiter.checkLimit("auth:login-challenge:$phoneNumber", limit = 10, window = Duration.ofMinutes(1))
        val trimmedId = deviceId.trim().take(128)
        val user = userRepository.findByPhoneNumber(phoneNumber)
        val device = user?.let { trustedDeviceRepository.findByUserIdAndDeviceId(it.id, trimmedId) }
        if (user == null || device?.publicKey == null) {
            throw InvalidDeviceVerificationException("No signed-in device found for this number")
        }
        val nonce = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val challenge = Base64.getEncoder().encodeToString(nonce)
        redisTemplate.opsForValue().set("device-login-challenge:${user.id}:$trimmedId", challenge, Duration.ofMinutes(2))
        return challenge
    }

    /** Unauthenticated counterpart to [verifyDeviceBySignature] -- on a valid
     * signature, returns the real user so [AuthService.loginWithDeviceSignature] can
     * issue a fresh session, the actual passwordless-login outcome. */
    @Transactional
    fun verifyLoginSignature(phoneNumber: String, deviceId: String, signatureBase64: String): User {
        rateLimiter.checkLimit("auth:login-verify:$phoneNumber", limit = 5, window = Duration.ofMinutes(1))
        val trimmedId = deviceId.trim().take(128)
        val user = userRepository.findByPhoneNumber(phoneNumber)
            ?: throw InvalidDeviceVerificationException("Signature verification failed")
        val device = trustedDeviceRepository.findByUserIdAndDeviceId(user.id, trimmedId)
            ?: throw InvalidDeviceVerificationException("Signature verification failed")
        val publicKeyBase64 = device.publicKey ?: throw InvalidDeviceVerificationException("Signature verification failed")
        val challenge = redisTemplate.execute(consumeChallengeScript, listOf("device-login-challenge:${user.id}:$trimmedId"))
            ?: throw InvalidDeviceVerificationException("No pending challenge for this device, or it expired -- request a new one")
        verifySignature(publicKeyBase64, challenge, signatureBase64)
        device.lastSeenAt = Instant.now()
        trustedDeviceRepository.save(device)
        return user
    }

    /** Folds real device-key registration directly into register()/login() instead of
     * requiring a separate later [registerDeviceKey] call -- no password re-check here
     * (unlike registerDeviceKey), since the caller already proved password/PIN
     * ownership in THIS SAME register/login request; asking a second time in the same
     * round trip would be pure friction, not real security. A no-op if the device row
     * doesn't exist yet (recordRegistrationDevice/recordLoginDevice must run first) or
     * no deviceId was supplied -- an older client that doesn't send a public key keeps
     * working exactly as before, just without the passwordless-login upgrade. */
    @Transactional
    fun registerKeyDuringAuth(userId: String, deviceId: String?, publicKeyBase64: String) {
        if (deviceId == null) return
        parsePublicKey(publicKeyBase64) // fail fast on a malformed key
        val trimmedId = deviceId.trim().take(128)
        val device = trustedDeviceRepository.findByUserIdAndDeviceId(userId, trimmedId) ?: return
        device.publicKey = publicKeyBase64
        device.lastSeenAt = Instant.now()
        trustedDeviceRepository.save(device)
    }

    private fun verifySignature(publicKeyBase64: String, challengeBase64: String, signatureBase64: String) {
        val publicKey = parsePublicKey(publicKeyBase64)
        val signatureBytes = try {
            Base64.getDecoder().decode(signatureBase64)
        } catch (e: IllegalArgumentException) {
            throw InvalidDeviceVerificationException("Malformed signature")
        }
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(Base64.getDecoder().decode(challengeBase64))
        if (!verifier.verify(signatureBytes)) {
            throw InvalidDeviceVerificationException("Signature does not match this device's registered key")
        }
    }

    /** Reconstructs a Java EC public key from the raw uncompressed P-256 point (0x04 ||
     * X || Y, 65 bytes) both Android Keystore and iOS's Secure Enclave hand back natively
     * -- see V100__trusted_device_public_key.sql's own comment on why this wire format
     * needs no per-platform conversion on either client. */
    private fun parsePublicKey(publicKeyBase64: String): ECPublicKey {
        val raw = try {
            Base64.getDecoder().decode(publicKeyBase64)
        } catch (e: IllegalArgumentException) {
            throw InvalidDeviceVerificationException("Malformed public key")
        }
        if (raw.size != 65 || raw[0] != 0x04.toByte()) {
            throw InvalidDeviceVerificationException("Public key must be a raw uncompressed P-256 point")
        }
        val x = BigInteger(1, raw.copyOfRange(1, 33))
        val y = BigInteger(1, raw.copyOfRange(33, 65))
        val params = AlgorithmParameters.getInstance("EC")
        params.init(ECGenParameterSpec("secp256r1"))
        val ecParameterSpec = params.getParameterSpec(ECParameterSpec::class.java)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(ECPublicKeySpec(ECPoint(x, y), ecParameterSpec)) as ECPublicKey
    }
}
