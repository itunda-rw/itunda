package rw.itunda.auth

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.core.domain.Notification
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
import java.util.Optional

/**
 * Real device binding (2026-07-20) -- see TrustedDevice's own doc comment for the full
 * account of what this closes and why. These tests cover DeviceService's own real
 * write-path logic; DeviceVerificationFilter (the enforcement point) is a plain
 * Spring filter, exercised via live verification instead of a unit test, matching this
 * codebase's own established convention for filter-level checks.
 */
class DeviceServiceTest : BehaviorSpec({

    val passwordEncoder = BCryptPasswordEncoder()

    // Real P-256 key pair in the exact wire format DeviceService.parsePublicKey expects
    // (raw uncompressed point, 0x04 || X || Y, 65 bytes, base64) -- generated the same
    // way DeviceKeyManager.kt (Android)/DeviceKeyManager.swift (iOS) do, so these tests
    // exercise the real reconstruction/verification path, not a stubbed-out shortcut.
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

    Given("a real user's first device, at registration") {
        val trustedDeviceRepository = mockk<TrustedDeviceRepository>()
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val redisTemplate = mockk<StringRedisTemplate>(relaxed = true)
        val service = DeviceService(trustedDeviceRepository, userRepository, notificationRepository, rateLimiter, pushNotificationService, redisTemplate)

        When("recording the registration device") {
            val savedSlot = slot<TrustedDevice>()
            every { trustedDeviceRepository.save(capture(savedSlot)) } answers { firstArg() }

            service.recordRegistrationDevice("user_1", "device_abc", "iPhone 17")

            Then("it's saved already trusted, no step-up needed") {
                savedSlot.captured.userId shouldBe "user_1"
                savedSlot.captured.deviceId shouldBe "device_abc"
                savedSlot.captured.deviceName shouldBe "iPhone 17"
                savedSlot.captured.trusted shouldBe true
                savedSlot.captured.verifiedAt shouldNotBe null
            }
        }

        When("recording a registration with no deviceId (an older, not-yet-updated client)") {
            Then("it's a real no-op, not an error") {
                service.recordRegistrationDevice("user_1", null, null)
                verify(exactly = 0) { trustedDeviceRepository.save(any()) }
            }
        }
    }

    Given("a real login from a brand-new device") {
        val trustedDeviceRepository = mockk<TrustedDeviceRepository>()
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val redisTemplate = mockk<StringRedisTemplate>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val service = DeviceService(trustedDeviceRepository, userRepository, notificationRepository, rateLimiter, pushNotificationService, redisTemplate)

        When("the device has never been seen before") {
            every { trustedDeviceRepository.findByUserIdAndDeviceId("user_2", "device_new") } returns null
            val savedSlot = slot<TrustedDevice>()
            every { trustedDeviceRepository.save(capture(savedSlot)) } answers { firstArg() }

            service.recordLoginDevice("user_2", "device_new", "Chrome on Mac")

            Then("it's recorded but NOT trusted, and a real notification fires") {
                savedSlot.captured.trusted shouldBe false
                savedSlot.captured.verifiedAt shouldBe null
                verify(exactly = 1) { notificationRepository.save(match<Notification> { it.type == "NEW_DEVICE_LOGIN" && it.userId == "user_2" }) }
            }

            Then("the real account owner also gets a real push notification, not just the in-app one") {
                verify(exactly = 1) { pushNotificationService.sendToUser("user_2", "New device signed in", any(), any()) }
            }
        }

        When("the device is already known") {
            val existing = TrustedDevice(id = "trusted_device_1", userId = "user_2", deviceId = "device_known", deviceName = "iPhone", trusted = true, lastSeenAt = Instant.EPOCH)
            every { trustedDeviceRepository.findByUserIdAndDeviceId("user_2", "device_known") } returns existing
            every { trustedDeviceRepository.save(any()) } answers { firstArg() }

            service.recordLoginDevice("user_2", "device_known", "iPhone")

            Then("it just updates lastSeenAt -- no duplicate row, no new notification") {
                existing.lastSeenAt shouldNotBe Instant.EPOCH
                verify(exactly = 0) { notificationRepository.save(any()) }
                verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any()) }
            }
        }
    }

    Given("a new-device login that is still inside a database transaction") {
        val trustedDeviceRepository = mockk<TrustedDeviceRepository>()
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val redisTemplate = mockk<StringRedisTemplate>(relaxed = true)
        val service = DeviceService(trustedDeviceRepository, userRepository, notificationRepository, rateLimiter, pushNotificationService, redisTemplate)

        every { trustedDeviceRepository.findByUserIdAndDeviceId("user_after_commit", "device_after_commit") } returns null
        every { trustedDeviceRepository.save(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }

        When("the new device has been recorded but its transaction has not committed") {
            TransactionSynchronizationManager.initSynchronization()
            try {
                service.recordLoginDevice("user_after_commit", "device_after_commit", "Chrome on Mac")

                Then("the durable in-app notification is saved, but no external push has been sent") {
                    verify(exactly = 1) { notificationRepository.save(any()) }
                    verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any()) }
                }

                Then("the push is sent only after the transaction's commit callback") {
                    TransactionSynchronizationManager.getSynchronizations().single().afterCommit()
                    verify(exactly = 1) {
                        pushNotificationService.sendToUser(
                            "user_after_commit",
                            "New device signed in",
                            any(),
                            mapOf("deviceId" to "device_after_commit"),
                        )
                    }
                }
            } finally {
                TransactionSynchronizationManager.clearSynchronization()
            }
        }
    }

    Given("a real user step-up-verifying their current device") {
        val trustedDeviceRepository = mockk<TrustedDeviceRepository>()
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val redisTemplate = mockk<StringRedisTemplate>(relaxed = true)
        val service = DeviceService(trustedDeviceRepository, userRepository, notificationRepository, rateLimiter, pushNotificationService, redisTemplate)

        val user = User(id = "user_3", phoneNumber = "+250788000003", firstName = "Jean", lastName = "B", passwordHash = passwordEncoder.encode("real-password"), createdAt = Instant.now())
        val device = TrustedDevice(id = "trusted_device_2", userId = "user_3", deviceId = "device_pending", deviceName = null, trusted = false)

        When("the real password is correct") {
            every { userRepository.findById("user_3") } returns Optional.of(user)
            every { trustedDeviceRepository.findByUserIdAndDeviceId("user_3", "device_pending") } returns device
            every { trustedDeviceRepository.save(any()) } answers { firstArg() }

            val result = service.verifyDevice("user_3", "device_pending", "real-password")

            Then("the device becomes trusted") {
                result.trusted shouldBe true
                result.verifiedAt shouldNotBe null
            }
        }

        When("the real password is wrong") {
            every { userRepository.findById("user_3") } returns Optional.of(user)

            Then("it throws InvalidDeviceVerificationException rather than trusting the device") {
                try {
                    service.verifyDevice("user_3", "device_pending", "wrong-password")
                    error("expected InvalidDeviceVerificationException")
                } catch (e: InvalidDeviceVerificationException) {
                    verify(exactly = 0) { trustedDeviceRepository.save(any()) }
                }
            }
        }

        When("the caller's session has no deviceId at all") {
            Then("it throws InvalidDeviceVerificationException before ever checking the password") {
                try {
                    service.verifyDevice("user_3", null, "real-password")
                    error("expected InvalidDeviceVerificationException")
                } catch (e: InvalidDeviceVerificationException) {
                    verify(exactly = 0) { userRepository.findById(any()) }
                }
            }
        }
    }

    Given("a real user revoking a device") {
        val trustedDeviceRepository = mockk<TrustedDeviceRepository>()
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val redisTemplate = mockk<StringRedisTemplate>(relaxed = true)
        val service = DeviceService(trustedDeviceRepository, userRepository, notificationRepository, rateLimiter, pushNotificationService, redisTemplate)

        When("the device is real and theirs") {
            val device = TrustedDevice(id = "trusted_device_3", userId = "user_4", deviceId = "device_old", deviceName = null, trusted = true)
            every { trustedDeviceRepository.findByUserIdAndDeviceId("user_4", "device_old") } returns device
            every { trustedDeviceRepository.delete(device) } returns Unit

            service.revokeDevice("user_4", "device_old")

            Then("it's real-deleted") {
                verify(exactly = 1) { trustedDeviceRepository.delete(device) }
            }
        }

        When("the device doesn't exist for this user") {
            every { trustedDeviceRepository.findByUserIdAndDeviceId("user_4", "device_unknown") } returns null

            Then("it throws DeviceNotFoundException") {
                try {
                    service.revokeDevice("user_4", "device_unknown")
                    error("expected DeviceNotFoundException")
                } catch (e: DeviceNotFoundException) {
                    // expected
                }
            }
        }
    }

    // Real Keystore/Secure-Enclave-signed-challenge device verification (item 246).
    Given("a real user registering a Keystore/Secure-Enclave device key") {
        val trustedDeviceRepository = mockk<TrustedDeviceRepository>()
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val redisTemplate = mockk<StringRedisTemplate>(relaxed = true)
        val service = DeviceService(trustedDeviceRepository, userRepository, notificationRepository, rateLimiter, pushNotificationService, redisTemplate)

        val user = User(id = "user_5", phoneNumber = "+250788000005", firstName = "Alice", lastName = "K", passwordHash = passwordEncoder.encode("real-password"), createdAt = Instant.now())
        val device = TrustedDevice(id = "trusted_device_5", userId = "user_5", deviceId = "device_key_pending", deviceName = null, trusted = false)
        val (publicKeyBase64, _) = generateRealDeviceKeyPair()

        When("the real password is correct and the key is a real, well-formed public point") {
            every { userRepository.findById("user_5") } returns Optional.of(user)
            every { trustedDeviceRepository.findByUserIdAndDeviceId("user_5", "device_key_pending") } returns device
            every { trustedDeviceRepository.save(any()) } answers { firstArg() }

            val result = service.registerDeviceKey("user_5", "device_key_pending", publicKeyBase64, "real-password")

            Then("the device is trusted and the public key is stored") {
                result.trusted shouldBe true
                result.verifiedAt shouldNotBe null
                result.publicKey shouldBe publicKeyBase64
            }
        }

        When("the real password is wrong") {
            every { userRepository.findById("user_5") } returns Optional.of(user)

            Then("it throws InvalidDeviceVerificationException and never stores the key") {
                try {
                    service.registerDeviceKey("user_5", "device_key_pending", publicKeyBase64, "wrong-password")
                    error("expected InvalidDeviceVerificationException")
                } catch (e: InvalidDeviceVerificationException) {
                    verify(exactly = 0) { trustedDeviceRepository.save(any()) }
                }
            }
        }

        When("the public key is malformed (not a real 65-byte uncompressed point)") {
            every { userRepository.findById("user_5") } returns Optional.of(user)

            Then("it throws InvalidDeviceVerificationException before ever looking up the device") {
                try {
                    service.registerDeviceKey("user_5", "device_key_pending", Base64.getEncoder().encodeToString(ByteArray(10)), "real-password")
                    error("expected InvalidDeviceVerificationException")
                } catch (e: InvalidDeviceVerificationException) {
                    verify(exactly = 0) { trustedDeviceRepository.findByUserIdAndDeviceId(any(), any()) }
                }
            }
        }
    }

    Given("a real device requesting a step-up challenge") {
        val trustedDeviceRepository = mockk<TrustedDeviceRepository>()
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val redisTemplate = mockk<StringRedisTemplate>(relaxed = true)
        val valueOperations = mockk<ValueOperations<String, String>>(relaxed = true)
        every { redisTemplate.opsForValue() } returns valueOperations
        val service = DeviceService(trustedDeviceRepository, userRepository, notificationRepository, rateLimiter, pushNotificationService, redisTemplate)

        When("a real deviceId is present") {
            val challenge = service.issueChallenge("user_6", "device_6")

            Then("it's a real random 32-byte nonce, base64-encoded, stored in Redis with a real TTL") {
                Base64.getDecoder().decode(challenge).size shouldBe 32
                verify(exactly = 1) { valueOperations.set("device-challenge:user_6:device_6", challenge, java.time.Duration.ofMinutes(2)) }
            }
        }

        When("the caller's session has no deviceId at all") {
            Then("it throws InvalidDeviceVerificationException before touching Redis") {
                try {
                    service.issueChallenge("user_6", null)
                    error("expected InvalidDeviceVerificationException")
                } catch (e: InvalidDeviceVerificationException) {
                    verify(exactly = 0) { valueOperations.set(any(), any(), any<java.time.Duration>()) }
                }
            }
        }
    }

    Given("a real device step-up via a Keystore/Secure-Enclave-signed challenge") {
        val trustedDeviceRepository = mockk<TrustedDeviceRepository>()
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val redisTemplate = mockk<StringRedisTemplate>(relaxed = true)
        val service = DeviceService(trustedDeviceRepository, userRepository, notificationRepository, rateLimiter, pushNotificationService, redisTemplate)

        val (publicKeyBase64, privateKey) = generateRealDeviceKeyPair()
        val challengeBytes = "a-real-32-byte-random-challenge!".toByteArray()
        val challenge = Base64.getEncoder().encodeToString(challengeBytes)

        When("the device has a registered key and signs the real pending challenge correctly") {
            every { redisTemplate.execute(any<RedisScript<String>>(), any<List<String>>()) } returns challenge
            val device = TrustedDevice(id = "trusted_device_7", userId = "user_7", deviceId = "device_7", deviceName = null, trusted = false, publicKey = publicKeyBase64)
            every { trustedDeviceRepository.findByUserIdAndDeviceId("user_7", "device_7") } returns device
            every { trustedDeviceRepository.save(any()) } answers { firstArg() }

            val result = service.verifyDeviceBySignature("user_7", "device_7", sign(privateKey, challengeBytes))

            Then("the device becomes trusted") {
                result.trusted shouldBe true
                result.verifiedAt shouldNotBe null
            }
        }

        When("the signature doesn't match the registered key (a forged or wrong-key attempt)") {
            every { redisTemplate.execute(any<RedisScript<String>>(), any<List<String>>()) } returns challenge
            val device = TrustedDevice(id = "trusted_device_7", userId = "user_7", deviceId = "device_7", deviceName = null, trusted = false, publicKey = publicKeyBase64)
            every { trustedDeviceRepository.findByUserIdAndDeviceId("user_7", "device_7") } returns device
            val (_, otherPrivateKey) = generateRealDeviceKeyPair()

            Then("it throws InvalidDeviceVerificationException and never trusts the device") {
                try {
                    service.verifyDeviceBySignature("user_7", "device_7", sign(otherPrivateKey, challengeBytes))
                    error("expected InvalidDeviceVerificationException")
                } catch (e: InvalidDeviceVerificationException) {
                    verify(exactly = 0) { trustedDeviceRepository.save(any()) }
                }
            }
        }

        When("no challenge is pending (never issued, or it already expired)") {
            every { redisTemplate.execute(any<RedisScript<*>>(), any<List<String>>()) } returns null

            Then("it throws InvalidDeviceVerificationException before ever looking up the device") {
                try {
                    service.verifyDeviceBySignature("user_7", "device_7", sign(privateKey, challengeBytes))
                    error("expected InvalidDeviceVerificationException")
                } catch (e: InvalidDeviceVerificationException) {
                    verify(exactly = 0) { trustedDeviceRepository.findByUserIdAndDeviceId(any(), any()) }
                }
            }
        }

        When("the device has no key registered yet") {
            every { redisTemplate.execute(any<RedisScript<String>>(), any<List<String>>()) } returns challenge
            val deviceWithNoKey = TrustedDevice(id = "trusted_device_7", userId = "user_7", deviceId = "device_7", deviceName = null, trusted = false, publicKey = null)
            every { trustedDeviceRepository.findByUserIdAndDeviceId("user_7", "device_7") } returns deviceWithNoKey

            Then("it throws InvalidDeviceVerificationException rather than a null-pointer failure") {
                try {
                    service.verifyDeviceBySignature("user_7", "device_7", sign(privateKey, challengeBytes))
                    error("expected InvalidDeviceVerificationException")
                } catch (e: InvalidDeviceVerificationException) {
                    // expected
                }
            }
        }
    }

    // Real Toss-sourced passwordless-login rollout (2026-08-24) -- see this file's
    // own imports/header for the shared real-crypto test helpers reused here
    // unchanged. Distinct from the "step-up via signed challenge" Given block above:
    // these three methods are the UNAUTHENTICATED counterparts (issueLoginChallenge/
    // verifyLoginSignature) plus the one-time key fold-in (registerKeyDuringAuth),
    // used to establish a BRAND NEW session with no JWT at all.
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
