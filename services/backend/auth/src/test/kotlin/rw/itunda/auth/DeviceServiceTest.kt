package rw.itunda.auth

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.TrustedDevice
import rw.itunda.core.domain.User
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.TrustedDeviceRepository
import rw.itunda.core.repository.UserRepository
import java.time.Instant
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

    Given("a real user's first device, at registration") {
        val trustedDeviceRepository = mockk<TrustedDeviceRepository>()
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = DeviceService(trustedDeviceRepository, userRepository, notificationRepository, rateLimiter)

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
        every { notificationRepository.save(any()) } answers { firstArg() }
        val service = DeviceService(trustedDeviceRepository, userRepository, notificationRepository, rateLimiter)

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
        }

        When("the device is already known") {
            val existing = TrustedDevice(id = "trusted_device_1", userId = "user_2", deviceId = "device_known", deviceName = "iPhone", trusted = true, lastSeenAt = Instant.EPOCH)
            every { trustedDeviceRepository.findByUserIdAndDeviceId("user_2", "device_known") } returns existing
            every { trustedDeviceRepository.save(any()) } answers { firstArg() }

            service.recordLoginDevice("user_2", "device_known", "iPhone")

            Then("it just updates lastSeenAt -- no duplicate row, no new notification") {
                existing.lastSeenAt shouldNotBe Instant.EPOCH
                verify(exactly = 0) { notificationRepository.save(any()) }
            }
        }
    }

    Given("a real user step-up-verifying their current device") {
        val trustedDeviceRepository = mockk<TrustedDeviceRepository>()
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = DeviceService(trustedDeviceRepository, userRepository, notificationRepository, rateLimiter)

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
        val service = DeviceService(trustedDeviceRepository, userRepository, notificationRepository, rateLimiter)

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
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
