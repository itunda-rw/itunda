package rw.itunda.notifications.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.Notification
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository

/**
 * First test coverage for NotificationAdminController -- a real, previously
 * missing B2B lever (2026-09-07) letting ops broadcast a system-wide
 * announcement into every user's in-app inbox.
 */
class NotificationAdminControllerTest : BehaviorSpec({

    Given("a real broadcast request with three real users") {
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>()
        val controller = NotificationAdminController(userRepository, notificationRepository)
        every { userRepository.findAllUserIds() } returns listOf("user_1", "user_2", "user_3")
        val savedSlot = slot<List<Notification>>()
        every { notificationRepository.saveAll(capture(savedSlot)) } answers { firstArg() }

        When("broadcasting") {
            val response = controller.broadcast(BroadcastNotificationRequest("Scheduled maintenance", "itunda will be briefly unavailable tonight."))

            Then("a real notification row is created for every real user, and the count is reported back") {
                verify(exactly = 1) { notificationRepository.saveAll(any<List<Notification>>()) }
                savedSlot.captured.map { it.userId } shouldBe listOf("user_1", "user_2", "user_3")
                savedSlot.captured.all { it.type == "system_broadcast" } shouldBe true
                savedSlot.captured.all { it.title == "Scheduled maintenance" } shouldBe true
                savedSlot.captured.all { !it.isRead } shouldBe true
                response.body?.get("success") shouldBe true
                response.body?.get("sentCount") shouldBe 3
            }
        }
    }

    Given("a real broadcast request with no users at all") {
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>()
        val controller = NotificationAdminController(userRepository, notificationRepository)
        every { userRepository.findAllUserIds() } returns emptyList()
        every { notificationRepository.saveAll(emptyList<Notification>()) } returns emptyList()

        When("broadcasting") {
            val response = controller.broadcast(BroadcastNotificationRequest("Title", "Body"))

            Then("it real-completes with a zero sent count rather than failing") {
                response.body?.get("sentCount") shouldBe 0
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
