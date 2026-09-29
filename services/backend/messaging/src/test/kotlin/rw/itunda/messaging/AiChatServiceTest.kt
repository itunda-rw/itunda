package rw.itunda.messaging

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.ai.AiSummaryClient
import rw.itunda.core.domain.AiChatMessage
import rw.itunda.core.repository.AiChatMessageRepository
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AiChatServiceTest : BehaviorSpec({

    Given("a real user sending a real message to the AI chatbot channel") {
        val aiChatMessageRepository = mockk<AiChatMessageRepository>()
        val aiSummaryClient = mockk<AiSummaryClient>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = AiChatService(aiChatMessageRepository, aiSummaryClient, rateLimiter)

        every { aiChatMessageRepository.save(any()) } answers { firstArg() }
        every { aiChatMessageRepository.findByUserIdOrderByCreatedAtDesc("user_1", PageRequest.of(0, 10)) } returns
            PageImpl(emptyList(), PageRequest.of(0, 10), 0)
        every { aiSummaryClient.completeChat(any(), any()) } returns "A real, grounded reply."

        When("a real completion succeeds") {
            val (userMessage, assistantMessage) = service.sendMessage("user_1", "Hello itunda")

            Then("it persists a real user turn and a real assistant turn") {
                userMessage.role shouldBe "user"
                userMessage.content shouldBe "Hello itunda"
                assistantMessage.role shouldBe "assistant"
                assistantMessage.content shouldBe "A real, grounded reply."
            }
            Then("it real-enforces the per-user cooldown via the existing RateLimiter") {
                verify(exactly = 1) { rateLimiter.checkLimit("ai-chat:user_1", limit = 1, window = any()) }
            }
        }

        When("the per-user cooldown is exceeded") {
            every { rateLimiter.checkLimit("ai-chat:user_1", limit = 1, window = any()) } throws RateLimitExceededException("Too many attempts, please try again later")

            Then("it real-rejects before ever calling the model") {
                shouldThrow<RateLimitExceededException> { service.sendMessage("user_1", "Hi again") }
                verify(exactly = 0) { aiSummaryClient.completeChat(any(), any()) }
            }
        }
    }

    Given("a real completion that fails at the model") {
        val aiChatMessageRepository = mockk<AiChatMessageRepository>()
        val aiSummaryClient = mockk<AiSummaryClient>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = AiChatService(aiChatMessageRepository, aiSummaryClient, rateLimiter)

        every { aiChatMessageRepository.save(any()) } answers { firstArg() }
        every { aiChatMessageRepository.findByUserIdOrderByCreatedAtDesc("user_2", PageRequest.of(0, 10)) } returns
            PageImpl(emptyList(), PageRequest.of(0, 10), 0)
        every { aiSummaryClient.completeChat(any(), any()) } returns null

        When("the client returns null (unconfigured/unreachable/malformed)") {
            val (_, assistantMessage) = service.sendMessage("user_2", "Anyone there?")

            Then("it returns a real, honest fallback message -- never a fabricated reply") {
                assistantMessage.content shouldBe "itunda AI couldn't generate a real response just now -- please try again."
            }
        }
    }

    Given("two concurrent requests against the one shared model instance") {
        // Real test of the single-flight guard: a slow-but-real completeChat call
        // held open by a latch, and a second concurrent sendMessage call that must
        // get the honest busy response rather than blocking or corrupting the first.
        val aiChatMessageRepository = mockk<AiChatMessageRepository>(relaxed = true)
        val aiSummaryClient = mockk<AiSummaryClient>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = AiChatService(aiChatMessageRepository, aiSummaryClient, rateLimiter)

        val firstCallStarted = CountDownLatch(1)
        val releaseFirstCall = CountDownLatch(1)
        every { aiChatMessageRepository.save(any()) } answers { firstArg() }
        every { aiChatMessageRepository.findByUserIdOrderByCreatedAtDesc(any(), any()) } returns
            PageImpl(emptyList(), PageRequest.of(0, 10), 0)
        every { aiSummaryClient.completeChat(any(), any()) } answers {
            firstCallStarted.countDown()
            releaseFirstCall.await(5, TimeUnit.SECONDS)
            "First real reply."
        }

        When("a second real request arrives while the first is still in flight") {
            val executor = Executors.newSingleThreadExecutor()
            val firstResult = executor.submit<Pair<AiChatMessage, AiChatMessage>> { service.sendMessage("user_a", "First") }
            firstCallStarted.await(5, TimeUnit.SECONDS)

            Then("it gets a real AiChatBusyException, never a blocked wait or a fabricated reply") {
                shouldThrow<AiChatBusyException> { service.sendMessage("user_b", "Second") }
            }

            releaseFirstCall.countDown()
            val (_, firstAssistantMessage) = firstResult.get(5, TimeUnit.SECONDS)
            firstAssistantMessage.content shouldBe "First real reply."
            executor.shutdown()
        }
    }
}) {
    // Real, deliberate: several Whens under one Given mix positive
    // (verify(exactly=1)) and negative (verify(exactly=0)) assertions against the
    // same mock -- these must not accumulate call history across sibling leaves,
    // matching this codebase's own MerchantCouponServiceTest.kt precedent.
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
