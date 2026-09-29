package rw.itunda.messaging

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.auth.RateLimiter
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.realtime.RealtimeMessagePublisher
import rw.itunda.core.repository.ContactRepository
import rw.itunda.core.repository.ConversationPreferenceRepository
import rw.itunda.core.repository.ConversationRepository
import rw.itunda.core.repository.MessageReactionRepository
import rw.itunda.core.repository.MessageRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserBlockRepository
import rw.itunda.core.repository.UserRepository

// Extracted into its own file rather than added to the already-large
// MessagingServiceTest.kt (2026-09-11, file-size-lint: that file would have
// crossed 500 lines for the first time) -- this test's setup shares nothing
// with MessagingServiceTest.kt's own single shared "two real itunda users"
// Given block (fresh mocks, no reused fixtures), so a new file is a real
// boundary, not a forced split of coupled logic.
//
// Real total-unread-count fix -- see MessagingService.getTotalUnreadCount's
// own doc comment: the web tab badge previously summed unreadCount across
// only listConversations' own first page, undercounting for any user with
// more than 20 real conversations. This is the real, unbounded aggregate.
class MessagingServiceUnreadCountTest : BehaviorSpec({

    Given("a real user with unread messages across more conversations than fit on one page") {
        val conversationRepository = mockk<ConversationRepository>()
        val messageRepository = mockk<MessageRepository>()
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val messageReactionRepository = mockk<MessageReactionRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val realtimeMessagePublisher = mockk<RealtimeMessagePublisher>(relaxed = true)
        val userBlockRepository = mockk<UserBlockRepository>(relaxed = true)
        val contactRepository = mockk<ContactRepository>(relaxed = true)
        val conversationPreferenceRepository = mockk<ConversationPreferenceRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = MessagingService(conversationRepository, messageRepository, userRepository, notificationRepository, messageReactionRepository, rateLimiter, realtimeMessagePublisher, userBlockRepository, contactRepository, conversationPreferenceRepository, pushNotificationService)
        every { messageRepository.countTotalUnreadForUser("user_a") } returns 47L

        When("the real total unread count is requested") {
            val total = service.getTotalUnreadCount("user_a")

            Then("it returns the real unbounded total, not capped at any page size") {
                total shouldBe 47L
            }
        }
    }
})
