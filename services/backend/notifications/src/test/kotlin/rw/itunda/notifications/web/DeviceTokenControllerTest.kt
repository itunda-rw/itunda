package rw.itunda.notifications.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.DevicePlatform
import rw.itunda.core.domain.DeviceToken
import rw.itunda.core.repository.DeviceTokenRepository
import rw.itunda.core.security.CurrentUser

/**
 * First test coverage for DeviceTokenController -- register/unregister,
 * including the real, previously-live TransactionRequiredException fix on
 * unregister (now guarded by @Transactional) and the device-handoff
 * token-reassignment overwrite logic, had zero test coverage.
 */
class DeviceTokenControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    Given("a brand-new device token") {
        val repository = mockk<DeviceTokenRepository>()
        val controller = DeviceTokenController(repository)
        every { repository.findByToken("token-1") } returns null
        val savedSlot = slot<DeviceToken>()
        every { repository.save(capture(savedSlot)) } answers { firstArg() }

        When("registering it") {
            val response = controller.register(RegisterDeviceTokenRequest(DevicePlatform.ANDROID, "token-1"), currentUser)

            Then("a real new row is created, scoped to the caller's own userId") {
                verify(exactly = 1) { repository.save(any()) }
                savedSlot.captured.userId shouldBe "user_1"
                savedSlot.captured.token shouldBe "token-1"
                response.statusCode shouldBe HttpStatus.CREATED
            }
        }
    }

    Given("a token already registered to a different user (device handoff)") {
        val repository = mockk<DeviceTokenRepository>()
        val controller = DeviceTokenController(repository)
        val existing = DeviceToken(id = "device_token_1", userId = "previous_user", platform = DevicePlatform.ANDROID, token = "token-1")
        every { repository.findByToken("token-1") } returns existing
        every { repository.save(any()) } answers { firstArg() }

        When("a different real user registers the same token") {
            val response = controller.register(RegisterDeviceTokenRequest(DevicePlatform.ANDROID, "token-1"), currentUser)

            Then("the existing row is reassigned to the new owner, not duplicated") {
                verify(exactly = 1) { repository.save(existing) }
                existing.userId shouldBe "user_1"
                response.body?.get("deviceToken") shouldBe existing
            }
        }
    }

    Given("a real token owned by the caller") {
        val repository = mockk<DeviceTokenRepository>()
        val controller = DeviceTokenController(repository)
        val existing = DeviceToken(id = "device_token_1", userId = "user_1", platform = DevicePlatform.ANDROID, token = "token-1")
        every { repository.findByToken("token-1") } returns existing
        every { repository.deleteByToken("token-1") } returns Unit

        When("the owner unregisters it") {
            val response = controller.unregister("token-1", currentUser)

            Then("it is really deleted") {
                verify(exactly = 1) { repository.deleteByToken("token-1") }
                response.body?.get("success") shouldBe true
            }
        }
    }

    Given("a real token owned by a different user") {
        val repository = mockk<DeviceTokenRepository>()
        val controller = DeviceTokenController(repository)
        val existing = DeviceToken(id = "device_token_1", userId = "someone_else", platform = DevicePlatform.ANDROID, token = "token-1")
        every { repository.findByToken("token-1") } returns existing

        When("a non-owner tries to unregister it") {
            val response = controller.unregister("token-1", currentUser)

            Then("it is never deleted, but the call still returns a real success response") {
                verify(exactly = 0) { repository.deleteByToken(any()) }
                response.body?.get("success") shouldBe true
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
