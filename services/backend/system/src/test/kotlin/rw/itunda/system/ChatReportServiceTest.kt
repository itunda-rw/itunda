package rw.itunda.system

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Conversation
import rw.itunda.core.domain.Message
import rw.itunda.core.repository.ChatReportRepository
import rw.itunda.core.repository.ConversationRepository
import rw.itunda.core.repository.MessageRepository
import java.util.Optional

/**
 * First test coverage for ChatReportService -- see ChatReportController's own doc
 * comment for the real per-message reporting feature this backs. Confirms the real
 * bug found live 2026-08-02 (report() had zero rate limiting despite every other real
 * content/report-creation endpoint in this codebase already having one) is closed,
 * and that the participant-only IDOR check real-404s a non-participant the same way
 * it real-404s a genuinely missing message.
 */
class ChatReportServiceTest : BehaviorSpec({

    fun message(id: String, conversationId: String) = Message(
        id = id, conversationId = conversationId, senderId = "sender_1", body = "hello",
    )

    fun conversation(id: String) = Conversation(
        id = id, participantAId = "user_1", participantBId = "user_2",
    )

    Given("a real participant reporting a real message in their own conversation") {
        val chatReportRepository = mockk<ChatReportRepository>()
        val messageRepository = mockk<MessageRepository>()
        val conversationRepository = mockk<ConversationRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = ChatReportService(chatReportRepository, messageRepository, conversationRepository, rateLimiter)

        every { messageRepository.findById("msg_1") } returns Optional.of(message("msg_1", "conv_1"))
        every { conversationRepository.findById("conv_1") } returns Optional.of(conversation("conv_1"))
        every { chatReportRepository.findByReporterUserIdAndMessageIdAndStatus(any(), any(), any()) } returns null
        every { chatReportRepository.save(any()) } answers { firstArg() }

        When("they file a real report") {
            val result = service.report("user_1", "msg_1", "Harassment")

            Then("it real-saves the report") {
                result.messageId shouldBe "msg_1"
                result.reporterUserId shouldBe "user_1"
            }

            Then("the real anti-spam rate limit was checked") {
                verify { rateLimiter.checkLimit("chat-report:user_1", any(), any()) }
            }
        }
    }

    Given("a non-participant trying to report a message in someone else's conversation") {
        val chatReportRepository = mockk<ChatReportRepository>()
        val messageRepository = mockk<MessageRepository>()
        val conversationRepository = mockk<ConversationRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = ChatReportService(chatReportRepository, messageRepository, conversationRepository, rateLimiter)

        every { messageRepository.findById("msg_2") } returns Optional.of(message("msg_2", "conv_2"))
        every { conversationRepository.findById("conv_2") } returns Optional.of(conversation("conv_2"))

        When("the attacker reports it anyway") {
            Then("it real-404s (ChatReportForbiddenException, mapped identically to the not-found case), never confirming the message exists") {
                shouldThrow<ChatReportForbiddenException> {
                    service.report("attacker", "msg_2", "Trying to probe")
                }
                verify(exactly = 0) { chatReportRepository.save(any()) }
            }
        }
    }

    Given("a user who has already hit the real chat-report rate limit") {
        val chatReportRepository = mockk<ChatReportRepository>()
        val messageRepository = mockk<MessageRepository>()
        val conversationRepository = mockk<ConversationRepository>()
        val rateLimiter = mockk<RateLimiter>()
        every { rateLimiter.checkLimit("chat-report:user_3", any(), any()) } throws RateLimitExceededException("Too many requests")
        val service = ChatReportService(chatReportRepository, messageRepository, conversationRepository, rateLimiter)

        When("they try to file yet another report") {
            Then("it real-429s before ever touching the message lookup or the repository") {
                shouldThrow<RateLimitExceededException> {
                    service.report("user_3", "msg_3", "Spam")
                }
                verify(exactly = 0) { messageRepository.findById(any()) }
                verify(exactly = 0) { chatReportRepository.save(any()) }
            }
        }
    }
})
