package rw.itunda.auth

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.data.redis.core.script.RedisScript
import rw.itunda.core.domain.TrustedDevice
import rw.itunda.core.domain.User
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.TrustedDeviceRepository
import rw.itunda.core.repository.UserRepository
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Base64

/**
 * Real Toss-sourced passwordless-login rollout (2026-08-24) -- extracted from
 * DeviceServiceTest.kt (see scripts/file-size-lint.py, that file crossed 500 lines
 * for the first time). Still tests `DeviceService` directly, same as that file --
 * this is a test-organization split by real, cohesive scenario topic, not a
 * production-class split, since `DeviceService.kt` itself is only 349 lines and
 * doesn't need one. These three methods (issueLoginChallenge/verifyLoginSignature/
 * registerKeyDuringAuth) are the UNAUTHENTICATED counterparts to
 * DeviceServiceTest.kt's "step-up via signed challenge" scenarios -- used to
 * establish a BRAND NEW session with no JWT at all, not to step up an
 * already-authenticated one.
 */
class DevicePasswordlessLoginTest : BehaviorSpec({

    // Same real P-256 key pair generation as DeviceServiceTest.kt -- duplicated
    // rather than shared via a test-fixtures module, matching this codebase's own
    // existing convention (no shared test-helper module exists for backend Kotest
    // specs; every *Test.kt file is self-contained).
    fun generateRealDeviceKeyPair(): Pair<String, java.security.PrivateKey> {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyPairGenerator.generateKeyPair()
        val publicKey = keyPair.public as ECPublicKey
        fun fixedLength(value: java.math.BigInteger, length: Int): ByteArray {
            val raw = value.toByteArray()
            if (raw.size == length) return raw
            val result = ByteArray(length)
            if (raw.size > length) System.arraycopy(raw, raw.size - length, result, 0, length)
            else System.arraycopy(raw, 0, result, length - raw.size, raw.size)
            return result
        }
        val point = ByteArray(65)
        point[0] = 0x04
        System.arraycopy(fixedLength(publicKey.w.affineX, 32), 0, point, 1, 32)
        System.arraycopy(fixedLength(publicKey.w.affineY, 32), 0, point, 33, 32)
        return Base64.getEncoder().encodeToString(point) to keyPair.private
    }

    fun sign(privateKey: java.security.PrivateKey, challengeBytes: ByteArray): String {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(challengeBytes)
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    Given("a real passwordless login via a device's already-registered key") {
        val trustedDeviceRepository = mockk<TrustedDeviceRepository>()
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val redisTemplate = mockk<StringRedisTemplate>(relaxed = true)
        val service = DeviceService(trustedDeviceRepository, userRepository, notificationRepository, rateLimiter, pushNotificationService, redisTemplate)

        val (publicKeyBase64, privateKey) = generateRealDeviceKeyPair()
        val user = User(id = "user_10", phoneNumber = "+250788000010", firstName = "Jean", lastName = "B", passwordHash = "unused", createdAt = Instant.now())

        When("issuing a login challenge for a phone number with a real keyed device") {
            every { userRepository.findByPhoneNumber("+250788000010") } returns user
            val device = TrustedDevice(id = "trusted_device_10", userId = "user_10", deviceId = "device_10", deviceName = null, trusted = true, publicKey = publicKeyBase64)
            every { trustedDeviceRepository.findByUserIdAndDeviceId("user_10", "device_10") } returns device
            val redisValueOps = mockk<ValueOperations<String, String>>(relaxed = true)
            every { redisTemplate.opsForValue() } returns redisValueOps

            val challenge = service.issueLoginChallenge("+250788000010", "device_10")

            Then("it returns a real challenge and stores it under this user+device") {
                challenge.isNotBlank() shouldBe true
                verify(exactly = 1) { redisValueOps.set(any(), challenge, any<java.time.Duration>()) }
            }

            // Real gap found live (repo-wide rate-limiter-verification sweep,
            // 2026-09-08): rateLimiter was relaxed = true with zero verify{} anywhere
            // in this file, so a future accidental removal of the real checkLimit call
            // would have compiled and passed silently.
            Then("the real rate limiter is actually consulted, not just mocked away") {
                verify(exactly = 1) { rateLimiter.checkLimit("auth:login-challenge:+250788000010", limit = 10, window = java.time.Duration.ofMinutes(1)) }
            }
        }

        When("issuing a login challenge for a phone number with no account at all") {
            every { userRepository.findByPhoneNumber("+250788999998") } returns null

            Then("it throws the same generic InvalidDeviceVerificationException as a real-but-unkeyed device -- no account-enumeration side channel") {
                try {
                    service.issueLoginChallenge("+250788999998", "device_x")
                    error("expected InvalidDeviceVerificationException")
                } catch (e: InvalidDeviceVerificationException) {
                    // expected
                }
            }
        }

        When("issuing a login challenge for a real account whose device has no registered key yet") {
            every { userRepository.findByPhoneNumber("+250788000010") } returns user
            val deviceWithNoKey = TrustedDevice(id = "trusted_device_10", userId = "user_10", deviceId = "device_10", deviceName = null, trusted = true, publicKey = null)
            every { trustedDeviceRepository.findByUserIdAndDeviceId("user_10", "device_10") } returns deviceWithNoKey

            Then("it throws the exact same generic error as the no-account case above") {
                try {
                    service.issueLoginChallenge("+250788000010", "device_10")
                    error("expected InvalidDeviceVerificationException")
                } catch (e: InvalidDeviceVerificationException) {
                    // expected
                }
            }
        }

        When("verifying a real, correctly-signed login challenge") {
            val challengeBytes = "a-real-32-byte-random-challenge!".toByteArray()
            val challenge = Base64.getEncoder().encodeToString(challengeBytes)
            every { userRepository.findByPhoneNumber("+250788000010") } returns user
            val device = TrustedDevice(id = "trusted_device_10", userId = "user_10", deviceId = "device_10", deviceName = null, trusted = true, publicKey = publicKeyBase64)
            every { trustedDeviceRepository.findByUserIdAndDeviceId("user_10", "device_10") } returns device
            every { redisTemplate.execute(any<RedisScript<String>>(), any<List<String>>()) } returns challenge
            every { trustedDeviceRepository.save(any()) } answers { firstArg() }

            val result = service.verifyLoginSignature("+250788000010", "device_10", sign(privateKey, challengeBytes))

            Then("it returns the real user -- AuthService.loginWithDeviceSignature issues a fresh session from this, no password or PIN involved") {
                result.id shouldBe "user_10"
            }

            // Real gap found live (repo-wide rate-limiter-verification sweep,
            // 2026-09-08): rateLimiter was relaxed = true with zero verify{} anywhere
            // in this file, so a future accidental removal of the real checkLimit call
            // would have compiled and passed silently.
            Then("the real rate limiter is actually consulted, not just mocked away") {
                verify(exactly = 1) { rateLimiter.checkLimit("auth:login-verify:+250788000010", limit = 5, window = java.time.Duration.ofMinutes(1)) }
            }
        }

        When("verifying a login signature that doesn't match the registered key") {
            val challengeBytes = "a-real-32-byte-random-challenge!".toByteArray()
            val challenge = Base64.getEncoder().encodeToString(challengeBytes)
            every { userRepository.findByPhoneNumber("+250788000010") } returns user
            val device = TrustedDevice(id = "trusted_device_10", userId = "user_10", deviceId = "device_10", deviceName = null, trusted = true, publicKey = publicKeyBase64)
            every { trustedDeviceRepository.findByUserIdAndDeviceId("user_10", "device_10") } returns device
            every { redisTemplate.execute(any<RedisScript<String>>(), any<List<String>>()) } returns challenge
            val (_, otherPrivateKey) = generateRealDeviceKeyPair()

            Then("it throws InvalidDeviceVerificationException -- a forged/wrong-device signature never issues a session") {
                try {
                    service.verifyLoginSignature("+250788000010", "device_10", sign(otherPrivateKey, challengeBytes))
                    error("expected InvalidDeviceVerificationException")
                } catch (e: InvalidDeviceVerificationException) {
                    // expected
                }
            }
        }

        When("registering a device key during a real register()/login() call, no separate password re-check") {
            val device = TrustedDevice(id = "trusted_device_10", userId = "user_10", deviceId = "device_10", deviceName = null, trusted = true, publicKey = null)
            every { trustedDeviceRepository.findByUserIdAndDeviceId("user_10", "device_10") } returns device
            every { trustedDeviceRepository.save(any()) } answers { firstArg() }

            service.registerKeyDuringAuth("user_10", "device_10", publicKeyBase64)

            Then("the device's public key is set, ready for the next passwordless-login attempt") {
                device.publicKey shouldBe publicKeyBase64
            }
        }

        When("registering a device key with no deviceId (an older client's request)") {
            Then("it's a real, silent no-op -- an older client just doesn't get the passwordless-login upgrade") {
                service.registerKeyDuringAuth("user_10", null, publicKeyBase64)
                verify(exactly = 0) { trustedDeviceRepository.findByUserIdAndDeviceId(any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
