package rw.itunda.core.push

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.repository.DeviceTokenRepository

class PushNotificationServiceTest : BehaviorSpec({
    Given("a transient device-token repository failure") {
        val deviceTokenRepository = mockk<DeviceTokenRepository>()
        val pushSender = mockk<PushSender>()
        every { deviceTokenRepository.findByUserId("user_1") } throws IllegalStateException("database unavailable")
        val service = PushNotificationService(deviceTokenRepository, pushSender)

        When("a business flow sends a push notification") {
            service.sendToUser("user_1", "Security alert", "Review required")

            Then("the auxiliary push failure is contained") {
                verify(exactly = 0) { pushSender.send(any(), any(), any(), any()) }
            }
        }
    }
})
