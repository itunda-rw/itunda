package rw.itunda.notifications.web

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.security.core.Authentication
import rw.itunda.core.domain.Notification
import rw.itunda.core.repository.NotificationRepository
import java.time.Instant
import java.util.Optional

/**
 * First test coverage for :notifications. Directly instantiates the controller with a
 * mocked repository and a mocked Authentication -- Authentication.name is exactly what
 * the JWT filter resolves the caller's userId into, no Spring context needed to
 * exercise the same logic Spring would invoke.
 *
 * markAsRead has a real ownership check (n.userId == userId) before ever flipping
 * isRead -- this file exists to make sure that can't silently regress into marking
 * someone else's notification as read.
 */
class NotificationControllerTest : BehaviorSpec({

    val objectMapper = ObjectMapper()

    fun notification(id: String, userId: String, isRead: Boolean = false) = Notification(
        id = id, userId = userId, type = "TRANSFER", title = "Money received", body = "You received 1,000 RWF",
        isRead = isRead, createdAt = Instant.now(), dataJson = null,
    )

    Given("an authenticated user with notifications") {
        val notificationRepository = mockk<NotificationRepository>()
        val controller = NotificationController(notificationRepository, objectMapper)
        val auth = mockk<Authentication>()
        every { auth.name } returns "user_1"

        When("listing notifications, some read and some not") {
            val mine = listOf(notification("n_1", "user_1", isRead = false), notification("n_2", "user_1", isRead = true))
            every { notificationRepository.findByUserIdOrderByCreatedAtDesc("user_1") } returns mine

            Then("it returns a real unread count, not a hardcoded one") {
                val response = controller.getNotifications(auth)
                @Suppress("UNCHECKED_CAST")
                val body = response.body as Map<String, Any>
                body["unreadCount"] shouldBe 1
                @Suppress("UNCHECKED_CAST")
                val dtos = body["notifications"] as List<Map<String, Any>>
                dtos.size shouldBe 2
            }
        }

        When("marking one specific notification as read") {
            val target = notification("n_1", "user_1", isRead = false)
            every { notificationRepository.findById("n_1") } returns Optional.of(target)
            val saved = slot<Notification>()
            every { notificationRepository.save(capture(saved)) } answers { saved.captured }

            Then("it flips isRead and saves it") {
                controller.markAsRead("n_1", auth)
                saved.captured.isRead shouldBe true
            }
        }

        When("marking a notification that belongs to a different user") {
            val theirs = notification("n_9", "someone_else", isRead = false)
            every { notificationRepository.findById("n_9") } returns Optional.of(theirs)

            Then("it never saves -- ownership check blocks it before any write") {
                controller.markAsRead("n_9", auth)
                verify(exactly = 0) { notificationRepository.save(any()) }
            }
        }

        When("marking \"all\" as read") {
            val mine = listOf(notification("n_1", "user_1", isRead = false), notification("n_2", "user_1", isRead = false))
            every { notificationRepository.findByUserIdOrderByCreatedAtDesc("user_1") } returns mine
            val saved = slot<List<Notification>>()
            every { notificationRepository.saveAll(capture(saved)) } answers { saved.captured }

            Then("it batch-saves every unread notification as read") {
                controller.markAsRead("all", auth)
                saved.captured.size shouldBe 2
                saved.captured.all { it.isRead } shouldBe true
            }
        }
    }
}) {
    // Same reasoning as LedgerServiceTest.kt/MerchantServiceTest.kt: fresh fixtures per
    // leaf test so a captured slot in one When can't leak into a sibling test.
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
