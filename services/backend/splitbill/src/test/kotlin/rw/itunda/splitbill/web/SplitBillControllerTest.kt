package rw.itunda.splitbill.web

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
import rw.itunda.core.domain.SplitBill
import rw.itunda.core.domain.SplitBillMode
import rw.itunda.core.domain.SplitBillParticipant
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.security.CurrentUser
import rw.itunda.messaging.GroupMemberNotFoundException
import rw.itunda.messaging.GroupNeedsMoreMembersException
import rw.itunda.splitbill.SplitBillAlreadyPaidException
import rw.itunda.splitbill.SplitBillAlreadySettledException
import rw.itunda.splitbill.SplitBillDescriptionRequiredException
import rw.itunda.splitbill.SplitBillInvalidAmountException
import rw.itunda.splitbill.SplitBillInvalidReceiptUrlException
import rw.itunda.splitbill.SplitBillInvalidVarianceLevelException
import rw.itunda.splitbill.SplitBillMaxRoundsReachedException
import rw.itunda.splitbill.SplitBillNeedsParticipantsException
import rw.itunda.splitbill.SplitBillNoAccountException
import rw.itunda.splitbill.SplitBillNoPendingParticipantsException
import rw.itunda.splitbill.SplitBillNotFoundException
import rw.itunda.splitbill.SplitBillParticipantNotGroupMemberException
import rw.itunda.splitbill.SplitBillService
import rw.itunda.splitbill.SplitBillWithParticipants
import java.math.BigDecimal

private fun testSplitBill(id: String = "splitbill_1") = SplitBill(
    id = id, organizerId = "user_1", groupConversationId = "group_1", messageId = "message_1",
    totalAmount = BigDecimal("3000"), description = "Dinner",
)

private fun testParticipant(id: String = "participant_1") = SplitBillParticipant(
    id = id, splitBillId = "splitbill_1", userId = "user_2", shareAmount = BigDecimal("1500"),
)

/**
 * Real gap found and fixed 2026-09-05 (feedback_idempotency_key_sweep re-audit):
 * before the fix, `requestNextRound` called SplitBillService.requestNextRound
 * directly with no Idempotency-Key protection, guarded by an explicit but flawed
 * doc comment reasoning "not money-moving, no Idempotency-Key needed" -- the same
 * recurring mistake already found and corrected on ForeignCurrencyController/
 * MotoOwnershipController/PayrollController. Unlike those, requestNextRound has NO
 * guard against a duplicate resubmit at all: it unconditionally increments
 * currentRound and sends another group reminder message every time, so a
 * lost-response retry would silently burn an extra settlement round and spam a
 * duplicate reminder. This file exists to make sure that wiring can't silently
 * regress.
 */
class SplitBillControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    Given("a first-time next-round request") {
        val service = mockk<SplitBillService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = SplitBillController(service, idempotencyService)

        val splitBill = mockk<SplitBill>(relaxed = true)
        every { service.requestNextRound("user_1", "bill_1") } returns splitBill

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/split-bills/bill_1/next-round", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("requesting the next round") {
            val response = controller.requestNextRound("bill_1", "key-1", currentUser)

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) {
                    idempotencyService.replayOrExecute("POST /api/v1/split-bills/bill_1/next-round", "key-1", any(), any())
                }
                verify(exactly = 1) { service.requestNextRound("user_1", "bill_1") }
                response.statusCode shouldBe HttpStatus.OK
                response.body?.get("splitBill") shouldBe splitBill
            }
        }
    }

    Given("a retried next-round request using the same Idempotency-Key as a completed one") {
        val service = mockk<SplitBillService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = SplitBillController(service, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/split-bills/bill_1/next-round", "key-1", any(), any())
        } returns (200 to mapOf("success" to true, "splitBill" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.requestNextRound("bill_1", "key-1", currentUser)

            Then("the cached response is returned and the round is never advanced again") {
                response.body?.get("splitBill") shouldBe "cached-result"
                verify(exactly = 0) { service.requestNextRound(any(), any()) }
            }
        }
    }

    // Real gap found live (Splitbill product-completeness pass, 2026-09-08): this
    // controller test file only ever covered requestNextRound's own idempotency
    // regression -- every other endpoint and 15 of the 17 exception handlers had no
    // coverage at all, the same gap shape GiftVoucherControllerTest.kt had before
    // the Gift pass fixed it.
    Given("a real create-split-bill request") {
        val service = mockk<SplitBillService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = SplitBillController(service, idempotencyService)
        val request = CreateSplitBillRequest(BigDecimal("3000"), "Dinner", listOf("user_2", "user_3"))
        val result = SplitBillWithParticipants(testSplitBill(), listOf(testParticipant()))
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            service.createSplitBill("user_1", "group_1", BigDecimal("3000"), "Dinner", listOf("user_2", "user_3"), SplitBillMode.EVEN, null)
        } returns result
        every {
            idempotencyService.replayOrExecute("POST /api/v1/split-bills/conversations/group_1", "key-1", request, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("creating it") {
            val response = controller.createSplitBill("group_1", request, "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/split-bills/conversations/group_1", "key-1", request, any()) }
                verify(exactly = 1) { service.createSplitBill("user_1", "group_1", BigDecimal("3000"), "Dinner", listOf("user_2", "user_3"), SplitBillMode.EVEN, null) }
                response.statusCode shouldBe HttpStatus.CREATED
            }
        }
    }

    Given("a real create-direct-split-bill request") {
        val service = mockk<SplitBillService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = SplitBillController(service, idempotencyService)
        val request = CreateDirectSplitBillRequest(BigDecimal("2000"), "Coffee")
        val result = SplitBillWithParticipants(testSplitBill(), listOf(testParticipant()))
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            service.createDirectSplitBill("user_1", "user_2", BigDecimal("2000"), "Coffee", SplitBillMode.EVEN, null)
        } returns result
        every {
            idempotencyService.replayOrExecute("POST /api/v1/split-bills/direct/user_2", "key-1", request, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("creating it") {
            val response = controller.createDirectSplitBill("user_2", request, "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/split-bills/direct/user_2", "key-1", request, any()) }
                verify(exactly = 1) { service.createDirectSplitBill("user_1", "user_2", BigDecimal("2000"), "Coffee", SplitBillMode.EVEN, null) }
                response.statusCode shouldBe HttpStatus.CREATED
            }
        }
    }

    Given("a real direct-split-bill history request") {
        val service = mockk<SplitBillService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = SplitBillController(service, idempotencyService)
        every { service.getDirectSplitBills("user_1", "user_2") } returns emptyList()

        When("fetching it") {
            controller.getDirectSplitBills("user_2", currentUser)
            Then("it delegates scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { service.getDirectSplitBills("user_1", "user_2") }
            }
        }
    }

    Given("a real single-split-bill request") {
        val service = mockk<SplitBillService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = SplitBillController(service, idempotencyService)
        val result = SplitBillWithParticipants(testSplitBill(), listOf(testParticipant()))
        every { service.getSplitBill("user_1", "splitbill_1") } returns result

        When("fetching it") {
            controller.getSplitBill("splitbill_1", currentUser)
            Then("it delegates scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { service.getSplitBill("user_1", "splitbill_1") }
            }
        }
    }

    Given("a real per-group split-bill history request") {
        val service = mockk<SplitBillService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = SplitBillController(service, idempotencyService)
        every { service.getSplitBillsForGroup("user_1", "group_1") } returns emptyList()

        When("fetching it") {
            controller.getSplitBillsForGroup("group_1", currentUser)
            Then("it delegates scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { service.getSplitBillsForGroup("user_1", "group_1") }
            }
        }
    }

    Given("a real attach-receipt request") {
        val service = mockk<SplitBillService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = SplitBillController(service, idempotencyService)
        val splitBill = testSplitBill()
        every { service.attachReceipt("user_1", "splitbill_1", "https://example.com/r.jpg") } returns splitBill

        When("attaching it") {
            controller.attachReceipt("splitbill_1", AttachSplitBillReceiptRequest("https://example.com/r.jpg"), currentUser)
            Then("it delegates scoped to the caller's own userId as the organizer, never a client-supplied one") {
                verify(exactly = 1) { service.attachReceipt("user_1", "splitbill_1", "https://example.com/r.jpg") }
            }
        }
    }

    Given("a real pay-share request") {
        val service = mockk<SplitBillService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = SplitBillController(service, idempotencyService)
        val participant = testParticipant()
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every { service.payShare("user_1", "splitbill_1") } returns participant
        every {
            idempotencyService.replayOrExecute("POST /api/v1/split-bills/splitbill_1/pay", "key-1", "splitbill_1", capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("paying it") {
            val response = controller.payShare("splitbill_1", "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/split-bills/splitbill_1/pay", "key-1", "splitbill_1", any()) }
                verify(exactly = 1) { service.payShare("user_1", "splitbill_1") }
                response.statusCode shouldBe HttpStatus.OK
            }
        }
    }

    listOf(
        Triple(GroupNeedsMoreMembersException("Bad request") as RuntimeException, HttpStatus.BAD_REQUEST, "GROUP_NEEDS_MORE_MEMBERS"),
        Triple(GroupMemberNotFoundException("Not found"), HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND"),
        Triple(SplitBillNotFoundException("Not found"), HttpStatus.NOT_FOUND, "SPLIT_BILL_NOT_FOUND"),
        Triple(SplitBillInvalidAmountException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_AMOUNT"),
        Triple(SplitBillDescriptionRequiredException("Bad request"), HttpStatus.BAD_REQUEST, "DESCRIPTION_REQUIRED"),
        Triple(SplitBillNeedsParticipantsException("Bad request"), HttpStatus.BAD_REQUEST, "SPLIT_BILL_NEEDS_PARTICIPANTS"),
        Triple(SplitBillInvalidVarianceLevelException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_LADDER_VARIANCE_LEVEL"),
        Triple(SplitBillInvalidReceiptUrlException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_RECEIPT_URL"),
        Triple(SplitBillParticipantNotGroupMemberException("Bad request"), HttpStatus.BAD_REQUEST, "PARTICIPANT_NOT_GROUP_MEMBER"),
        Triple(SplitBillAlreadyPaidException("Conflict"), HttpStatus.CONFLICT, "SPLIT_BILL_SHARE_ALREADY_PAID"),
        Triple(SplitBillNoAccountException("Not found"), HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"),
        Triple(SplitBillAlreadySettledException("Conflict"), HttpStatus.CONFLICT, "SPLIT_BILL_ALREADY_SETTLED"),
        Triple(SplitBillNoPendingParticipantsException("Conflict"), HttpStatus.CONFLICT, "SPLIT_BILL_NO_PENDING_PARTICIPANTS"),
        Triple(SplitBillMaxRoundsReachedException("Conflict"), HttpStatus.CONFLICT, "SPLIT_BILL_MAX_ROUNDS_REACHED"),
        Triple(InsufficientFundsException("Insufficient funds"), HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS"),
        Triple(IdempotencyConflictException("Conflict"), HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT"),
        Triple(IdempotencyInProgressException("Conflict"), HttpStatus.CONFLICT, "IDEMPOTENT_REQUEST_PROCESSING"),
        Triple(RateLimitExceededException("Too many requests"), HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED"),
    ).forEach { (exception, expectedStatus, expectedCode) ->
        Given("a real ${exception::class.simpleName}") {
            val service = mockk<SplitBillService>()
            val idempotencyService = mockk<IdempotencyService>()
            val controller = SplitBillController(service, idempotencyService)

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is GroupNeedsMoreMembersException -> controller.handleGroupNeedsMoreMembers(exception)
                    is GroupMemberNotFoundException -> controller.handleGroupMemberNotFound(exception)
                    is SplitBillNotFoundException -> controller.handleNotFound(exception)
                    is SplitBillInvalidAmountException -> controller.handleInvalidAmount(exception)
                    is SplitBillDescriptionRequiredException -> controller.handleDescriptionRequired(exception)
                    is SplitBillNeedsParticipantsException -> controller.handleNeedsParticipants(exception)
                    is SplitBillInvalidVarianceLevelException -> controller.handleInvalidVarianceLevel(exception)
                    is SplitBillInvalidReceiptUrlException -> controller.handleInvalidReceiptUrl(exception)
                    is SplitBillParticipantNotGroupMemberException -> controller.handleParticipantNotGroupMember(exception)
                    is SplitBillAlreadyPaidException -> controller.handleAlreadyPaid(exception)
                    is SplitBillNoAccountException -> controller.handleNoAccount(exception)
                    is SplitBillAlreadySettledException -> controller.handleAlreadySettled(exception)
                    is SplitBillNoPendingParticipantsException -> controller.handleNoPendingParticipants(exception)
                    is SplitBillMaxRoundsReachedException -> controller.handleMaxRoundsReached(exception)
                    is InsufficientFundsException -> controller.handleInsufficientFunds(exception)
                    is IdempotencyConflictException -> controller.handleConflict(exception)
                    is IdempotencyInProgressException -> controller.handleInProgress(exception)
                    is RateLimitExceededException -> controller.handleRateLimit(exception)
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
        val service = mockk<SplitBillService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = SplitBillController(service, idempotencyService)
        When("its exception handler maps it to a real HTTP response") {
            val response = controller.handleMissingHeader(mockk<MissingRequestHeaderException>(relaxed = true))
            Then("it maps to 400 with code IDEMPOTENCY_KEY_REQUIRED, not a generic 500") {
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
                response.body?.code shouldBe "IDEMPOTENCY_KEY_REQUIRED"
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
