package rw.itunda.calling

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.CallEndReason
import rw.itunda.core.domain.CallSession
import rw.itunda.core.domain.CallType
import rw.itunda.core.domain.Conversation
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.realtime.RealtimeMessagePublisher
import rw.itunda.core.repository.CallSessionRepository
import rw.itunda.core.repository.ConversationRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import java.util.Optional

class CallServiceTest : BehaviorSpec({

    fun newService(): Pair<CallService, CallSessionRepository> {
        val callSessionRepository = mockk<CallSessionRepository>()
        val conversationRepository = mockk<ConversationRepository>()
        val realtimeMessagePublisher = mockk<RealtimeMessagePublisher>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>(relaxed = true)
        every { userRepository.findById(any()) } returns Optional.empty()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        every { callSessionRepository.save(any()) } answers { firstArg() }
        val service = CallService(callSessionRepository, conversationRepository, realtimeMessagePublisher, rateLimiter, userRepository, notificationRepository, pushNotificationService, "test-turn-secret")
        return service to callSessionRepository
    }

    Given("a real caller and a real 1:1 conversation with user_b") {
        val callSessionRepository = mockk<CallSessionRepository>()
        val conversationRepository = mockk<ConversationRepository>()
        val realtimeMessagePublisher = mockk<RealtimeMessagePublisher>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val userRepository = mockk<UserRepository>(relaxed = true)
        every { userRepository.findById(any()) } returns Optional.empty()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        every { callSessionRepository.save(any()) } answers { firstArg() }
        val service = CallService(callSessionRepository, conversationRepository, realtimeMessagePublisher, rateLimiter, userRepository, notificationRepository, pushNotificationService, "test-turn-secret")

        val conversation = Conversation(id = "conversation_1", participantAId = "user_a", participantBId = "user_b")
        every { conversationRepository.findById("conversation_1") } returns Optional.of(conversation)

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

            // Real gap found live (sibling comparison against MessagingService.sendMessage's
            // own "save a Notification row, push after commit" convention for an offline
            // recipient, 2026-09-13): initiateCall never durably notified the callee at all --
            // publishCallRing is a silent no-op when the callee has no live WebSocket session.
            Then("it real-persists and pushes a durable incoming-call notification, not just the live WS frame") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "user_b" && it.type == "INCOMING_CALL" }) }
                verify(exactly = 1) { pushNotificationService.sendToUser("user_b", any(), any(), any()) }
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

    // Real repo-wide rate-limiter-verify sweep (2026-09-09) -- rateLimiter was mocked
    // relaxed = true everywhere else in this file, with no test anywhere exercising
    // initiateCall's own real rateLimiter.checkLimit call -- a real regression (the
    // check silently deleted) would have gone undetected. Same throw-and-catch
    // convention this codebase's other rate-limited services already establish
    // (OverdraftServiceTest/Grow31SavingsServiceTest etc.), not a verify{} call.
    Given("a caller who has exceeded the real call-initiation rate limit") {
        val callSessionRepository = mockk<CallSessionRepository>()
        val conversationRepository = mockk<ConversationRepository>()
        val realtimeMessagePublisher = mockk<RealtimeMessagePublisher>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>()
        val userRepository = mockk<UserRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = CallService(callSessionRepository, conversationRepository, realtimeMessagePublisher, rateLimiter, userRepository, notificationRepository, pushNotificationService, "test-turn-secret")
        every { rateLimiter.checkLimit("call:initiate:user_a", limit = 10, window = any()) } throws RateLimitExceededException("Too many requests")

        When("initiating a real call") {
            Then("a real RateLimitExceededException fires before ever touching a real conversation row") {
                try {
                    service.initiateCall("user_a", "conversation_1", CallType.VOICE)
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { conversationRepository.findById(any()) }
                }
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
