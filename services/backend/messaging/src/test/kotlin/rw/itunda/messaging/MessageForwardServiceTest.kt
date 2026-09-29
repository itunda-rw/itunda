package rw.itunda.messaging

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.GroupMessage
import rw.itunda.core.domain.Message

/**
 * Real zero-test-coverage gap found live (2026-09-14, test-coverage sweep): this
 * whole class -- the real, live route both MessagingController and
 * GroupMessagingController's forward endpoints call -- had no test file at all.
 */
class MessageForwardServiceTest : BehaviorSpec({

    Given("a real 1:1 message a user wants to forward") {
        val messagingService = mockk<MessagingService>()
        val groupMessagingService = mockk<GroupMessagingService>()
        val service = MessageForwardService(messagingService, groupMessagingService)

        val sourceMessage = Message(id = "message_1", conversationId = "conv_1", senderId = "user_a", body = "see you at 5pm")
        every { messagingService.getMessageForParticipant("user_a", "message_1") } returns sourceMessage

        When("forwarding it into another real 1:1 conversation") {
            val forwardedMessage = Message(id = "message_2", conversationId = "conv_2", senderId = "user_a", body = "see you at 5pm")
            every {
                messagingService.sendMessage("user_a", "conv_2", "see you at 5pm", forwardedFromMessageId = "message_1", forwardedFromType = "DIRECT")
            } returns forwardedMessage

            val result = service.forward(
                "user_a", MessageDestinationType.DIRECT, "message_1", MessageDestinationType.DIRECT, "conv_2",
            )

            Then("the real source body is carried over verbatim, with real forward provenance -- never a client-asserted string") {
                (result is ForwardResult.Direct) shouldBe true
                (result as ForwardResult.Direct).message.body shouldBe "see you at 5pm"
                verify(exactly = 1) {
                    messagingService.sendMessage("user_a", "conv_2", "see you at 5pm", forwardedFromMessageId = "message_1", forwardedFromType = "DIRECT")
                }
            }
        }

        When("forwarding it into a real group instead") {
            val forwardedGroupMessage = GroupMessage(id = "group_message_1", groupConversationId = "group_1", senderId = "user_a", body = "see you at 5pm")
            every {
                groupMessagingService.sendMessage("user_a", "group_1", "see you at 5pm", forwardedFromMessageId = "message_1", forwardedFromType = "DIRECT")
            } returns forwardedGroupMessage

            val result = service.forward(
                "user_a", MessageDestinationType.DIRECT, "message_1", MessageDestinationType.GROUP, "group_1",
            )

            Then("a real GroupMessage is created with the same carried-over body") {
                (result is ForwardResult.Group) shouldBe true
                (result as ForwardResult.Group).message.body shouldBe "see you at 5pm"
            }
        }

        When("a non-participant tries to forward a message they can't read") {
            every { messagingService.getMessageForParticipant("user_stranger", "message_1") } throws MessageNotFoundException("Message not found")

            Then("it real-throws before ever touching the destination -- read authorization happens server-side, not trusted from the client") {
                try {
                    service.forward("user_stranger", MessageDestinationType.DIRECT, "message_1", MessageDestinationType.DIRECT, "conv_2")
                    error("expected MessageNotFoundException")
                } catch (e: MessageNotFoundException) {
                    verify(exactly = 0) { messagingService.sendMessage(any(), any(), any()) }
                }
            }
        }
    }

    Given("a real group message a member wants to forward") {
        val messagingService = mockk<MessagingService>()
        val groupMessagingService = mockk<GroupMessagingService>()
        val service = MessageForwardService(messagingService, groupMessagingService)

        val sourceGroupMessage = GroupMessage(id = "group_message_5", groupConversationId = "group_1", senderId = "user_b", body = "meeting moved to Friday")
        every { groupMessagingService.getMessageForMember("user_a", "group_message_5") } returns sourceGroupMessage

        When("forwarding it into a real 1:1 conversation") {
            val forwardedMessage = Message(id = "message_3", conversationId = "conv_3", senderId = "user_a", body = "meeting moved to Friday")
            every {
                messagingService.sendMessage("user_a", "conv_3", "meeting moved to Friday", forwardedFromMessageId = "group_message_5", forwardedFromType = "GROUP")
            } returns forwardedMessage

            val result = service.forward(
                "user_a", MessageDestinationType.GROUP, "group_message_5", MessageDestinationType.DIRECT, "conv_3",
            )

            Then("the real group message's body is carried over into the real 1:1 conversation") {
                (result is ForwardResult.Direct) shouldBe true
                (result as ForwardResult.Direct).message.body shouldBe "meeting moved to Friday"
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
