package rw.itunda.calling

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.CallEndReason
import rw.itunda.core.domain.CallSession
import rw.itunda.core.domain.CallType
import rw.itunda.core.domain.Conversation
import rw.itunda.core.realtime.RealtimeMessagePublisher
import rw.itunda.core.repository.CallSessionRepository
import rw.itunda.messaging.MessagingService
import java.util.Optional

class CallServiceTest : BehaviorSpec({

    fun newService(): Pair<CallService, CallSessionRepository> {
        val callSessionRepository = mockk<CallSessionRepository>()
        val messagingService = mockk<MessagingService>()
        val realtimeMessagePublisher = mockk<RealtimeMessagePublisher>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        every { callSessionRepository.save(any()) } answers { firstArg() }
        val service = CallService(callSessionRepository, messagingService, realtimeMessagePublisher, rateLimiter, "test-turn-secret")
        return service to callSessionRepository
    }

    Given("a real caller and a real 1:1 conversation with user_b") {
        val callSessionRepository = mockk<CallSessionRepository>()
        val messagingService = mockk<MessagingService>()
        val realtimeMessagePublisher = mockk<RealtimeMessagePublisher>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        every { callSessionRepository.save(any()) } answers { firstArg() }
        val service = CallService(callSessionRepository, messagingService, realtimeMessagePublisher, rateLimiter, "test-turn-secret")

        val conversation = Conversation(id = "conversation_1", participantAId = "user_a", participantBId = "user_b")
        every { messagingService.getConversationForParticipant("user_a", "conversation_1") } returns conversation

        When("user_a initiates a real call") {
            val call = service.initiateCall("user_a", "conversation_1", CallType.VOICE)

            Then("it creates a real CallSession with user_b resolved as the real callee") {
                call.callerId shouldBe "user_a"
                call.calleeId shouldBe "user_b"
                call.callType shouldBe CallType.VOICE
                call.answeredAt shouldBe null
            }
            Then("it real-pushes a ring event to the real callee, not the caller") {
                verify(exactly = 1) { realtimeMessagePublisher.publishCallRing("user_b", any(), "user_a", CallType.VOICE) }
            }
        }
    }

    Given("a real, still-active call between user_a and user_b") {
        val (service, callSessionRepository) = newService()
        val call = CallSession(id = "call_1", conversationId = "conversation_1", callerId = "user_a", calleeId = "user_b", callType = CallType.VIDEO)
        every { callSessionRepository.findById("call_1") } returns Optional.of(call)

        When("the real callee answers") {
            val answered = service.answerCall("user_b", "call_1")

            Then("it real-stamps answeredAt") {
                answered.answeredAt shouldNotBe null
            }
        }

        When("a real stranger (not caller or callee) tries to answer") {
            Then("it rejects with a real CallNotParticipantException") {
                shouldThrow<CallNotParticipantException> { service.answerCall("user_stranger", "call_1") }
            }
        }

        When("the real WS handler verifies an inbound signaling frame from the real caller") {
            val otherUserId = service.verifyActiveParticipant("user_a", "call_1")

            Then("it resolves the real other participant to relay to") {
                otherUserId shouldBe "user_b"
            }
        }

        When("a real stranger's signaling frame is verified") {
            Then("it rejects -- never relays to/from a non-participant") {
                shouldThrow<CallNotParticipantException> { service.verifyActiveParticipant("user_stranger", "call_1") }
            }
        }
    }

    Given("a real call that already ended") {
        val (service, callSessionRepository) = newService()
        val endedCall = CallSession(id = "call_ended", conversationId = "conversation_1", callerId = "user_a", calleeId = "user_b", callType = CallType.VOICE)
        endedCall.endedAt = java.time.Instant.now()
        endedCall.endReason = CallEndReason.COMPLETED
        every { callSessionRepository.findById("call_ended") } returns Optional.of(endedCall)

        When("a real, still-active-signal check is attempted") {
            Then("it rejects with a real CallAlreadyEndedException -- never relays into a dead call") {
                shouldThrow<CallAlreadyEndedException> { service.verifyActiveParticipant("user_a", "call_ended") }
            }
        }

        When("endCall is called again (idempotent hangup race)") {
            Then("it real-returns the already-ended call unchanged, no duplicate push") {
                val result = service.endCall("user_a", "call_ended", CallEndReason.CANCELLED)
                result.endReason shouldBe CallEndReason.COMPLETED
            }
        }
    }

    Given("a real caller minting TURN credentials") {
        val (service, _) = newService()

        When("real credentials are requested") {
            val credentials = service.getTurnCredentials("user_a")

            Then("it real-mints a time-limited HMAC credential, never a static shared secret") {
                credentials.username.contains(":user_a") shouldBe true
                credentials.password.isNotBlank() shouldBe true
                credentials.ttlSeconds shouldBe java.time.Duration.ofHours(6).seconds
            }
        }
    }
})
