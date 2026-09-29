package rw.itunda.rewards.web

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
import rw.itunda.rewards.MissionClaimResult
import rw.itunda.rewards.ShoppingMissionService
import rw.itunda.rewards.ShoppingMissionType
import java.math.BigDecimal

/**
 * Real gap found and fixed 2026-09-05 (feedback_idempotency_key_sweep re-audit):
 * before the fix, `completeMission` had no Idempotency-Key protection, relying on
 * a flawed "naturally idempotent, no Idempotency-Key needed" doc comment. The
 * stored per-day/once-ever flag genuinely prevents a double credit, but it does so
 * by throwing MissionAlreadyCompletedException -- so a lost-response retry after a
 * SUCCESSFUL completion used to hit that exact guard with a confusing conflict for
 * a mission that actually already completed. This file exists to make sure the fix
 * can't silently regress.
 */
class ShoppingMissionControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    Given("a first-time mission completion") {
        val service = mockk<ShoppingMissionService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = ShoppingMissionController(service, idempotencyService)

        val result = MissionClaimResult(ShoppingMissionType.CHECK_IN, BigDecimal("20"), BigDecimal("1020"))
        every { service.completeDailyMission("user_1", ShoppingMissionType.CHECK_IN) } returns result

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/shopping/points/missions/CHECK_IN/complete", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("completing the mission") {
            val response = controller.completeMission("CHECK_IN", "key-1", currentUser)

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) {
                    idempotencyService.replayOrExecute("POST /api/v1/shopping/points/missions/CHECK_IN/complete", "key-1", any(), any())
                }
                verify(exactly = 1) { service.completeDailyMission("user_1", ShoppingMissionType.CHECK_IN) }
                response.statusCode shouldBe HttpStatus.OK
                response.body?.get("amountEarned") shouldBe BigDecimal("20")
            }
        }
    }

    Given("a retried mission completion using the same Idempotency-Key as a completed one") {
        val service = mockk<ShoppingMissionService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = ShoppingMissionController(service, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/shopping/points/missions/CHECK_IN/complete", "key-1", any(), any())
        } returns (200 to mapOf("success" to true, "amountEarned" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.completeMission("CHECK_IN", "key-1", currentUser)

            Then("the cached response is returned and the mission is never completed again") {
                response.body?.get("amountEarned") shouldBe "cached-result"
                verify(exactly = 0) { service.completeDailyMission(any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
