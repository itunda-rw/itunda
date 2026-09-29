package rw.itunda.messaging.web

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.security.CurrentUser
import rw.itunda.messaging.GroupMessagingService
import rw.itunda.messaging.MessageForwardService
import rw.itunda.messaging.MessagingService

// First test coverage for MessagingController -- confirmed zero existing
// coverage via a direct file search before adding this. Scoped narrowly to
// the new getUnreadCount endpoint (2026-09-11, real total-unread-count fix
// -- see MessagingService.getTotalUnreadCount/GroupMessagingService
// .getTotalUnreadCount's own doc comments) rather than attempting the whole
// controller's real ~20-endpoint surface in one pass.
class MessagingControllerTest : BehaviorSpec({

    Given("a real user with unread messages across both 1:1 conversations and groups") {
        val messagingService = mockk<MessagingService>()
        val messageForwardService = mockk<MessageForwardService>()
        val groupMessagingService = mockk<GroupMessagingService>()
        val controller = MessagingController(messagingService, messageForwardService, groupMessagingService)
        val currentUser = CurrentUser(userId = "user_1")
        every { messagingService.getTotalUnreadCount("user_1") } returns 47L
        every { groupMessagingService.getTotalUnreadCount("user_1") } returns 12L

        When("the real combined unread count is requested") {
            val response = controller.getUnreadCount(currentUser)

            Then("it real-delegates to both services and sums the real unbounded totals") {
                verify(exactly = 1) { messagingService.getTotalUnreadCount("user_1") }
                verify(exactly = 1) { groupMessagingService.getTotalUnreadCount("user_1") }
                response.body?.get("conversationsUnread") shouldBe 47L
                response.body?.get("groupsUnread") shouldBe 12L
                response.body?.get("total") shouldBe 59L
            }
        }
    }
})
