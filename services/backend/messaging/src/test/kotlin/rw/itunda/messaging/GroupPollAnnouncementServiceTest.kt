package rw.itunda.messaging

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import rw.itunda.core.domain.GroupConversation
import rw.itunda.core.domain.GroupPoll
import rw.itunda.core.domain.GroupPollOption
import rw.itunda.core.domain.GroupPollVote
import rw.itunda.core.repository.GroupAnnouncementRepository
import rw.itunda.core.repository.GroupPollOptionRepository
import rw.itunda.core.repository.GroupPollRepository
import rw.itunda.core.repository.GroupPollVoteRepository

class GroupPollAnnouncementServiceTest : BehaviorSpec({

    fun newService(): GroupPollAnnouncementService {
        val groupMessagingService = mockk<GroupMessagingService>(relaxed = true)
        val groupAnnouncementRepository = mockk<GroupAnnouncementRepository>(relaxed = true)
        val groupPollRepository = mockk<GroupPollRepository>(relaxed = true)
        val groupPollOptionRepository = mockk<GroupPollOptionRepository>(relaxed = true)
        val groupPollVoteRepository = mockk<GroupPollVoteRepository>(relaxed = true)
        every { groupMessagingService.getGroupForMember(any(), any()) } returns GroupConversation(id = "group_1", name = "Test group", createdBy = "user_1")
        every { groupPollRepository.save(any()) } answers { firstArg() }
        every { groupPollOptionRepository.save(any()) } answers { firstArg() }
        every { groupPollVoteRepository.save(any()) } answers { firstArg() }
        return GroupPollAnnouncementService(groupMessagingService, groupAnnouncementRepository, groupPollRepository, groupPollOptionRepository, groupPollVoteRepository)
    }

    Given("a real group member posting an announcement") {
        val groupMessagingService = mockk<GroupMessagingService>()
        val groupAnnouncementRepository = mockk<GroupAnnouncementRepository>()
        val groupPollRepository = mockk<GroupPollRepository>(relaxed = true)
        val groupPollOptionRepository = mockk<GroupPollOptionRepository>(relaxed = true)
        val groupPollVoteRepository = mockk<GroupPollVoteRepository>(relaxed = true)
        val service = GroupPollAnnouncementService(groupMessagingService, groupAnnouncementRepository, groupPollRepository, groupPollOptionRepository, groupPollVoteRepository)
        every { groupMessagingService.getGroupForMember("user_1", "group_1") } returns GroupConversation(id = "group_1", name = "Test group", createdBy = "user_1")
        every { groupAnnouncementRepository.save(any()) } answers { firstArg() }

        When("the body is real and non-empty") {
            val announcement = service.postAnnouncement("user_1", "group_1", "  Real announcement body  ")

            Then("it real-trims and creates it -- open to any real member, no admin gate") {
                announcement.body shouldBe "Real announcement body"
                announcement.createdBy shouldBe "user_1"
            }
        }

        When("the body is blank") {
            Then("it rejects with a real InvalidGroupAnnouncementException") {
                shouldThrow<InvalidGroupAnnouncementException> { service.postAnnouncement("user_1", "group_1", "   ") }
            }
        }

        When("a non-member tries to post") {
            every { groupMessagingService.getGroupForMember("user_stranger", "group_1") } throws GroupNotFoundException("Group not found")

            Then("it rejects with the same non-disclosing 404 GroupMessagingService already establishes") {
                shouldThrow<GroupNotFoundException> { service.postAnnouncement("user_stranger", "group_1", "hi") }
            }
        }
    }

    Given("a real group member creating a real poll") {
        val service = newService()

        When("real options are given") {
            val result = service.createPoll("user_1", "group_1", "Which day works?", listOf("Mon", "Tue"), allowMultiple = false, closesAt = null)

            Then("it creates a real poll with real options") {
                result.poll.question shouldBe "Which day works?"
                result.options.map { it.text } shouldBe listOf("Mon", "Tue")
            }
        }

        When("fewer than 2 real options are given") {
            Then("it rejects with a real InvalidGroupPollException") {
                shouldThrow<InvalidGroupPollException> { service.createPoll("user_1", "group_1", "Q?", listOf("only one"), false, null) }
            }
        }
    }

    Given("a real single-choice poll a user already voted on") {
        val groupMessagingService = mockk<GroupMessagingService>(relaxed = true)
        val groupAnnouncementRepository = mockk<GroupAnnouncementRepository>(relaxed = true)
        val groupPollRepository = mockk<GroupPollRepository>()
        val groupPollOptionRepository = mockk<GroupPollOptionRepository>()
        val groupPollVoteRepository = mockk<GroupPollVoteRepository>(relaxed = true)
        val service = GroupPollAnnouncementService(groupMessagingService, groupAnnouncementRepository, groupPollRepository, groupPollOptionRepository, groupPollVoteRepository)

        val poll = GroupPoll(id = "poll_1", groupConversationId = "group_1", createdBy = "user_1", question = "Q?", allowMultiple = false)
        val optionA = GroupPollOption(id = "option_a", pollId = "poll_1", text = "A")
        val optionB = GroupPollOption(id = "option_b", pollId = "poll_1", text = "B")
        every { groupPollRepository.findById("poll_1") } returns java.util.Optional.of(poll)
        every { groupPollOptionRepository.findById("option_b") } returns java.util.Optional.of(optionB)
        every { groupPollRepository.findByGroupConversationIdOrderByCreatedAtDesc("group_1") } returns listOf(poll)
        every { groupPollOptionRepository.findByPollIdIn(listOf("poll_1")) } returns listOf(optionA, optionB)
        every { groupPollVoteRepository.findByPollIdIn(listOf("poll_1")) } returns listOf(GroupPollVote(id = "vote_1", pollId = "poll_1", optionId = "option_b", userId = "user_1"))
        every { groupPollVoteRepository.save(any()) } answers { firstArg() }

        When("the same user votes for a different option") {
            service.vote("user_1", "group_1", "poll_1", "option_b")

            Then("it real-deletes their prior vote(s) before inserting the new one -- a real replace, not an additive second vote") {
                io.mockk.verify(exactly = 1) { groupPollVoteRepository.deleteByPollIdAndUserId("poll_1", "user_1") }
            }
        }
    }
})
