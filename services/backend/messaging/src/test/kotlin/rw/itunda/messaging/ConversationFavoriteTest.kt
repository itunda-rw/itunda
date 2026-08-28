package rw.itunda.messaging

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Conversation
import rw.itunda.core.domain.ConversationPreference
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
import java.util.Optional

// Real "favorite" chat toggle (itunda Talk redesign, 2026-08-28) -- see
// ConversationPreference.favorite's own doc comment. Extracted into its own file
// (rather than MessagingServiceTest.kt, which this pushed past its file-size-lint
// baseline) mirroring this session's own "extract a cohesive new unit into its own
// file" precedent.
class ConversationFavoriteTest : BehaviorSpec({

    fun newService(): Triple<MessagingService, ConversationRepository, ConversationPreferenceRepository> {
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
        return Triple(service, conversationRepository, conversationPreferenceRepository)
    }

    Given("a real conversation with no preference row yet") {
        val (service, conversationRepository, conversationPreferenceRepository) = newService()
        val conversation = Conversation(id = "conversation_favorite_new", participantAId = "user_a", participantBId = "user_b")
        every { conversationRepository.findById("conversation_favorite_new") } returns Optional.of(conversation)
        every { conversationPreferenceRepository.findByConversationIdAndUserId("conversation_favorite_new", "user_a") } returns null
        val savedSlot = slot<ConversationPreference>()
        every { conversationPreferenceRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("a user favorites it for the first time") {
            service.setConversationFavorite("user_a", "conversation_favorite_new", true)

            Then("it creates a real preference row with favorite=true, private to this user") {
                savedSlot.captured.userId shouldBe "user_a"
                savedSlot.captured.conversationId shouldBe "conversation_favorite_new"
                savedSlot.captured.favorite shouldBe true
            }
        }
    }

    Given("a real conversation already favorited") {
        val (service, conversationRepository, conversationPreferenceRepository) = newService()
        val conversation = Conversation(id = "conversation_unfavorite", participantAId = "user_a", participantBId = "user_b")
        val preference = ConversationPreference("preference_favorite_1", "conversation_unfavorite", "user_a", favorite = true)
        every { conversationRepository.findById("conversation_unfavorite") } returns Optional.of(conversation)
        every { conversationPreferenceRepository.findByConversationIdAndUserId("conversation_unfavorite", "user_a") } returns preference
        every { conversationPreferenceRepository.save(any()) } answers { firstArg() }

        When("a user unfavorites it") {
            service.setConversationFavorite("user_a", "conversation_unfavorite", false)

            Then("it flips the existing row back to false rather than deleting it") {
                preference.favorite shouldBe false
            }
        }
    }
})
