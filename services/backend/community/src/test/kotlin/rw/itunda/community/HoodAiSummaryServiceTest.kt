package rw.itunda.community

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import rw.itunda.core.ai.AiSummaryClient
import rw.itunda.core.domain.CommunityPost
import rw.itunda.core.repository.CommunityPostRepository
import rw.itunda.core.repository.GroupConversationMemberRepository
import rw.itunda.core.repository.GroupMemberCount
import java.time.Instant
import java.time.temporal.ChronoUnit

class HoodAiSummaryServiceTest : BehaviorSpec({

    Given("a real meetup with a real, substantive body") {
        val postRepository = mockk<CommunityPostRepository>()
        val groupConversationMemberRepository = mockk<GroupConversationMemberRepository>()
        val aiSummaryClient = mockk<AiSummaryClient>()
        val service = HoodAiSummaryService(postRepository, groupConversationMemberRepository, aiSummaryClient)

        val post = CommunityPost(
            id = "post_1", authorId = "author_1", category = "meetup", title = "Weekend running club",
            body = "We meet every Saturday morning at 7am for a real 5k run around the neighborhood, then coffee after.",
            groupConversationId = "group_1", commentCount = 4,
        )

        When("generating with real member/comment counts available") {
            every { groupConversationMemberRepository.countMembersByGroupConversationIds(listOf("group_1")) } returns
                listOf(object : GroupMemberCount { override val groupConversationId = "group_1"; override val memberCount = 12L })
            val promptSlot = slot<String>()
            every { aiSummaryClient.complete(any(), capture(promptSlot), any()) } returns "A weekly Saturday running group with real coffee afterward."

            val summary = service.generateSummaryFor(post)

            Then("it returns the real model output, grounded in real facts only") {
                summary shouldBe "A weekly Saturday running group with real coffee afterward."
                promptSlot.captured shouldBe
                    "Title: Weekend running club\n" +
                        "Description: We meet every Saturday morning at 7am for a real 5k run around the neighborhood, then coffee after.\n" +
                        "Real member count: 12\nReal comment count: 4"
            }
        }
    }

    Given("a real meetup with a genuinely thin body -- the real fabrication risk this session's Maps pass found live") {
        val postRepository = mockk<CommunityPostRepository>()
        val groupConversationMemberRepository = mockk<GroupConversationMemberRepository>()
        val aiSummaryClient = mockk<AiSummaryClient>()
        val service = HoodAiSummaryService(postRepository, groupConversationMemberRepository, aiSummaryClient)

        val thinPost = CommunityPost(id = "post_2", authorId = "author_1", category = "meetup", title = "Meetup", body = "come")

        When("generating") {
            val summary = service.generateSummaryFor(thinPost)

            Then("it honestly declines rather than risking the model inventing an activity/vibe from almost nothing") {
                summary shouldBe null
            }
        }
    }

    Given("a real non-meetup post") {
        val postRepository = mockk<CommunityPostRepository>()
        val groupConversationMemberRepository = mockk<GroupConversationMemberRepository>()
        val aiSummaryClient = mockk<AiSummaryClient>()
        val service = HoodAiSummaryService(postRepository, groupConversationMemberRepository, aiSummaryClient)

        val questionPost = CommunityPost(id = "post_3", authorId = "author_1", category = "question", title = "Q", body = "A real, long enough question body here")

        When("generating") {
            val summary = service.generateSummaryFor(questionPost)

            Then("it never generates a summary for a non-meetup post") {
                summary shouldBe null
            }
        }
    }

    Given("the real AI client isn't configured") {
        val postRepository = mockk<CommunityPostRepository>()
        val groupConversationMemberRepository = mockk<GroupConversationMemberRepository>(relaxed = true)
        val aiSummaryClient = mockk<AiSummaryClient>()
        val service = HoodAiSummaryService(postRepository, groupConversationMemberRepository, aiSummaryClient)
        every { aiSummaryClient.isConfigured } returns false

        When("running the real batch job") {
            val generated = service.generateMissing()

            Then("it real-no-ops rather than attempting any real API calls") {
                generated shouldBe 0
            }
        }
    }

    Given("a real batch of posts, one already-summarized meetup, one stale meetup, one missing meetup, and one non-meetup post") {
        val postRepository = mockk<CommunityPostRepository>()
        val groupConversationMemberRepository = mockk<GroupConversationMemberRepository>(relaxed = true)
        val aiSummaryClient = mockk<AiSummaryClient>()
        val service = HoodAiSummaryService(postRepository, groupConversationMemberRepository, aiSummaryClient)
        every { aiSummaryClient.isConfigured } returns true
        every { aiSummaryClient.complete(any(), any(), any()) } returns "Real generated summary."

        val freshBody = "A real, substantive body long enough to summarize honestly without inventing anything."
        val fresh = CommunityPost(id = "p_fresh", authorId = "o1", category = "meetup", title = "Fresh", body = freshBody)
        fresh.aiSummary = "Already summarized."
        fresh.aiSummaryGeneratedAt = Instant.now()
        val stale = CommunityPost(id = "p_stale", authorId = "o2", category = "meetup", title = "Stale", body = freshBody)
        stale.aiSummary = "Old summary."
        stale.aiSummaryGeneratedAt = Instant.now().minus(30, ChronoUnit.DAYS)
        val missing = CommunityPost(id = "p_missing", authorId = "o3", category = "meetup", title = "Missing", body = freshBody)
        val notMeetup = CommunityPost(id = "p_question", authorId = "o4", category = "question", title = "Q", body = freshBody)

        every { postRepository.findAll() } returns listOf(fresh, stale, missing, notMeetup)
        every { postRepository.save(any()) } answers { firstArg() }

        When("running the real batch job") {
            val generated = service.generateMissing()

            Then("it real-regenerates the stale and missing meetups, leaves the fresh one alone, and never touches the non-meetup post") {
                generated shouldBe 2
                stale.aiSummary shouldBe "Real generated summary."
                missing.aiSummary shouldBe "Real generated summary."
                fresh.aiSummary shouldBe "Already summarized."
                notMeetup.aiSummary shouldBe null
            }
        }
    }
})
