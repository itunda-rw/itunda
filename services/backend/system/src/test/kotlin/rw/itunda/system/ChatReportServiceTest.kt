package rw.itunda.system

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.ChatReport
import rw.itunda.core.domain.Conversation
import rw.itunda.core.domain.HoodReportStatus
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

    Given("an ops reviewer opening the real chat-report queue") {
        val chatReportRepository = mockk<ChatReportRepository>()
        val messageRepository = mockk<MessageRepository>()
        val conversationRepository = mockk<ConversationRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = ChatReportService(chatReportRepository, messageRepository, conversationRepository, rateLimiter)
        val pageable = PageRequest.of(0, 30)
        val openReport = ChatReport("chat_report_1", "user_1", "msg_1", "Harassment")
        every { chatReportRepository.findByStatusOrderByCreatedAtAsc(HoodReportStatus.OPEN, pageable) } returns PageImpl(listOf(openReport))

        When("they fetch the queue") {
            val result = service.queue(pageable)

            Then("it real-returns only OPEN reports, oldest first") {
                result.content shouldBe listOf(openReport)
            }
        }
    }

    Given("an ops reviewer dismissing a chat report without touching the message") {
        val chatReportRepository = mockk<ChatReportRepository>()
        val messageRepository = mockk<MessageRepository>()
        val conversationRepository = mockk<ConversationRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = ChatReportService(chatReportRepository, messageRepository, conversationRepository, rateLimiter)
        val report = ChatReport("chat_report_2", "user_1", "msg_2", "Spam")
        every { chatReportRepository.findById("chat_report_2") } returns Optional.of(report)
        every { chatReportRepository.save(any()) } answers { firstArg() }

        When("they resolve it") {
            val result = service.resolve("chat_report_2", "reviewer_1")

            Then("it real-marks the report RESOLVED and attributes the reviewer, leaving the message alone") {
                result.status shouldBe HoodReportStatus.RESOLVED
                result.reviewedBy shouldBe "reviewer_1"
                verify(exactly = 0) { messageRepository.findById(any()) }
            }
        }
    }

    Given("an ops reviewer removing a reported message") {
        val chatReportRepository = mockk<ChatReportRepository>()
        val messageRepository = mockk<MessageRepository>()
        val conversationRepository = mockk<ConversationRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = ChatReportService(chatReportRepository, messageRepository, conversationRepository, rateLimiter)
        val report = ChatReport("chat_report_3", "user_1", "msg_3", "Abuse")
        val message = message("msg_3", "conv_3")
        every { chatReportRepository.findById("chat_report_3") } returns Optional.of(report)
        every { messageRepository.findById("msg_3") } returns Optional.of(message)
        every { messageRepository.save(any()) } answers { firstArg() }
        every { chatReportRepository.save(any()) } answers { firstArg() }

        When("they remove the message") {
            val result = service.removeMessage("chat_report_3", "reviewer_1")

            Then("it real-soft-deletes the message, attributed to the reviewer, and resolves the report") {
                message.deletedByUserId shouldBe "reviewer_1"
                verify { messageRepository.save(message) }
                result.status shouldBe HoodReportStatus.RESOLVED
            }
        }

        When("the message was already removed") {
            val alreadyDeletedMessageRepository = mockk<MessageRepository>()
            val alreadyDeletedChatReportRepository = mockk<ChatReportRepository>()
            val alreadyDeletedService = ChatReportService(alreadyDeletedChatReportRepository, alreadyDeletedMessageRepository, conversationRepository, rateLimiter)
            val alreadyDeleted = message("msg_4", "conv_4")
            alreadyDeleted.deletedAt = java.time.Instant.parse("2026-01-01T00:00:00Z")
            alreadyDeleted.deletedByUserId = "someone_else"
            val secondReport = ChatReport("chat_report_4", "user_1", "msg_4", "Abuse")
            every { alreadyDeletedChatReportRepository.findById("chat_report_4") } returns Optional.of(secondReport)
            every { alreadyDeletedMessageRepository.findById("msg_4") } returns Optional.of(alreadyDeleted)
            every { alreadyDeletedChatReportRepository.save(any()) } answers { firstArg() }

            Then("it real-skips re-deleting (no double-attribution), but still resolves the report") {
                alreadyDeletedService.removeMessage("chat_report_4", "reviewer_2")
                verify(exactly = 0) { alreadyDeletedMessageRepository.save(any()) }
                alreadyDeleted.deletedByUserId shouldBe "someone_else"
            }
        }
    }
})
