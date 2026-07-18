package rw.itunda.messaging

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Conversation
import rw.itunda.core.domain.Message
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.User
import rw.itunda.core.realtime.RealtimeMessagePublisher
import rw.itunda.core.repository.ConversationRepository
import rw.itunda.core.repository.MessageRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import java.util.Optional

class MessagingServiceTest : BehaviorSpec({

    fun user(id: String, first: String) =
        User(id = id, phoneNumber = "+2507880000$id", firstName = first, lastName = "Test", passwordHash = "hash")

    Given("two real itunda users") {
        val conversationRepository = mockk<ConversationRepository>()
        val messageRepository = mockk<MessageRepository>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val realtimeMessagePublisher = mockk<RealtimeMessagePublisher>(relaxed = true)
        val service = MessagingService(conversationRepository, messageRepository, userRepository, notificationRepository, rateLimiter, realtimeMessagePublisher)

        When("starting a conversation between user_a and user_b for the first time") {
            every { userRepository.findById("user_b") } returns Optional.of(user("user_b", "Beata"))
            every { conversationRepository.findByParticipantAIdAndParticipantBId("user_a", "user_b") } returns null
            val savedSlot = slot<Conversation>()
            every { conversationRepository.save(capture(savedSlot)) } answers { firstArg() }

            val conversation = service.startOrGetConversation("user_a", "user_b")

            Then("it canonically sorts the pair so the same conversation is found from either direction") {
                conversation.participantAId shouldBe "user_a"
                conversation.participantBId shouldBe "user_b"
            }
        }

        When("starting a conversation the other direction (user_b -> user_a) after it already exists") {
            val existing = Conversation(id = "conversation_1", participantAId = "user_a", participantBId = "user_b")
            every { userRepository.findById("user_a") } returns Optional.of(user("user_a", "Alice"))
            every { conversationRepository.findByParticipantAIdAndParticipantBId("user_a", "user_b") } returns existing

            val conversation = service.startOrGetConversation("user_b", "user_a")

            Then("it returns the exact same conversation rather than creating a duplicate") {
                conversation.id shouldBe "conversation_1"
            }
        }

        When("starting a conversation with yourself") {
            Then("it throws SelfConversationException") {
                try {
                    service.startOrGetConversation("user_a", "user_a")
                    error("expected SelfConversationException")
                } catch (e: SelfConversationException) {
                    // expected
                }
            }
        }

        When("starting a conversation with a phone number that has no real itunda account") {
            every { userRepository.findById("ghost") } returns Optional.empty()

            Then("it throws RecipientNotFoundException rather than creating a conversation with a non-existent user") {
                try {
                    service.startOrGetConversation("user_a", "ghost")
                    error("expected RecipientNotFoundException")
                } catch (e: RecipientNotFoundException) {
                    // expected
                }
            }
        }

        When("starting a conversation by the other person's real phone number") {
            every { userRepository.findByPhoneNumber("+250788999111") } returns user("user_b", "Beata")
            every { userRepository.findById("user_b") } returns Optional.of(user("user_b", "Beata"))
            every { conversationRepository.findByParticipantAIdAndParticipantBId("user_a", "user_b") } returns null
            every { conversationRepository.save(any()) } answers { firstArg() }

            val conversation = service.startOrGetConversationByPhoneNumber("user_a", "+250788999111")

            Then("it resolves the phone number to that user's real account and starts a real conversation with them") {
                conversation.participantAId shouldBe "user_a"
                conversation.participantBId shouldBe "user_b"
            }
        }

        When("starting a conversation by a phone number with no real itunda account") {
            every { userRepository.findByPhoneNumber("+250700000000") } returns null

            Then("it throws RecipientNotFoundException") {
                try {
                    service.startOrGetConversationByPhoneNumber("user_a", "+250700000000")
                    error("expected RecipientNotFoundException")
                } catch (e: RecipientNotFoundException) {
                    // expected
                }
            }
        }

        When("sending a message in a conversation you're a real participant of") {
            val conversation = Conversation(id = "conversation_1", participantAId = "user_a", participantBId = "user_b")
            every { conversationRepository.findById("conversation_1") } returns Optional.of(conversation)
            every { conversationRepository.save(any()) } answers { firstArg() }
            every { userRepository.findById("user_a") } returns Optional.of(user("user_a", "Alice"))
            every { messageRepository.save(any()) } answers { firstArg() }
            val notifSlot = slot<Notification>()
            every { notificationRepository.save(capture(notifSlot)) } answers { firstArg() }

            val message = service.sendMessage("user_a", "conversation_1", "  Hey there  ")

            Then("it trims the body, persists it, and notifies the OTHER participant, not the sender") {
                message.body shouldBe "Hey there"
                message.senderId shouldBe "user_a"
                notifSlot.captured.userId shouldBe "user_b"
            }

            Then("it real-time-pushes the trimmed message to the OTHER participant, not the sender") {
                verify { realtimeMessagePublisher.publishNewMessage("conversation_1", "user_b", message) }
            }
        }

        When("sending an empty/whitespace-only message") {
            Then("it throws EmptyMessageException before even looking up the conversation") {
                try {
                    service.sendMessage("user_a", "conversation_1", "   ")
                    error("expected EmptyMessageException")
                } catch (e: EmptyMessageException) {
                    // expected
                }
            }
        }

        When("a non-participant tries to send a message into someone else's conversation") {
            val conversation = Conversation(id = "conversation_2", participantAId = "user_a", participantBId = "user_b")
            every { conversationRepository.findById("conversation_2") } returns Optional.of(conversation)

            Then("it throws ConversationNotFoundException, not a 403 that would confirm the conversation exists") {
                try {
                    service.sendMessage("user_stranger", "conversation_2", "sneaky")
                    error("expected ConversationNotFoundException")
                } catch (e: ConversationNotFoundException) {
                    // expected
                }
            }
        }

        When("listing conversations with a real last-message preview and unread count") {
            val conversation = Conversation(id = "conversation_1", participantAId = "user_a", participantBId = "user_b")
            every { conversationRepository.findByParticipant("user_a", PageRequest.of(0, 20)) } returns
                PageImpl(listOf(conversation), PageRequest.of(0, 20), 1)
            every { userRepository.findById("user_b") } returns Optional.of(user("user_b", "Beata"))
            val lastMessage = Message(id = "message_1", conversationId = "conversation_1", senderId = "user_b", body = "hi!")
            every { messageRepository.findByConversationIdOrderBySentAtDesc("conversation_1", any()) } returns
                PageImpl(listOf(lastMessage))
            every { messageRepository.countByConversationIdAndSenderIdNotAndReadAtIsNull("conversation_1", "user_a") } returns 3L

            val page = service.listConversations("user_a", PageRequest.of(0, 20))

            Then("the summary shows the OTHER participant, the last message, and a real unread count") {
                page.content.size shouldBe 1
                page.content[0].otherUserId shouldBe "user_b"
                page.content[0].otherUserName shouldBe "Beata Test"
                page.content[0].lastMessagePreview shouldBe "hi!"
                page.content[0].unreadCount shouldBe 3L
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
