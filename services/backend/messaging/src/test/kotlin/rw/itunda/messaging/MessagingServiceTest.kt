package rw.itunda.messaging

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import io.mockk.every
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Conversation
import rw.itunda.core.domain.ConversationPreference
import rw.itunda.core.domain.Message
import rw.itunda.core.domain.MessageReaction
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.User
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.realtime.ReactionGroup
import rw.itunda.core.realtime.RealtimeMessagePublisher
import rw.itunda.core.repository.ConversationRepository
import rw.itunda.core.repository.MessageReactionRepository
import rw.itunda.core.repository.MessageRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.UserBlockRepository
import rw.itunda.core.repository.ContactRepository
import rw.itunda.core.repository.ConversationPreferenceRepository
import java.time.Duration
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
        val userBlockRepository = mockk<UserBlockRepository>(relaxed = true)
        val contactRepository = mockk<ContactRepository>(relaxed = true)
        val conversationPreferenceRepository = mockk<ConversationPreferenceRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = MessagingService(conversationRepository, messageRepository, userRepository, notificationRepository, messageReactionRepository, rateLimiter, realtimeMessagePublisher, userBlockRepository, contactRepository, conversationPreferenceRepository, pushNotificationService)

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

            Then("the OTHER participant also gets a real mobile push notification, not just the in-app one") {
                verify(exactly = 1) { pushNotificationService.sendToUser("user_b", "Alice Test", "Hey there", any()) }
            }
        }

        When("the recipient has made a room quiet") {
            clearMocks(notificationRepository, answers = false)
            val conversation = Conversation(id = "conversation_quiet", participantAId = "user_a", participantBId = "user_b")
            every { conversationRepository.findById("conversation_quiet") } returns Optional.of(conversation)
            every { conversationRepository.save(any()) } answers { firstArg() }
            every { userRepository.findById("user_a") } returns Optional.of(user("user_a", "Alice"))
            every { messageRepository.save(any()) } answers { firstArg() }
            every { conversationPreferenceRepository.findByConversationIdAndUserId("conversation_quiet", "user_b") } returns
                ConversationPreference("preference_1", "conversation_quiet", "user_b", quiet = true)

            service.sendMessage("user_a", "conversation_quiet", "No alert please")

            Then("it preserves delivery without creating a new-message alert") {
                verify(exactly = 0) { notificationRepository.save(match { it.userId == "user_b" && it.type == "NEW_MESSAGE" }) }
                verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any()) }
            }
        }

        When("a participant pins a message from their own conversation") {
            val conversation = Conversation(id = "conversation_pin", participantAId = "user_a", participantBId = "user_b")
            val message = Message(id = "message_pin", conversationId = "conversation_pin", senderId = "user_b", body = "Meet at 4")
            every { conversationRepository.findById("conversation_pin") } returns Optional.of(conversation)
            every { conversationRepository.save(any()) } answers { firstArg() }
            every { messageRepository.findById("message_pin") } returns Optional.of(message)

            service.setPinnedMessage("user_a", "conversation_pin", "message_pin")

            Then("it stores and returns the shared pin only to a participant") {
                conversation.pinnedMessageId shouldBe "message_pin"
                service.getPinnedMessage("user_b", "conversation_pin")?.id shouldBe "message_pin"
            }
        }

        When("a participant tries to pin a message from another conversation") {
            val conversation = Conversation(id = "conversation_pin_safe", participantAId = "user_a", participantBId = "user_b")
            val otherMessage = Message(id = "message_elsewhere", conversationId = "conversation_elsewhere", senderId = "user_b", body = "Private")
            every { conversationRepository.findById("conversation_pin_safe") } returns Optional.of(conversation)
            every { messageRepository.findById("message_elsewhere") } returns Optional.of(otherMessage)

            Then("it rejects the cross-conversation reference") {
                shouldThrow<MessageNotFoundException> {
                    service.setPinnedMessage("user_a", "conversation_pin_safe", "message_elsewhere")
                }
            }
        }

        When("the sender deletes a direct message") {
            val conversation = Conversation(id = "conversation_delete", participantAId = "user_a", participantBId = "user_b")
            val message = Message(id = "message_delete", conversationId = "conversation_delete", senderId = "user_a", body = "remove this")
            every { conversationRepository.findById("conversation_delete") } returns Optional.of(conversation)
            every { messageRepository.findById("message_delete") } returns Optional.of(message)
            every { messageRepository.save(any()) } answers { firstArg() }

            service.deleteMessage("user_a", "conversation_delete", "message_delete")

            Then("it retains the auditable row but marks its content deleted") {
                (message.deletedAt != null) shouldBe true
                message.deletedByUserId shouldBe "user_a"
            }
        }

        When("a user pins a conversation to the top for the first time (no preference row yet)") {
            val conversation = Conversation(id = "conversation_pin_top_new", participantAId = "user_a", participantBId = "user_b")
            every { conversationRepository.findById("conversation_pin_top_new") } returns Optional.of(conversation)
            every { conversationPreferenceRepository.findByConversationIdAndUserId("conversation_pin_top_new", "user_a") } returns null
            val savedSlot = slot<ConversationPreference>()
            every { conversationPreferenceRepository.save(capture(savedSlot)) } answers { firstArg() }

            service.setConversationPinnedToTop("user_a", "conversation_pin_top_new", true)

            Then("it creates a real preference row with pinned=true, private to this user") {
                savedSlot.captured.userId shouldBe "user_a"
                savedSlot.captured.conversationId shouldBe "conversation_pin_top_new"
                savedSlot.captured.pinned shouldBe true
            }
        }

        When("a user unpins an already-pinned conversation") {
            val conversation = Conversation(id = "conversation_unpin", participantAId = "user_a", participantBId = "user_b")
            val preference = ConversationPreference("preference_pin_1", "conversation_unpin", "user_a", pinned = true)
            every { conversationRepository.findById("conversation_unpin") } returns Optional.of(conversation)
            every { conversationPreferenceRepository.findByConversationIdAndUserId("conversation_unpin", "user_a") } returns preference
            every { conversationPreferenceRepository.save(any()) } answers { firstArg() }

            service.setConversationPinnedToTop("user_a", "conversation_unpin", false)

            Then("it flips the existing row back to false rather than deleting it") {
                preference.pinned shouldBe false
            }
        }

        When("a non-participant tries to pin someone else's conversation") {
            val conversation = Conversation(id = "conversation_pin_safe_2", participantAId = "user_a", participantBId = "user_b")
            every { conversationRepository.findById("conversation_pin_safe_2") } returns Optional.of(conversation)

            Then("it rejects with the same non-disclosing 404 as every other conversation preference check") {
                shouldThrow<ConversationNotFoundException> {
                    service.setConversationPinnedToTop("user_c", "conversation_pin_safe_2", true)
                }
            }
        }

        When("a recipient tries to delete another person's direct message") {
            val conversation = Conversation(id = "conversation_delete_owner", participantAId = "user_a", participantBId = "user_b")
            val message = Message(id = "message_delete_owner", conversationId = "conversation_delete_owner", senderId = "user_a", body = "keep")
            every { conversationRepository.findById("conversation_delete_owner") } returns Optional.of(conversation)
            every { messageRepository.findById("message_delete_owner") } returns Optional.of(message)

            Then("it rejects the ownership violation") {
                shouldThrow<MessageDeleteForbiddenException> {
                    service.deleteMessage("user_b", "conversation_delete_owner", "message_delete_owner")
                }
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

        When("sending a message longer than the real 2000-char DB column bound") {
            Then("it throws MessageTooLongException rather than risking a raw DB insert failure") {
                try {
                    service.sendMessage("user_a", "conversation_1", "x".repeat(2001))
                    error("expected MessageTooLongException")
                } catch (e: MessageTooLongException) {
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
            every { conversationRepository.findByParticipantNotArchived("user_a", PageRequest.of(0, 20)) } returns
                PageImpl(listOf(conversation), PageRequest.of(0, 20), 1)
            every { userRepository.findAllById(listOf("user_b")) } returns listOf(user("user_b", "Beata"))
            val lastMessage = Message(id = "message_1", conversationId = "conversation_1", senderId = "user_b", body = "hi!")
            every { messageRepository.findByConversationIdInOrderBySentAtDesc(listOf("conversation_1"), any()) } returns listOf(lastMessage)
            every { messageRepository.countUnreadByConversationIds(listOf("conversation_1"), "user_a") } returns
                listOf(object : rw.itunda.core.repository.ConversationUnreadCount {
                    override val conversationId = "conversation_1"
                    override val unreadCount = 3L
                })

            val page = service.listConversations("user_a", PageRequest.of(0, 20))

            Then("the summary shows the OTHER participant, the last message, and a real unread count") {
                page.content.size shouldBe 1
                page.content[0].otherUserId shouldBe "user_b"
                page.content[0].otherUserName shouldBe "Beata Test"
                page.content[0].lastMessagePreview shouldBe "hi!"
                page.content[0].unreadCount shouldBe 3L
            }
        }

        When("listing conversations where one has been pinned to top") {
            val pinnedConversation = Conversation(id = "conversation_pinned_list", participantAId = "user_a", participantBId = "user_b")
            every { conversationRepository.findByParticipantNotArchived("user_a", PageRequest.of(0, 20)) } returns
                PageImpl(listOf(pinnedConversation), PageRequest.of(0, 20), 1)
            every { userRepository.findAllById(listOf("user_b")) } returns listOf(user("user_b", "Beata"))
            every { messageRepository.findByConversationIdInOrderBySentAtDesc(listOf("conversation_pinned_list"), any()) } returns emptyList()
            every { messageRepository.countUnreadByConversationIds(listOf("conversation_pinned_list"), "user_a") } returns emptyList()
            every { conversationPreferenceRepository.findByUserIdAndConversationIdIn("user_a", listOf("conversation_pinned_list")) } returns
                listOf(ConversationPreference("preference_pinned_list", "conversation_pinned_list", "user_a", pinned = true))

            val page = service.listConversations("user_a", PageRequest.of(0, 20))

            Then("the summary reflects this user's own real pinned-to-top preference") {
                page.content[0].pinnedToTop shouldBe true
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

        When("a real participant exceeds the real reaction rate limit") {
            every {
                rateLimiter.checkLimit("messaging:reaction:user_a", limit = 60, window = Duration.ofMinutes(1))
            } throws RateLimitExceededException("Too many requests")

            Then("it real-propagates RateLimitExceededException, found missing in a 2026-07-19 security review") {
                try {
                    service.toggleReaction("user_a", "message_1", "👍")
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    // expected
                }
            }
        }

        val today = java.time.LocalDate.now(java.time.ZoneId.of("Africa/Kigali"))
        val notToday = today.plusDays(1).let { if (it.monthValue == today.monthValue) today.minusDays(1) else it }

        fun contact(id: String, name: String, phone: String) =
            rw.itunda.core.domain.Contact(id = id, userId = "user_a", name = name, bank = "BK", acc = "0000", phoneNumber = phone, color = "#000", letter = "T")

        When("a real saved contact's birthday is today") {
            every { contactRepository.findByUserId("user_a") } returns listOf(contact("contact_1", "Birthday Beata", "+250780000002"))
            every { userRepository.findAllByPhoneNumberIn(listOf("+250780000002")) } returns listOf(
                user("user_b", "Beata").apply { phoneNumber = "+250780000002"; birthDate = today },
            )

            val birthdays = service.getTodaysBirthdays("user_a")

            Then("it appears in the real Today's Birthday list, using the caller's own saved name") {
                birthdays.size shouldBe 1
                birthdays[0].userId shouldBe "user_b"
                birthdays[0].name shouldBe "Birthday Beata"
            }
        }

        When("a real saved contact's birthday is a different day") {
            every { contactRepository.findByUserId("user_a") } returns listOf(contact("contact_2", "Not Today Chantal", "+250780000003"))
            every { userRepository.findAllByPhoneNumberIn(listOf("+250780000003")) } returns listOf(
                user("user_c", "Chantal").apply { phoneNumber = "+250780000003"; birthDate = notToday },
            )

            Then("it is correctly excluded from the real Today's Birthday list") {
                service.getTodaysBirthdays("user_a").size shouldBe 0
            }
        }

        When("a real saved contact has never set a birth date") {
            every { contactRepository.findByUserId("user_a") } returns listOf(contact("contact_3", "No Birthday David", "+250780000004"))
            every { userRepository.findAllByPhoneNumberIn(listOf("+250780000004")) } returns listOf(
                user("user_d", "David").apply { phoneNumber = "+250780000004" },
            )

            Then("it is safely excluded, not a null-pointer crash") {
                service.getTodaysBirthdays("user_a").size shouldBe 0
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
