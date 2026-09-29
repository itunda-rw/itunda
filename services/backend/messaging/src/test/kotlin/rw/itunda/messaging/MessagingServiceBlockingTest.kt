package rw.itunda.messaging

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Conversation
import rw.itunda.core.domain.User
import rw.itunda.core.domain.UserBlock
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.realtime.RealtimeMessagePublisher
import rw.itunda.core.repository.ConversationPreferenceRepository
import rw.itunda.core.repository.ConversationRepository
import rw.itunda.core.repository.ContactRepository
import rw.itunda.core.repository.MessageReactionRepository
import rw.itunda.core.repository.MessageRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserBlockRepository
import rw.itunda.core.repository.UserRepository
import java.util.Optional

/**
 * Real zero-test-coverage gap found live (2026-09-14, test-coverage sweep):
 * blockConversationParticipant/unblockConversationParticipant are the real, live
 * routes MessagingController's block/unblock endpoints call, and requireNotBlocked
 * (the actual enforcement) had zero coverage for either the creation, removal, or
 * enforcement of a real block -- every test in MessagingServiceTest.kt uses a
 * relaxed userBlockRepository that silently defaults to "not blocked", never
 * exercising the throw branch at all. Split into its own file (rather than grown
 * into MessagingServiceTest.kt directly) since that file had just crossed the
 * 500-line new-file threshold -- see docs/AI_AGENT_SELF_CHECK.md.
 */
class MessagingServiceBlockingTest : BehaviorSpec({

    fun user(id: String, first: String) =
        User(id = id, phoneNumber = "+2507880000$id", firstName = first, lastName = "Test", passwordHash = "hash")

    Given("two real itunda users with an existing conversation") {
        val conversationRepository = mockk<ConversationRepository>()
        val messageRepository = mockk<MessageRepository>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val messageReactionRepository = mockk<MessageReactionRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val realtimeMessagePublisher = mockk<RealtimeMessagePublisher>(relaxed = true)
        val userBlockRepository = mockk<UserBlockRepository>(relaxed = true)
        val contactRepository = mockk<ContactRepository>(relaxed = true)
        val conversationPreferenceRepository = mockk<ConversationPreferenceRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = MessagingService(
            conversationRepository, messageRepository, userRepository, notificationRepository, messageReactionRepository,
            rateLimiter, realtimeMessagePublisher, userBlockRepository, contactRepository, conversationPreferenceRepository,
            pushNotificationService,
        )

        When("a participant blocks the other side of an existing conversation") {
            val conversation = Conversation(id = "conversation_block_1", participantAId = "user_a", participantBId = "user_b")
            every { conversationRepository.findById("conversation_block_1") } returns Optional.of(conversation)
            every { userBlockRepository.existsByBlockerUserIdAndBlockedUserId("user_a", "user_b") } returns false
            val savedSlot = slot<UserBlock>()
            every { userBlockRepository.save(capture(savedSlot)) } answers { firstArg() }

            service.blockConversationParticipant("user_a", "conversation_block_1")

            Then("a real block row is saved against the OTHER participant, never the caller themselves") {
                savedSlot.captured.blockerUserId shouldBe "user_a"
                savedSlot.captured.blockedUserId shouldBe "user_b"
            }
        }

        When("a participant who already blocked the other side blocks them again") {
            val conversation = Conversation(id = "conversation_block_1b", participantAId = "user_a", participantBId = "user_b")
            every { conversationRepository.findById("conversation_block_1b") } returns Optional.of(conversation)
            every { userBlockRepository.existsByBlockerUserIdAndBlockedUserId("user_a", "user_b") } returns true

            service.blockConversationParticipant("user_a", "conversation_block_1b")

            Then("it's a real no-op -- no duplicate block row is ever created") {
                verify(exactly = 0) { userBlockRepository.save(any()) }
            }
        }

        When("a participant unblocks the other side") {
            val conversation = Conversation(id = "conversation_block_2", participantAId = "user_a", participantBId = "user_b")
            every { conversationRepository.findById("conversation_block_2") } returns Optional.of(conversation)
            val existingBlock = UserBlock(id = "user_block_1", blockerUserId = "user_a", blockedUserId = "user_b")
            every { userBlockRepository.findByBlockerUserIdAndBlockedUserId("user_a", "user_b") } returns existingBlock

            service.unblockConversationParticipant("user_a", "conversation_block_2")

            Then("the real block row is deleted") {
                verify(exactly = 1) { userBlockRepository.delete(existingBlock) }
            }
        }

        When("a non-participant tries to block someone in a conversation they're not part of") {
            val conversation = Conversation(id = "conversation_block_3", participantAId = "user_a", participantBId = "user_b")
            every { conversationRepository.findById("conversation_block_3") } returns Optional.of(conversation)

            Then("it throws ConversationNotFoundException -- a real 404, not a 403, same IDOR discipline as every other check") {
                shouldThrow<ConversationNotFoundException> { service.blockConversationParticipant("user_c", "conversation_block_3") }
            }
        }

        When("a blocked user tries to start a conversation with whoever blocked them") {
            every { userRepository.findById("user_a") } returns Optional.of(user("user_a", "Alice"))
            every { userBlockRepository.existsByBlockerUserIdAndBlockedUserId("user_a", "user_b") } returns true
            every { userBlockRepository.existsByBlockerUserIdAndBlockedUserId("user_b", "user_a") } returns false

            Then("it real-throws UserBlockedException -- the block works from EITHER direction") {
                shouldThrow<UserBlockedException> { service.startOrGetConversation("user_b", "user_a") }
            }
        }

        When("neither side has blocked the other") {
            every { userRepository.findById("user_b") } returns Optional.of(user("user_b", "Beata"))
            every { userBlockRepository.existsByBlockerUserIdAndBlockedUserId("user_a", "user_b") } returns false
            every { userBlockRepository.existsByBlockerUserIdAndBlockedUserId("user_b", "user_a") } returns false
            every { conversationRepository.findByParticipantAIdAndParticipantBId("user_a", "user_b") } returns null
            every { conversationRepository.save(any()) } answers { firstArg() }

            Then("starting a real conversation succeeds normally") {
                val conversation = service.startOrGetConversation("user_a", "user_b")
                conversation.participantAId shouldBe "user_a"
                conversation.participantBId shouldBe "user_b"
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
