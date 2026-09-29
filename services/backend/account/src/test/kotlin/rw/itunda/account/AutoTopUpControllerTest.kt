package rw.itunda.account

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser

/**
 * Real gap found and fixed 2026-09-05 (feedback_idempotency_key_sweep re-audit):
 * before the fix, `trigger` had no Idempotency-Key protection, relying on an
 * incomplete "idempotent no-op if real conditions aren't met" comment --
 * AutoTopUpService.evaluateAndTopUp's own threshold check only blocks a retry
 * once the balance has already crossed back above the threshold. If topUpAmount
 * is small relative to thresholdAmount, a lost-response retry could re-trigger a
 * real second top-up before that happens, bounded only by dailyTriggerCap -- a
 * real, silent double-charge risk. This file exists to make sure the fix can't
 * silently regress.
 */
class AutoTopUpControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    Given("a first-time trigger request") {
        val service = mockk<AutoTopUpService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = AutoTopUpController(service, idempotencyService)

        val result = AutoTopUpTriggerResult(triggered = true, reason = "Topped up 5000 RWF")
        every { service.evaluateAndTopUp("user_1", "account_1") } returns result

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/account/account_1/auto-topup/trigger", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("triggering") {
            val response = controller.trigger("account_1", "key-1", currentUser)

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) {
                    idempotencyService.replayOrExecute("POST /api/v1/account/account_1/auto-topup/trigger", "key-1", any(), any())
                }
                verify(exactly = 1) { service.evaluateAndTopUp("user_1", "account_1") }
                response.statusCode shouldBe HttpStatus.OK
                response.body?.get("triggered") shouldBe true
            }
        }
    }

    Given("a retried trigger request using the same Idempotency-Key as a completed one") {
        val service = mockk<AutoTopUpService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = AutoTopUpController(service, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/account/account_1/auto-topup/trigger", "key-1", any(), any())
        } returns (200 to mapOf("success" to true, "triggered" to true, "reason" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.trigger("account_1", "key-1", currentUser)

            Then("the cached response is returned and the top-up is never triggered again") {
                response.body?.get("reason") shouldBe "cached-result"
                verify(exactly = 0) { service.evaluateAndTopUp(any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
