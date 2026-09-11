package rw.itunda.messaging

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.auth.RateLimiter
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.realtime.RealtimeMessagePublisher
import rw.itunda.core.repository.GroupConversationMemberRepository
import rw.itunda.core.repository.GroupConversationRepository
import rw.itunda.core.repository.GroupMessageReactionRepository
import rw.itunda.core.repository.GroupMessageRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import java.time.Instant

// Extracted into its own file rather than added to the already-large
// GroupMessagingServiceTest.kt (2026-09-11, file-size-lint) -- same
// "independent fresh mocks, not a forced split of coupled logic" reasoning
// as MessagingServiceUnreadCountTest.kt's own doc comment.
//
// Real total-unread-count fix -- see GroupMessagingService
// .getTotalUnreadCount's own doc comment: same gap as MessagingService's
// own conversation-side fix, group-chat side.
class GroupMessagingServiceUnreadCountTest : BehaviorSpec({

    Given("a real user with unread group messages across more groups than fit on one page") {
        val groupConversationRepository = mockk<GroupConversationRepository>()
        val groupConversationMemberRepository = mockk<GroupConversationMemberRepository>(relaxed = true)
        val groupMessageRepository = mockk<GroupMessageRepository>()
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val groupMessageReactionRepository = mockk<GroupMessageReactionRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val realtimeMessagePublisher = mockk<RealtimeMessagePublisher>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = GroupMessagingService(
            groupConversationRepository, groupConversationMemberRepository, groupMessageRepository,
            userRepository, notificationRepository, groupMessageReactionRepository, rateLimiter, realtimeMessagePublisher,
            pushNotificationService,
        )
        every { groupMessageRepository.countTotalUnreadForUser("user_a", Instant.EPOCH) } returns 12L

        When("the real total unread count is requested") {
            val total = service.getTotalUnreadCount("user_a")

            Then("it returns the real unbounded total, not capped at any page size") {
                total shouldBe 12L
            }
        }
    }
})
