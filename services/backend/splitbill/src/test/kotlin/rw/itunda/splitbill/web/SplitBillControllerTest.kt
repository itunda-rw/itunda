package rw.itunda.splitbill.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.SplitBill
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import rw.itunda.splitbill.SplitBillService

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
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
