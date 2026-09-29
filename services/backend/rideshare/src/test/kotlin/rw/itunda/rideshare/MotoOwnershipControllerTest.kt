package rw.itunda.rideshare

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.MotoOwnershipPlan
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import java.math.BigDecimal

/**
 * Real gap found and fixed 2026-09-05 (feedback_idempotency_key_sweep): before the
 * fix, `createPlan` called MotoOwnershipService.createPlan directly with no
 * Idempotency-Key protection -- a lost response after a successful create would
 * resubmit here and hit MotoOwnershipPlanAlreadyActiveException on the retry, a
 * confusing conflict for a plan that actually already got created. `contribute`/
 * `cancel`/`convert-to-loan`/`repay` were already protected; this create endpoint
 * was the outlier. This file exists to make sure that wiring can't silently regress.
 */
class MotoOwnershipControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    Given("a first-time plan creation request") {
        val service = mockk<MotoOwnershipService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = MotoOwnershipController(service, idempotencyService)

        val plan = mockk<MotoOwnershipPlan>(relaxed = true)
        every { service.createPlan("user_1", BigDecimal("2000000"), BigDecimal("5000")) } returns plan

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/moto-ownership/plans", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("creating the plan") {
            val response = controller.createPlan(
                CreateMotoOwnershipPlanRequest(BigDecimal("2000000"), BigDecimal("5000")),
                "key-1",
                currentUser,
            )

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/moto-ownership/plans", "key-1", any(), any()) }
                verify(exactly = 1) { service.createPlan("user_1", BigDecimal("2000000"), BigDecimal("5000")) }
                response.statusCode shouldBe HttpStatus.CREATED
                response.body?.get("plan") shouldBe plan
            }
        }
    }

    Given("a retried plan creation request using the same Idempotency-Key as a completed one") {
        val service = mockk<MotoOwnershipService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = MotoOwnershipController(service, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/moto-ownership/plans", "key-1", any(), any())
        } returns (201 to mapOf("success" to true, "plan" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.createPlan(
                CreateMotoOwnershipPlanRequest(BigDecimal("2000000"), BigDecimal("5000")),
                "key-1",
                currentUser,
            )

            Then("the cached response is returned and a second plan is never created") {
                response.body?.get("plan") shouldBe "cached-result"
                verify(exactly = 0) { service.createPlan(any(), any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
