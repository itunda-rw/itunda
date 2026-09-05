package rw.itunda.loans

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.HarvestAdvance
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import java.math.BigDecimal
import java.time.Instant

/**
 * Real gap found and fixed 2026-09-05 (feedback_idempotency_key_sweep): worse than
 * the sibling loan/apply fixes in this same sweep -- CooperativeService.requestAdvance
 * had NO "already have a pending advance" guard at all before the fix, so a
 * lost-response retry with no Idempotency-Key protection wouldn't just show a
 * confusing error, it would silently create a SECOND harvest advance for the same
 * membership. `disburse`/`repay` were already protected; this create endpoint was
 * the outlier. This file exists to make sure that wiring can't silently regress.
 */
class CooperativeControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")
    val harvestDate = Instant.parse("2027-03-01T00:00:00Z")

    Given("a first-time harvest advance request") {
        val service = mockk<CooperativeService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = CooperativeController(service, idempotencyService)

        val advance = mockk<HarvestAdvance>(relaxed = true)
        every {
            service.requestAdvance("user_1", "membership_1", BigDecimal("100000"), "fertilizer", harvestDate)
        } returns advance

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/cooperatives/advances", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("requesting the advance") {
            val response = controller.requestAdvance(
                RequestAdvanceRequest("membership_1", BigDecimal("100000"), "fertilizer", harvestDate),
                "key-1",
                currentUser,
            )

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/cooperatives/advances", "key-1", any(), any()) }
                verify(exactly = 1) { service.requestAdvance("user_1", "membership_1", BigDecimal("100000"), "fertilizer", harvestDate) }
                response.statusCode shouldBe HttpStatus.CREATED
                response.body?.get("advance") shouldBe advance
            }
        }
    }

    Given("a retried harvest advance request using the same Idempotency-Key as a completed one") {
        val service = mockk<CooperativeService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = CooperativeController(service, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/cooperatives/advances", "key-1", any(), any())
        } returns (201 to mapOf("success" to true, "advance" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.requestAdvance(
                RequestAdvanceRequest("membership_1", BigDecimal("100000"), "fertilizer", harvestDate),
                "key-1",
                currentUser,
            )

            Then("the cached response is returned and a second advance is never created") {
                response.body?.get("advance") shouldBe "cached-result"
                verify(exactly = 0) { service.requestAdvance(any(), any(), any(), any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
