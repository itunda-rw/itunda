package rw.itunda.messaging

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
import rw.itunda.core.push.PushNotificationService
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

    fun user(id: String, first: String, phoneNumber: String = "+2507880000$id") =
        User(id = id, phoneNumber = phoneNumber, firstName = first, lastName = "Test", passwordHash = "hash")

    Given("a real user creating a real group") {
        val groupConversationRepository = mockk<GroupConversationRepository>()
        val groupConversationMemberRepository = mockk<GroupConversationMemberRepository>(relaxed = true)
        val groupMessageRepository = mockk<GroupMessageRepository>(relaxed = true)
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

        When("creating a group with two real other members") {
            every { userRepository.findAllById(listOf("user_b", "user_c")) } returns listOf(user("user_b", "Beata"), user("user_c", "Claude"))
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

        When("creating a group with a name longer than the real 100-char DB column bound") {
            Then("it throws GroupNameTooLongException rather than risking a raw DB insert failure") {
                try {
                    service.createGroup("user_a", "x".repeat(101), listOf("user_b"))
                    error("expected GroupNameTooLongException")
                } catch (e: GroupNameTooLongException) {
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
            every { userRepository.findAllById(listOf("ghost")) } returns emptyList()

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
            every { userRepository.findAllByPhoneNumberIn(listOf("+250780000002")) } returns listOf(user("user_b", "Beata", "+250780000002"))
            val savedSlot = slot<GroupConversation>()
            every { groupConversationRepository.save(capture(savedSlot)) } answers { firstArg() }

            val group = service.createGroupByPhoneNumbers("user_a", "Phone Group", listOf("+250780000002"))

            Then("it resolves the real phone number to the real member") {
                group.name shouldBe "Phone Group"
                verify { groupConversationMemberRepository.saveAll(match<List<GroupConversationMember>> { it.map { m -> m.userId }.toSet() == setOf("user_a", "user_b") }) }
            }
        }

        When("creating a group by an unknown phone number") {
            every { userRepository.findAllByPhoneNumberIn(listOf("+250780000099")) } returns emptyList()

            Then("it throws GroupMemberNotFoundException") {
                try {
                    service.createGroupByPhoneNumbers("user_a", "Group", listOf("+250780000099"))
                    error("expected GroupMemberNotFoundException")
                } catch (e: GroupMemberNotFoundException) {
                    // expected
                }
            }
        }

        // Real bug found live (2026-08-02): group creation had shipped with zero rate
        // limiting -- every other real content-creation endpoint in this codebase
        // already carries one; an authenticated caller could otherwise spam unlimited
        // GroupConversation + member rows.
        When("creating a group by member id, checking the real per-user rate limit") {
            every { userRepository.findAllById(listOf("user_b")) } returns listOf(user("user_b", "Beata"))
            every { groupConversationRepository.save(any()) } answers { firstArg() }

            service.createGroup("user_a", "Rate Limit Check", listOf("user_b"))

            Then("it checks the real limit before ever touching the repository") {
                verify(exactly = 1) { rateLimiter.checkLimit("messaging:group-create:user_a", limit = 20, window = Duration.ofHours(1)) }
            }
        }

        When("a real user exceeds the real group-creation rate limit, by member id") {
            every {
                rateLimiter.checkLimit("messaging:group-create:user_a", limit = 20, window = Duration.ofHours(1))
            } throws RateLimitExceededException("Too many requests")

            Then("it real-propagates RateLimitExceededException, never silently creating the group") {
                try {
                    service.createGroup("user_a", "Group", listOf("user_b"))
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    // expected
                }
                verify(exactly = 0) { groupConversationRepository.save(any()) }
            }
        }

        When("a real user exceeds the real group-creation rate limit, by phone number") {
            every { userRepository.findAllByPhoneNumberIn(listOf("+250780000002")) } returns listOf(user("user_b", "Beata", "+250780000002"))
            every {
                rateLimiter.checkLimit("messaging:group-create:user_a", limit = 20, window = Duration.ofHours(1))
            } throws RateLimitExceededException("Too many requests")

            Then("the phone-number entry point real-propagates the same rate limit") {
                try {
                    service.createGroupByPhoneNumbers("user_a", "Group", listOf("+250780000002"))
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    // expected
                }
                verify(exactly = 0) { groupConversationRepository.save(any()) }
            }
        }
    }

    Given("a real KakaoTalk 오픈채팅-style open group, and a stranger with its real join code") {
        val groupConversationRepository = mockk<GroupConversationRepository>()
        val groupConversationMemberRepository = mockk<GroupConversationMemberRepository>(relaxed = true)
        val groupMessageRepository = mockk<GroupMessageRepository>(relaxed = true)
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

        When("creating an open group") {
            every { groupConversationRepository.existsByJoinCode(any()) } returns false
            val savedSlot = slot<GroupConversation>()
            every { groupConversationRepository.save(capture(savedSlot)) } answers { firstArg() }
            // Real, explicit stub, not relaxed=true's default -- same known "relaxed
            // mockk can't correctly infer JpaRepository's generic save() signature"
            // gotcha this codebase's own tests already document repeatedly.
            every { groupConversationMemberRepository.save(any()) } answers { firstArg() }

            val group = service.createOpenGroup("user_a", "  Kigali Devs  ")

            Then("it trims the name and generates a real 6-character join code, unlike an ordinary invite-only group") {
                group.name shouldBe "Kigali Devs"
                group.joinCode shouldNotBe null
                group.joinCode!!.length shouldBe 6
                verify { groupConversationMemberRepository.save(match<GroupConversationMember> { it.userId == "user_a" }) }
            }
        }

        When("a stranger (never invited) joins using the real code") {
            val group = GroupConversation(id = "group_open_1", name = "Kigali Devs", createdBy = "user_a", joinCode = "ABC234")
            every { groupConversationRepository.findByJoinCode("ABC234") } returns group
            every { groupConversationMemberRepository.findByGroupConversationIdAndUserId("group_open_1", "user_z") } returns null
            every { groupConversationMemberRepository.save(any()) } answers { firstArg() }

            val joined = service.joinByCode("user_z", "abc234")

            Then("it succeeds -- lowercase input normalized, no prior invite/membership needed") {
                joined.id shouldBe "group_open_1"
                verify { groupConversationMemberRepository.save(match<GroupConversationMember> { it.userId == "user_z" && it.groupConversationId == "group_open_1" }) }
            }
        }

        When("a real existing member re-joins using the same code") {
            val group = GroupConversation(id = "group_open_1", name = "Kigali Devs", createdBy = "user_a", joinCode = "ABC234")
            every { groupConversationRepository.findByJoinCode("ABC234") } returns group
            every { groupConversationMemberRepository.findByGroupConversationIdAndUserId("group_open_1", "user_a") } returns
                GroupConversationMember(id = "group_member_existing", groupConversationId = "group_open_1", userId = "user_a")

            service.joinByCode("user_a", "ABC234")

            Then("it's a quiet no-op, not a duplicate member row or an error") {
                verify(exactly = 0) { groupConversationMemberRepository.save(match<GroupConversationMember> { it.userId == "user_a" }) }
            }
        }

        When("joining with a code that matches no real open group") {
            every { groupConversationRepository.findByJoinCode("ZZZZZZ") } returns null

            Then("it throws InvalidGroupJoinCodeException") {
                try {
                    service.joinByCode("user_z", "ZZZZZZ")
                    error("expected InvalidGroupJoinCodeException")
                } catch (e: InvalidGroupJoinCodeException) {
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
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = GroupMessagingService(
            groupConversationRepository, groupConversationMemberRepository, groupMessageRepository,
            userRepository, notificationRepository, groupMessageReactionRepository, rateLimiter, realtimeMessagePublisher,
            pushNotificationService,
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
            // `<S extends T> S save(S)`/`saveAll` signature, throwing a real
            // ClassCastException back in the caller (same known gotcha
            // MerchantServiceTest/OrderServiceTest/EatsOrderServiceTest already document).
            every { notificationRepository.saveAll(any<List<rw.itunda.core.domain.Notification>>()) } answers { firstArg() }
            every { groupConversationMemberRepository.findByGroupConversationId("group_1") } returns members
            every { userRepository.findById("user_a") } returns Optional.of(user("user_a", "Alice"))
            // parseMentions resolves every real member's name to check for @mentions,
            // even when the message has none -- see GroupMessagingService.parseMentions's
            // own doc comment.
            every { userRepository.findAllById(any<List<String>>()) } returns listOf(user("user_a", "Alice"), user("user_b", "Beata"), user("user_c", "Claude"))

            val message = service.sendMessage("user_a", "group_1", "  Hey everyone  ")

            Then("it trims the body, persists it, and fans the real push out to every OTHER real member") {
                message.body shouldBe "Hey everyone"
                message.senderId shouldBe "user_a"
                verify { realtimeMessagePublisher.publishNewGroupMessage("group_1", match { it.toSet() == setOf("user_b", "user_c") }, message) }
                verify { notificationRepository.saveAll(match<List<rw.itunda.core.domain.Notification>> { it.size == 2 && it.map { n -> n.userId }.toSet() == setOf("user_b", "user_c") }) }
            }

            Then("an ordinary message with no @mention never sends a real push -- only mentions do, to avoid spamming every message") {
                verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any()) }
            }
        }

        When("a real member sends a message that real @mentions another real member") {
            every { groupConversationRepository.findById("group_1") } returns Optional.of(group)
            every { groupConversationMemberRepository.findByGroupConversationIdAndUserId("group_1", "user_a") } returns members[0]
            every { groupConversationRepository.save(any()) } answers { firstArg() }
            every { groupMessageRepository.save(any()) } answers { firstArg() }
            every { notificationRepository.saveAll(any<List<rw.itunda.core.domain.Notification>>()) } answers { firstArg() }
            every { groupConversationMemberRepository.findByGroupConversationId("group_1") } returns members
            every { userRepository.findById("user_a") } returns Optional.of(user("user_a", "Alice"))
            every { userRepository.findAllById(any<List<String>>()) } returns listOf(user("user_a", "Alice"), user("user_b", "Beata"), user("user_c", "Claude"))

            service.sendMessage("user_a", "group_1", "Hey @Beata check this out")

            Then("only the real mentioned member gets a real push, not the whole group") {
                verify(exactly = 1) { pushNotificationService.sendToUser("user_b", "Alice Test mentioned you in Kigali Friends", any(), any()) }
                verify(exactly = 0) { pushNotificationService.sendToUser("user_c", any(), any(), any()) }
            }
        }

        When("sending a group message longer than the real 2000-char DB column bound") {
            Then("it throws GroupMessageTooLongException before even looking up the group") {
                try {
                    service.sendMessage("user_a", "group_1", "x".repeat(2001))
                    error("expected GroupMessageTooLongException")
                } catch (e: GroupMessageTooLongException) {
                    // expected
                }
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

        When("the sender deletes a group message") {
            val groupMessage = GroupMessage(id = "group_message_delete", groupConversationId = "group_1", senderId = "user_a", body = "remove this")
            every { groupMessageRepository.findById("group_message_delete") } returns Optional.of(groupMessage)
            every { groupConversationRepository.findById("group_1") } returns Optional.of(group)
            every { groupConversationMemberRepository.findByGroupConversationIdAndUserId("group_1", "user_a") } returns members[0]
            every { groupMessageRepository.save(any()) } answers { firstArg() }

            service.deleteMessage("user_a", "group_1", "group_message_delete")

            Then("it preserves the row but marks it deleted") {
                (groupMessage.deletedAt != null) shouldBe true
                groupMessage.deletedByUserId shouldBe "user_a"
            }
        }

        When("a different group member tries to delete someone else's message") {
            val groupMessage = GroupMessage(id = "group_message_owner", groupConversationId = "group_1", senderId = "user_b", body = "keep")
            every { groupMessageRepository.findById("group_message_owner") } returns Optional.of(groupMessage)
            every { groupConversationRepository.findById("group_1") } returns Optional.of(group)
            every { groupConversationMemberRepository.findByGroupConversationIdAndUserId("group_1", "user_a") } returns members[0]

            Then("it rejects the ownership violation") {
                try { service.deleteMessage("user_a", "group_1", "group_message_owner"); error("expected GroupMessageDeleteForbiddenException") }
                catch (_: GroupMessageDeleteForbiddenException) { }
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

        When("a real member fetches messages, advancing their own real read cursor") {
            val memberSlot = slot<GroupConversationMember>()
            every { groupConversationRepository.findById("group_1") } returns Optional.of(group)
            every { groupConversationMemberRepository.findByGroupConversationIdAndUserId("group_1", "user_a") } returns members[0]
            every { groupConversationMemberRepository.findByGroupConversationId("group_1") } returns members
            every { groupConversationMemberRepository.save(capture(memberSlot)) } answers { firstArg() }
            every { groupMessageRepository.findByGroupConversationIdOrderBySentAtDesc("group_1", any()) } returns
                org.springframework.data.domain.PageImpl(emptyList())

            service.getMessages("user_a", "group_1", org.springframework.data.domain.Pageable.unpaged())

            Then("it real-advances the caller's own lastReadAt and live-pushes the change to every OTHER real member") {
                memberSlot.captured.lastReadAt shouldNotBe null
                verify { realtimeMessagePublisher.publishGroupReadReceiptChange("group_1", match { it.toSet() == setOf("user_b", "user_c") }, "user_a", any()) }
            }
        }

        When("a real member pins a real message in the group") {
            val pinMessage = GroupMessage(id = "group_message_pin", groupConversationId = "group_1", senderId = "user_b", body = "Pin me")
            every { groupConversationRepository.findById("group_1") } returns Optional.of(group)
            every { groupConversationMemberRepository.findByGroupConversationIdAndUserId("group_1", "user_a") } returns members[0]
            every { groupMessageRepository.findById("group_message_pin") } returns Optional.of(pinMessage)
            every { groupConversationRepository.save(any()) } answers { firstArg() }

            service.setPinnedMessage("user_a", "group_1", "group_message_pin")

            Then("it stores and returns the shared pin to any real member") {
                group.pinnedMessageId shouldBe "group_message_pin"
                every { groupConversationMemberRepository.findByGroupConversationIdAndUserId("group_1", "user_b") } returns members[1]
                service.getPinnedMessage("user_b", "group_1")?.id shouldBe "group_message_pin"
            }
        }

        When("a real member tries to pin a message from a different group") {
            val otherGroupMessage = GroupMessage(id = "group_message_elsewhere", groupConversationId = "group_elsewhere", senderId = "user_b", body = "Private")
            every { groupConversationRepository.findById("group_1") } returns Optional.of(group)
            every { groupConversationMemberRepository.findByGroupConversationIdAndUserId("group_1", "user_a") } returns members[0]
            every { groupMessageRepository.findById("group_message_elsewhere") } returns Optional.of(otherGroupMessage)

            Then("it rejects the cross-group reference, same real IDOR discipline as 1:1 pin") {
                try {
                    service.setPinnedMessage("user_a", "group_1", "group_message_elsewhere")
                    error("expected GroupMessageNotFoundException")
                } catch (e: GroupMessageNotFoundException) {
                    // expected
                }
            }
        }

        When("computing the real per-message unread countdown from real per-member cursors") {
            val now = java.time.Instant.now()
            val readMembers = listOf(
                GroupConversationMember(id = "gm_a", groupConversationId = "group_1", userId = "user_a", lastReadAt = now),
                // user_b's cursor is AFTER the message's sentAt -- they've already caught up.
                GroupConversationMember(id = "gm_b", groupConversationId = "group_1", userId = "user_b", lastReadAt = now),
                // user_c's cursor is BEFORE the message's sentAt -- still unread.
                GroupConversationMember(id = "gm_c", groupConversationId = "group_1", userId = "user_c", lastReadAt = now.minusSeconds(10)),
            )
            every { groupConversationMemberRepository.findByGroupConversationId("group_1") } returns readMembers
            val message = GroupMessage(id = "group_message_1", groupConversationId = "group_1", senderId = "user_a", body = "Hey", sentAt = now.minusSeconds(5))

            val counts = service.getUnreadCounts("group_1", listOf(message))

            Then("it counts only OTHER members whose real cursor is still behind this message's sentAt -- user_b already caught up, user_c hasn't, the sender never counts against themself") {
                counts["group_message_1"] shouldBe 1
            }
        }
    }

    // Real group photo/description (2026-07-28) -- see GroupMessagingService
    // .setGroupPhotoUrl/setGroupDescription's own doc comments.
    Given("a real group and a real member of it") {
        val groupConversationRepository = mockk<GroupConversationRepository>()
        val groupConversationMemberRepository = mockk<GroupConversationMemberRepository>()
        val groupMessageRepository = mockk<GroupMessageRepository>(relaxed = true)
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

        val group = GroupConversation(id = "group_1", name = "Kigali Friends", createdBy = "user_a")
        val memberA = GroupConversationMember(id = "gm_a", groupConversationId = "group_1", userId = "user_a")

        every { groupConversationRepository.findById("group_1") } returns Optional.of(group)
        every { groupConversationMemberRepository.findByGroupConversationIdAndUserId("group_1", "user_a") } returns memberA
        every { groupConversationMemberRepository.findByGroupConversationIdAndUserId("group_1", "user_stranger") } returns null
        every { groupConversationRepository.save(any()) } answers { firstArg() }

        When("any real member sets a real group photo -- flat membership, not gated to createdBy") {
            val result = service.setGroupPhotoUrl("user_a", "group_1", "  https://uploads.example/group.jpg  ")

            Then("it real-trims and saves the URL") {
                result.photoUrl shouldBe "https://uploads.example/group.jpg"
            }
        }

        When("a real member sets a real group description") {
            val result = service.setGroupDescription("user_a", "group_1", "  Friends from Kigali  ")

            Then("it real-trims and saves the description") {
                result.description shouldBe "Friends from Kigali"
            }
        }

        When("a blank photo URL is submitted") {
            val result = service.setGroupPhotoUrl("user_a", "group_1", "   ")

            Then("it real-clears the photo back to unset rather than storing an empty string") {
                result.photoUrl shouldBe null
            }
        }

        When("a photo URL over the real 2048-character bound is submitted") {
            Then("it throws GroupPhotoUrlTooLongException before ever touching the group row") {
                try {
                    service.setGroupPhotoUrl("user_a", "group_1", "x".repeat(2049))
                    error("expected GroupPhotoUrlTooLongException")
                } catch (e: GroupPhotoUrlTooLongException) {
                    verify(exactly = 0) { groupConversationRepository.save(any()) }
                }
            }
        }

        When("a description over the real 500-character bound is submitted") {
            Then("it throws GroupDescriptionTooLongException before ever touching the group row") {
                try {
                    service.setGroupDescription("user_a", "group_1", "x".repeat(501))
                    error("expected GroupDescriptionTooLongException")
                } catch (e: GroupDescriptionTooLongException) {
                    verify(exactly = 0) { groupConversationRepository.save(any()) }
                }
            }
        }

        When("someone who isn't a real member tries to set the group photo") {
            Then("it real-404s rather than revealing the group exists") {
                try {
                    service.setGroupPhotoUrl("user_stranger", "group_1", "https://uploads.example/group.jpg")
                    error("expected GroupNotFoundException")
                } catch (e: GroupNotFoundException) {
                    // expected
                }
            }
        }
    }

    // Real 1:1-chat split-bill support (2026-08-09) -- see
    // getOrCreateDirectSplitGroup's own doc comment.
    Given("two real users splitting a bill 1:1, with no existing hidden group between them") {
        val groupConversationRepository = mockk<GroupConversationRepository>()
        val groupConversationMemberRepository = mockk<GroupConversationMemberRepository>(relaxed = true)
        val groupMessageRepository = mockk<GroupMessageRepository>(relaxed = true)
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

        When("resolving a direct split group for the first time") {
            every { groupConversationRepository.findDirectGroupBetween("user_a", "user_b") } returns null
            every { userRepository.findById("user_b") } returns Optional.of(user("user_b", "Beata"))
            val savedSlot = slot<GroupConversation>()
            every { groupConversationRepository.save(capture(savedSlot)) } answers { firstArg() }

            val group = service.getOrCreateDirectSplitGroup("user_a", "user_b")

            Then("it creates a real, hidden 2-person group") {
                group.isDirect shouldBe true
                group.createdBy shouldBe "user_a"
                group.name shouldBe "Split with Beata"
                savedSlot.captured.isDirect shouldBe true
                verify { groupConversationMemberRepository.saveAll(match<List<GroupConversationMember>> { it.size == 2 && it.map { m -> m.userId }.toSet() == setOf("user_a", "user_b") }) }
            }

            Then("it rate-limits the creation the same way createGroup/createGroupByPhoneNumbers do") {
                verify(exactly = 1) { rateLimiter.checkLimit("messaging:group-create:user_a", limit = 20, window = Duration.ofHours(1)) }
            }
        }

        When("the group-creation rate limit is exceeded") {
            every { groupConversationRepository.findDirectGroupBetween("user_a", "user_b") } returns null
            every {
                rateLimiter.checkLimit("messaging:group-create:user_a", limit = 20, window = Duration.ofHours(1))
            } throws RateLimitExceededException("Too many requests")

            Then("it real-propagates RateLimitExceededException before ever creating a hidden group") {
                try {
                    service.getOrCreateDirectSplitGroup("user_a", "user_b")
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { groupConversationRepository.save(any()) }
                }
            }
        }

        When("resolving a direct split group that already exists between the same two people") {
            val existing = GroupConversation(id = "group_direct_1", name = "Split with Beata", createdBy = "user_a", isDirect = true)
            every { groupConversationRepository.findDirectGroupBetween("user_a", "user_b") } returns existing

            val group = service.getOrCreateDirectSplitGroup("user_a", "user_b")

            Then("it reuses the existing hidden group instead of creating a second one") {
                group.id shouldBe "group_direct_1"
                verify(exactly = 0) { groupConversationRepository.save(any()) }
            }

            Then("it does NOT rate-limit a repeat split between an already-paired-up couple") {
                verify(exactly = 0) { rateLimiter.checkLimit("messaging:group-create:user_a", limit = 20, window = Duration.ofHours(1)) }
            }
        }

        When("someone tries to resolve a direct split group with themselves") {
            Then("it throws GroupNeedsMoreMembersException rather than creating a 1-person group") {
                try {
                    service.getOrCreateDirectSplitGroup("user_a", "user_a")
                    error("expected GroupNeedsMoreMembersException")
                } catch (e: GroupNeedsMoreMembersException) {
                    verify(exactly = 0) { groupConversationRepository.save(any()) }
                }
            }
        }

        When("the other user doesn't have a real itunda account") {
            every { groupConversationRepository.findDirectGroupBetween("user_a", "user_ghost") } returns null
            every { userRepository.findById("user_ghost") } returns Optional.empty()

            Then("it throws GroupMemberNotFoundException rather than creating a group with a fake member") {
                try {
                    service.getOrCreateDirectSplitGroup("user_a", "user_ghost")
                    error("expected GroupMemberNotFoundException")
                } catch (e: GroupMemberNotFoundException) {
                    verify(exactly = 0) { groupConversationRepository.save(any()) }
                }
            }
        }
    }

    Given("a real member searching messages in their own group") {
        val groupConversationRepository = mockk<GroupConversationRepository>()
        val groupConversationMemberRepository = mockk<GroupConversationMemberRepository>(relaxed = true)
        val groupMessageRepository = mockk<GroupMessageRepository>(relaxed = true)
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
        val group = GroupConversation(id = "group_1", name = "Kigali Friends", createdBy = "user_a")
        every { groupConversationRepository.findById("group_1") } returns Optional.of(group)
        every { groupConversationMemberRepository.findByGroupConversationIdAndUserId("group_1", "user_a") } returns
            GroupConversationMember(id = "member_1", groupConversationId = "group_1", userId = "user_a")

        When("they search for a real match") {
            service.searchMessages("user_a", "group_1", "hello", org.springframework.data.domain.PageRequest.of(0, 30))

            Then("it real-scopes the search to that group, after the normal membership check") {
                verify { groupMessageRepository.searchByGroupConversationIdAndBody("group_1", "hello", any()) }
            }
        }

        When("the query is too short") {
            Then("it throws InvalidGroupMessageSearchException before ever touching the repository") {
                try {
                    service.searchMessages("user_a", "group_1", "h", org.springframework.data.domain.PageRequest.of(0, 30))
                    error("expected InvalidGroupMessageSearchException")
                } catch (e: InvalidGroupMessageSearchException) {
                    verify(exactly = 0) { groupMessageRepository.searchByGroupConversationIdAndBody(any(), any(), any()) }
                }
            }
        }
    }

    Given("a non-member trying to search messages in someone else's group") {
        val groupConversationRepository = mockk<GroupConversationRepository>()
        val groupConversationMemberRepository = mockk<GroupConversationMemberRepository>(relaxed = true)
        val groupMessageRepository = mockk<GroupMessageRepository>(relaxed = true)
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
        val group = GroupConversation(id = "group_2", name = "Someone else's group", createdBy = "user_b")
        every { groupConversationRepository.findById("group_2") } returns Optional.of(group)
        every { groupConversationMemberRepository.findByGroupConversationIdAndUserId("group_2", "attacker") } returns null

        When("the attacker searches it anyway") {
            Then("it real-404s (GroupNotFoundException) rather than confirming the group exists") {
                try {
                    service.searchMessages("attacker", "group_2", "hello", org.springframework.data.domain.PageRequest.of(0, 30))
                    error("expected GroupNotFoundException")
                } catch (e: GroupNotFoundException) {
                    verify(exactly = 0) { groupMessageRepository.searchByGroupConversationIdAndBody(any(), any(), any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
