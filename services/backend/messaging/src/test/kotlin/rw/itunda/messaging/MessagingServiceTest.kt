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
import rw.itunda.core.domain.MessageReaction
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.User
import rw.itunda.core.realtime.ReactionGroup
import rw.itunda.core.realtime.RealtimeMessagePublisher
import rw.itunda.core.repository.ConversationRepository
import rw.itunda.core.repository.MessageReactionRepository
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
        val messageReactionRepository = mockk<MessageReactionRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val realtimeMessagePublisher = mockk<RealtimeMessagePublisher>(relaxed = true)
        val service = MessagingService(conversationRepository, messageRepository, userRepository, notificationRepository, messageReactionRepository, rateLimiter, realtimeMessagePublisher)

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

        When("checking real online/offline presence for a set of user ids") {
            every { realtimeMessagePublisher.isOnline("user_a") } returns true
            every { realtimeMessagePublisher.isOnline("user_b") } returns false

            val presence = service.getPresence(listOf("user_a", "user_b", "user_a"))

            Then("it reads the real session registry via RealtimeMessagePublisher, de-duping the requested ids") {
                presence shouldBe mapOf("user_a" to true, "user_b" to false)
            }
        }

        When("a real participant reacts to a real message for the first time") {
            val conversation = Conversation(id = "conversation_1", participantAId = "user_a", participantBId = "user_b")
            val message = Message(id = "message_1", conversationId = "conversation_1", senderId = "user_b", body = "hi")
            every { messageRepository.findById("message_1") } returns Optional.of(message)
            every { conversationRepository.findById("conversation_1") } returns Optional.of(conversation)
            every { messageReactionRepository.findByMessageIdAndUserIdAndEmoji("message_1", "user_a", "👍") } returns null
            every { messageReactionRepository.save(any()) } answers { firstArg() }
            every { messageReactionRepository.findByMessageId("message_1") } returns listOf(
                MessageReaction(id = "message_reaction_1", messageId = "message_1", userId = "user_a", emoji = "👍"),
            )

            val reactions = service.toggleReaction("user_a", "message_1", "👍")

            Then("it adds the real reaction, pushes it to the real other participant, and returns the real grouped summary") {
                reactions shouldBe listOf(ReactionGroup("👍", listOf("user_a")))
                verify { messageReactionRepository.save(any()) }
                verify { realtimeMessagePublisher.publishReactionChange("conversation_1", "user_b", "message_1", reactions) }
            }
        }

        When("a real participant taps their own already-active reaction again") {
            val conversation = Conversation(id = "conversation_1", participantAId = "user_a", participantBId = "user_b")
            val message = Message(id = "message_1", conversationId = "conversation_1", senderId = "user_b", body = "hi")
            val existingReaction = MessageReaction(id = "message_reaction_1", messageId = "message_1", userId = "user_a", emoji = "👍")
            every { messageRepository.findById("message_1") } returns Optional.of(message)
            every { conversationRepository.findById("conversation_1") } returns Optional.of(conversation)
            every { messageReactionRepository.findByMessageIdAndUserIdAndEmoji("message_1", "user_a", "👍") } returns existingReaction
            every { messageReactionRepository.delete(existingReaction) } returns Unit
            every { messageReactionRepository.findByMessageId("message_1") } returns emptyList()

            val reactions = service.toggleReaction("user_a", "message_1", "👍")

            Then("it real toggles the reaction OFF (deletes it) rather than erroring") {
                reactions shouldBe emptyList()
                verify { messageReactionRepository.delete(existingReaction) }
            }
        }

        When("a stranger (not a real participant) tries to react") {
            val conversation = Conversation(id = "conversation_1", participantAId = "user_a", participantBId = "user_b")
            val message = Message(id = "message_1", conversationId = "conversation_1", senderId = "user_b", body = "hi")
            every { messageRepository.findById("message_1") } returns Optional.of(message)
            every { conversationRepository.findById("conversation_1") } returns Optional.of(conversation)

            Then("it throws ConversationNotFoundException, not a 403 that would confirm the conversation exists") {
                try {
                    service.toggleReaction("stranger", "message_1", "👍")
                    error("expected ConversationNotFoundException")
                } catch (e: ConversationNotFoundException) {
                    // expected
                }
            }
        }

        When("reacting with an empty emoji") {
            Then("it throws InvalidReactionException before even looking up the message") {
                try {
                    service.toggleReaction("user_a", "message_1", "   ")
                    error("expected InvalidReactionException")
                } catch (e: InvalidReactionException) {
                    // expected
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
