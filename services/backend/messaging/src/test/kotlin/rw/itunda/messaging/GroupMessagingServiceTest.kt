package rw.itunda.messaging

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.GroupConversation
import rw.itunda.core.domain.GroupConversationMember
import rw.itunda.core.domain.GroupMessage
import rw.itunda.core.domain.GroupMessageReaction
import rw.itunda.core.domain.User
import rw.itunda.core.realtime.ReactionGroup
import rw.itunda.core.realtime.RealtimeMessagePublisher
import rw.itunda.core.repository.GroupConversationMemberRepository
import rw.itunda.core.repository.GroupConversationRepository
import rw.itunda.core.repository.GroupMessageReactionRepository
import rw.itunda.core.repository.GroupMessageRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import java.time.Duration
import java.util.Optional

class GroupMessagingServiceTest : BehaviorSpec({

    fun user(id: String, first: String) =
        User(id = id, phoneNumber = "+2507880000$id", firstName = first, lastName = "Test", passwordHash = "hash")

    Given("a real user creating a real group") {
        val groupConversationRepository = mockk<GroupConversationRepository>()
        val groupConversationMemberRepository = mockk<GroupConversationMemberRepository>(relaxed = true)
        val groupMessageRepository = mockk<GroupMessageRepository>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val groupMessageReactionRepository = mockk<GroupMessageReactionRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val realtimeMessagePublisher = mockk<RealtimeMessagePublisher>(relaxed = true)
        val service = GroupMessagingService(
            groupConversationRepository, groupConversationMemberRepository, groupMessageRepository,
            userRepository, notificationRepository, groupMessageReactionRepository, rateLimiter, realtimeMessagePublisher,
        )

        When("creating a group with two real other members") {
            every { userRepository.findById("user_b") } returns Optional.of(user("user_b", "Beata"))
            every { userRepository.findById("user_c") } returns Optional.of(user("user_c", "Claude"))
            val savedSlot = slot<GroupConversation>()
            every { groupConversationRepository.save(capture(savedSlot)) } answers { firstArg() }

            val group = service.createGroup("user_a", "  Kigali Friends  ", listOf("user_b", "user_c", "user_a"))

            Then("it trims the name, dedupes the creator out of the member list, and includes them anyway") {
                group.name shouldBe "Kigali Friends"
                group.createdBy shouldBe "user_a"
                verify { groupConversationMemberRepository.saveAll(match<List<GroupConversationMember>> { it.size == 3 }) }
            }
        }

        When("creating a group with a blank name") {
            Then("it throws GroupNameRequiredException before looking up any members") {
                try {
                    service.createGroup("user_a", "   ", listOf("user_b"))
                    error("expected GroupNameRequiredException")
                } catch (e: GroupNameRequiredException) {
                    // expected
                }
            }
        }

        When("creating a group with no other real members (just yourself)") {
            Then("it throws GroupNeedsMoreMembersException") {
                try {
                    service.createGroup("user_a", "Solo group", listOf("user_a"))
                    error("expected GroupNeedsMoreMembersException")
                } catch (e: GroupNeedsMoreMembersException) {
                    // expected
                }
            }
        }

        When("inviting a phone number with no real itunda account") {
            every { userRepository.findById("ghost") } returns Optional.empty()

            Then("it throws GroupMemberNotFoundException") {
                try {
                    service.createGroup("user_a", "Group", listOf("ghost"))
                    error("expected GroupMemberNotFoundException")
                } catch (e: GroupMemberNotFoundException) {
                    // expected
                }
            }
        }

        When("creating a group by real phone numbers") {
            every { userRepository.findByPhoneNumber("+250780000002") } returns user("user_b", "Beata")
            val savedSlot = slot<GroupConversation>()
            every { groupConversationRepository.save(capture(savedSlot)) } answers { firstArg() }

            val group = service.createGroupByPhoneNumbers("user_a", "Phone Group", listOf("+250780000002"))

            Then("it resolves the real phone number to the real member") {
                group.name shouldBe "Phone Group"
                verify { groupConversationMemberRepository.saveAll(match<List<GroupConversationMember>> { it.map { m -> m.userId }.toSet() == setOf("user_a", "user_b") }) }
            }
        }

        When("creating a group by an unknown phone number") {
            every { userRepository.findByPhoneNumber("+250780000099") } returns null

            Then("it throws GroupMemberNotFoundException") {
                try {
                    service.createGroupByPhoneNumbers("user_a", "Group", listOf("+250780000099"))
                    error("expected GroupMemberNotFoundException")
                } catch (e: GroupMemberNotFoundException) {
                    // expected
                }
            }
        }
    }

    Given("a real group with three real members") {
        val groupConversationRepository = mockk<GroupConversationRepository>()
        val groupConversationMemberRepository = mockk<GroupConversationMemberRepository>()
        val groupMessageRepository = mockk<GroupMessageRepository>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val groupMessageReactionRepository = mockk<GroupMessageReactionRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val realtimeMessagePublisher = mockk<RealtimeMessagePublisher>(relaxed = true)
        val service = GroupMessagingService(
            groupConversationRepository, groupConversationMemberRepository, groupMessageRepository,
            userRepository, notificationRepository, groupMessageReactionRepository, rateLimiter, realtimeMessagePublisher,
        )

        val group = GroupConversation(id = "group_1", name = "Kigali Friends", createdBy = "user_a")
        val members = listOf(
            GroupConversationMember(id = "gm_a", groupConversationId = "group_1", userId = "user_a"),
            GroupConversationMember(id = "gm_b", groupConversationId = "group_1", userId = "user_b"),
            GroupConversationMember(id = "gm_c", groupConversationId = "group_1", userId = "user_c"),
        )

        When("a real member sends a real message") {
            every { groupConversationRepository.findById("group_1") } returns Optional.of(group)
            every { groupConversationMemberRepository.findByGroupConversationIdAndUserId("group_1", "user_a") } returns members[0]
            every { groupConversationRepository.save(any()) } answers { firstArg() }
            every { groupMessageRepository.save(any()) } answers { firstArg() }
            // Explicit stub even though notificationRepository is relaxed -- mockk's
            // relaxed default can't correctly infer JpaRepository's generic
            // `<S extends T> S save(S)` signature, throwing a real ClassCastException
            // back in the caller (same known gotcha MerchantServiceTest/OrderServiceTest/
            // EatsOrderServiceTest already document).
            every { notificationRepository.save(any()) } answers { firstArg() }
            every { groupConversationMemberRepository.findByGroupConversationId("group_1") } returns members
            every { userRepository.findById("user_a") } returns Optional.of(user("user_a", "Alice"))

            val message = service.sendMessage("user_a", "group_1", "  Hey everyone  ")

            Then("it trims the body, persists it, and fans the real push out to every OTHER real member") {
                message.body shouldBe "Hey everyone"
                message.senderId shouldBe "user_a"
                verify { realtimeMessagePublisher.publishNewGroupMessage("group_1", match { it.toSet() == setOf("user_b", "user_c") }, message) }
                verify(exactly = 2) { notificationRepository.save(any()) }
            }
        }

        When("a non-member tries to send a message into someone else's group") {
            every { groupConversationRepository.findById("group_1") } returns Optional.of(group)
            every { groupConversationMemberRepository.findByGroupConversationIdAndUserId("group_1", "stranger") } returns null

            Then("it throws GroupNotFoundException, not a 403 that would confirm the group exists") {
                try {
                    service.sendMessage("stranger", "group_1", "hi")
                    error("expected GroupNotFoundException")
                } catch (e: GroupNotFoundException) {
                    // expected
                }
            }
        }

        When("a real member adds a real new member") {
            every { groupConversationRepository.findById("group_1") } returns Optional.of(group)
            every { groupConversationMemberRepository.findByGroupConversationIdAndUserId("group_1", "user_a") } returns members[0]
            every { userRepository.findById("user_d") } returns Optional.of(user("user_d", "Dan"))
            every { groupConversationMemberRepository.findByGroupConversationIdAndUserId("group_1", "user_d") } returns null
            every { groupConversationMemberRepository.save(any()) } answers { firstArg() }

            service.addMember("user_a", "group_1", "user_d")

            Then("it real-adds them") {
                verify { groupConversationMemberRepository.save(match<GroupConversationMember> { it.userId == "user_d" }) }
            }
        }

        When("a real member fetches the real group member list") {
            every { groupConversationRepository.findById("group_1") } returns Optional.of(group)
            every { groupConversationMemberRepository.findByGroupConversationIdAndUserId("group_1", "user_a") } returns members[0]
            every { groupConversationMemberRepository.findByGroupConversationId("group_1") } returns members
            every { userRepository.findAllById(listOf("user_a", "user_b", "user_c")) } returns listOf(
                user("user_a", "Alice"), user("user_b", "Beata"), user("user_c", "Chris"),
            )

            val result = service.getMembers("user_a", "group_1")

            Then("it returns every real member with their real resolved display name") {
                result shouldBe listOf(
                    GroupMemberInfo("user_a", "Alice Test"),
                    GroupMemberInfo("user_b", "Beata Test"),
                    GroupMemberInfo("user_c", "Chris Test"),
                )
            }
        }

        When("a non-member tries to fetch someone else's real group member list") {
            every { groupConversationRepository.findById("group_1") } returns Optional.of(group)
            every { groupConversationMemberRepository.findByGroupConversationIdAndUserId("group_1", "stranger") } returns null

            Then("it throws GroupNotFoundException, not a 403 that would confirm the group exists") {
                try {
                    service.getMembers("stranger", "group_1")
                    error("expected GroupNotFoundException")
                } catch (e: GroupNotFoundException) {
                    // expected
                }
            }
        }

        When("a real member tries to add someone who's already a member") {
            every { groupConversationRepository.findById("group_1") } returns Optional.of(group)
            every { groupConversationMemberRepository.findByGroupConversationIdAndUserId("group_1", "user_a") } returns members[0]
            every { userRepository.findById("user_b") } returns Optional.of(user("user_b", "Beata"))
            every { groupConversationMemberRepository.findByGroupConversationIdAndUserId("group_1", "user_b") } returns members[1]

            Then("it throws AlreadyGroupMemberException") {
                try {
                    service.addMember("user_a", "group_1", "user_b")
                    error("expected AlreadyGroupMemberException")
                } catch (e: AlreadyGroupMemberException) {
                    // expected
                }
            }
        }

        When("a real member leaves the group") {
            every { groupConversationMemberRepository.findByGroupConversationIdAndUserId("group_1", "user_b") } returns members[1]
            every { groupConversationMemberRepository.delete(members[1]) } returns Unit

            service.leaveGroup("user_b", "group_1")

            Then("it real-removes their membership row") {
                verify { groupConversationMemberRepository.delete(members[1]) }
            }
        }

        When("listing groups with a real last message, member count, and unread count -- batched (2026-07-19)") {
            every { groupConversationRepository.findByMember("user_a", org.springframework.data.domain.PageRequest.of(0, 20)) } returns
                org.springframework.data.domain.PageImpl(listOf(group), org.springframework.data.domain.PageRequest.of(0, 20), 1)
            every { groupConversationMemberRepository.findByGroupConversationIdInAndUserId(listOf("group_1"), "user_a") } returns
                listOf(members[0])
            val lastMessage = GroupMessage(id = "group_message_9", groupConversationId = "group_1", senderId = "user_b", body = "see you all soon")
            every { groupMessageRepository.findByGroupConversationIdInOrderBySentAtDesc(listOf("group_1"), any()) } returns listOf(lastMessage)
            every { groupConversationMemberRepository.countMembersByGroupConversationIds(listOf("group_1")) } returns
                listOf(object : rw.itunda.core.repository.GroupMemberCount {
                    override val groupConversationId = "group_1"
                    override val memberCount = 3L
                })
            every { groupMessageRepository.countUnread("group_1", "user_a", null) } returns 2L

            val page = service.listMyGroups("user_a", org.springframework.data.domain.PageRequest.of(0, 20))

            Then("the real batched lookups populate the summary without a per-group query for the first three") {
                page.content.size shouldBe 1
                page.content[0].memberCount shouldBe 3
                page.content[0].lastMessagePreview shouldBe "see you all soon"
                page.content[0].unreadCount shouldBe 2L
                verify(exactly = 0) { groupConversationMemberRepository.findByGroupConversationIdAndUserId(any(), any()) }
                verify(exactly = 0) { groupConversationMemberRepository.findByGroupConversationId(any()) }
            }
        }

        When("a real member reacts to a real group message for the first time") {
            val groupMessage = GroupMessage(id = "group_message_1", groupConversationId = "group_1", senderId = "user_b", body = "hi all")
            every { groupMessageRepository.findById("group_message_1") } returns Optional.of(groupMessage)
            every { groupConversationRepository.findById("group_1") } returns Optional.of(group)
            every { groupConversationMemberRepository.findByGroupConversationIdAndUserId("group_1", "user_a") } returns members[0]
            every { groupConversationMemberRepository.findByGroupConversationId("group_1") } returns members
            every { groupMessageReactionRepository.findByGroupMessageIdAndUserIdAndEmoji("group_message_1", "user_a", "👍") } returns null
            every { groupMessageReactionRepository.save(any()) } answers { firstArg() }
            every { groupMessageReactionRepository.findByGroupMessageId("group_message_1") } returns listOf(
                GroupMessageReaction(id = "group_message_reaction_1", groupMessageId = "group_message_1", userId = "user_a", emoji = "👍"),
            )

            val reactions = service.toggleReaction("user_a", "group_message_1", "👍")

            Then("it adds the real reaction and fans the push out to every other real member") {
                reactions shouldBe listOf(ReactionGroup("👍", listOf("user_a")))
                verify { realtimeMessagePublisher.publishGroupReactionChange("group_1", listOf("user_b", "user_c"), "group_message_1", reactions) }
            }
        }

        When("a non-member tries to react to a group message") {
            val groupMessage = GroupMessage(id = "group_message_1", groupConversationId = "group_1", senderId = "user_b", body = "hi all")
            every { groupMessageRepository.findById("group_message_1") } returns Optional.of(groupMessage)
            every { groupConversationRepository.findById("group_1") } returns Optional.of(group)
            every { groupConversationMemberRepository.findByGroupConversationIdAndUserId("group_1", "stranger") } returns null

            Then("it throws GroupNotFoundException, not a 403 that would confirm the group exists") {
                try {
                    service.toggleReaction("stranger", "group_message_1", "👍")
                    error("expected GroupNotFoundException")
                } catch (e: GroupNotFoundException) {
                    // expected
                }
            }
        }

        When("a real member exceeds the real group-reaction rate limit") {
            every {
                rateLimiter.checkLimit("messaging:group-reaction:user_a", limit = 60, window = Duration.ofMinutes(1))
            } throws RateLimitExceededException("Too many requests")

            Then("it real-propagates RateLimitExceededException, found missing in a 2026-07-19 security review") {
                try {
                    service.toggleReaction("user_a", "group_message_1", "👍")
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    // expected
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
