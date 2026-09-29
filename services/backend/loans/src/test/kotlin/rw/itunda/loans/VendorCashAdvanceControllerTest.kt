package rw.itunda.loans

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.VendorCashAdvance
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser

/**
 * Real gap found and fixed 2026-09-05 (feedback_idempotency_key_sweep): before the
 * fix, `apply` called VendorCashAdvanceService.applyForAdvance directly with no
 * Idempotency-Key protection -- a lost response after a successful apply would
 * resubmit here and hit VendorCashAdvanceAlreadyActiveException on the retry, a
 * confusing conflict for an application that actually already succeeded.
 * `disburse`/`repay-early` were already protected; this create endpoint was the
 * outlier. This file exists to make sure that wiring can't silently regress.
 */
class VendorCashAdvanceControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    Given("a first-time cash advance application") {
        val service = mockk<VendorCashAdvanceService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = VendorCashAdvanceController(service, idempotencyService)

        val advance = mockk<VendorCashAdvance>(relaxed = true)
        every { service.applyForAdvance("user_1", "merchant_1") } returns advance

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/vendor-advance/apply", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("applying") {
            val response = controller.apply(ApplyForVendorCashAdvanceRequest("merchant_1"), "key-1", currentUser)

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/vendor-advance/apply", "key-1", any(), any()) }
                verify(exactly = 1) { service.applyForAdvance("user_1", "merchant_1") }
                response.statusCode shouldBe HttpStatus.CREATED
                response.body?.get("advance") shouldBe advance
            }
        }
    }

    Given("a retried cash advance application using the same Idempotency-Key as a completed one") {
        val service = mockk<VendorCashAdvanceService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = VendorCashAdvanceController(service, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/vendor-advance/apply", "key-1", any(), any())
        } returns (201 to mapOf("success" to true, "advance" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.apply(ApplyForVendorCashAdvanceRequest("merchant_1"), "key-1", currentUser)

            Then("the cached response is returned and the advance is never applied for again") {
                response.body?.get("advance") shouldBe "cached-result"
                verify(exactly = 0) { service.applyForAdvance(any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
