package rw.itunda.gift.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MissingRequestHeaderException
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.domain.Gift
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.security.CurrentUser
import rw.itunda.gift.GiftAlreadyResolvedException
import rw.itunda.gift.GiftExpiredException
import rw.itunda.gift.GiftInvalidAmountException
import rw.itunda.gift.GiftNoAccountException
import rw.itunda.gift.GiftNotFoundException
import rw.itunda.gift.GiftNotRecipientException
import rw.itunda.gift.GiftRecipientNotFoundException
import rw.itunda.gift.GiftSelfException
import rw.itunda.gift.GiftService
import java.math.BigDecimal
import java.time.Instant

private fun testGift(id: String = "gift_1") = Gift(
    id = id, senderId = "user_1", recipientId = "user_2", conversationId = "conversation_1",
    messageId = "message_1", amount = BigDecimal("5000"), note = null, holdTransactionId = "txn_1",
    expiresAt = Instant.now().plusSeconds(3600),
)

/**
 * First test coverage for GiftController -- previously untested despite
 * GiftServiceTest.kt already having full service-layer coverage. Covers real
 * delegation (caller-scoped userId, never client-supplied) for all 4 endpoints and
 * every real exception-handler mapping, matching this sweep's established
 * BehaviorSpec/MockK shape (see FamilyLinkControllerTest.kt/StocksControllerTest.kt).
 */
class GiftControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    fun controller(
        giftService: GiftService = mockk(),
        idempotencyService: IdempotencyService = mockk(),
    ) = GiftController(giftService, idempotencyService)

    Given("a real send-gift request") {
        val giftService = mockk<GiftService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(giftService, idempotencyService)
        val request = SendGiftRequest("+250788000002", BigDecimal("5000"), "Happy birthday", null)
        val gift = testGift()
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every { giftService.sendGift("user_1", "+250788000002", BigDecimal("5000"), "Happy birthday", null) } returns gift
        every {
            idempotencyService.replayOrExecute("POST /api/v1/gifts", "key-1", request, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("sending it") {
            val response = ctl.sendGift(request, "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/gifts", "key-1", request, any()) }
                verify(exactly = 1) { giftService.sendGift("user_1", "+250788000002", BigDecimal("5000"), "Happy birthday", null) }
                response.statusCode shouldBe HttpStatus.CREATED
            }
        }
    }

    Given("a real send-gift-in-conversation request") {
        val giftService = mockk<GiftService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(giftService, idempotencyService)
        val request = SendGiftInConversationRequest(BigDecimal("2000"), null, null)
        val gift = testGift()
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every { giftService.sendGiftInConversation("user_1", "conversation_1", BigDecimal("2000"), null, null) } returns gift
        every {
            idempotencyService.replayOrExecute("POST /api/v1/gifts/conversations/conversation_1", "key-1", request, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("sending it") {
            val response = ctl.sendGiftInConversation("conversation_1", request, "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/gifts/conversations/conversation_1", "key-1", request, any()) }
                verify(exactly = 1) { giftService.sendGiftInConversation("user_1", "conversation_1", BigDecimal("2000"), null, null) }
                response.statusCode shouldBe HttpStatus.CREATED
            }
        }
    }

    Given("a real single-gift request") {
        val giftService = mockk<GiftService>()
        val ctl = controller(giftService)
        val gift = testGift()
        every { giftService.getGift("user_1", "gift_1") } returns gift

        When("fetching it") {
            ctl.getGift("gift_1", currentUser)
            Then("it delegates scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { giftService.getGift("user_1", "gift_1") }
            }
        }
    }

    Given("a real per-conversation gift history request") {
        val giftService = mockk<GiftService>()
        val ctl = controller(giftService)
        every { giftService.getGiftsForConversation("user_1", "conversation_1") } returns emptyList()

        When("fetching it") {
            ctl.getGiftsForConversation("conversation_1", currentUser)
            Then("it delegates scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { giftService.getGiftsForConversation("user_1", "conversation_1") }
            }
        }
    }

    Given("a real claim-gift request") {
        val giftService = mockk<GiftService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(giftService, idempotencyService)
        val gift = testGift()
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every { giftService.claimGift("user_1", "gift_1") } returns gift
        every {
            idempotencyService.replayOrExecute("POST /api/v1/gifts/gift_1/claim", "key-1", "gift_1", capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("claiming it") {
            val response = ctl.claimGift("gift_1", "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/gifts/gift_1/claim", "key-1", "gift_1", any()) }
                verify(exactly = 1) { giftService.claimGift("user_1", "gift_1") }
                response.statusCode shouldBe HttpStatus.OK
            }
        }
    }

    listOf(
        Triple(GiftNotFoundException("Not found") as RuntimeException, HttpStatus.NOT_FOUND, "GIFT_NOT_FOUND"),
        Triple(GiftAlreadyResolvedException("Conflict"), HttpStatus.CONFLICT, "GIFT_ALREADY_RESOLVED"),
        Triple(GiftExpiredException("Conflict"), HttpStatus.CONFLICT, "GIFT_EXPIRED"),
        Triple(GiftNotRecipientException("Forbidden"), HttpStatus.FORBIDDEN, "NOT_GIFT_RECIPIENT"),
        Triple(GiftSelfException("Bad request"), HttpStatus.BAD_REQUEST, "SELF_GIFT_NOT_ALLOWED"),
        Triple(GiftNoAccountException("Not found"), HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"),
        Triple(GiftRecipientNotFoundException("Not found"), HttpStatus.NOT_FOUND, "GIFT_RECIPIENT_NOT_FOUND"),
        Triple(GiftInvalidAmountException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_AMOUNT"),
        Triple(InsufficientFundsException("Insufficient funds"), HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS"),
        Triple(IdempotencyConflictException("Conflict"), HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT"),
        Triple(IdempotencyInProgressException("Conflict"), HttpStatus.CONFLICT, "IDEMPOTENT_REQUEST_PROCESSING"),
        Triple(RateLimitExceededException("Too many requests"), HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED"),
    ).forEach { (exception, expectedStatus, expectedCode) ->
        Given("a real ${exception::class.simpleName}") {
            val ctl = controller()

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is GiftNotFoundException -> ctl.handleNotFound(exception)
                    is GiftAlreadyResolvedException -> ctl.handleAlreadyResolved(exception)
                    is GiftExpiredException -> ctl.handleExpired(exception)
                    is GiftNotRecipientException -> ctl.handleNotRecipient(exception)
                    is GiftSelfException -> ctl.handleSelf(exception)
                    is GiftNoAccountException -> ctl.handleNoAccount(exception)
                    is GiftRecipientNotFoundException -> ctl.handleRecipientNotFound(exception)
                    is GiftInvalidAmountException -> ctl.handleInvalidAmount(exception)
                    is InsufficientFundsException -> ctl.handleInsufficientFunds(exception)
                    is IdempotencyConflictException -> ctl.handleConflict(exception)
                    is IdempotencyInProgressException -> ctl.handleInProgress(exception)
                    is RateLimitExceededException -> ctl.handleRateLimit(exception)
                    else -> error("unexpected exception type")
                }

                Then("it maps to $expectedStatus with code $expectedCode, not a generic 500") {
                    response.statusCode shouldBe expectedStatus
                    response.body?.code shouldBe expectedCode
                }
            }
        }
    }

    Given("a real MissingRequestHeaderException") {
        val ctl = controller()
        When("its exception handler maps it to a real HTTP response") {
            val response = ctl.handleMissingHeader(mockk<MissingRequestHeaderException>(relaxed = true))
            Then("it maps to 400 with code IDEMPOTENCY_KEY_REQUIRED, not a generic 500") {
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
                response.body?.code shouldBe "IDEMPOTENCY_KEY_REQUIRED"
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
