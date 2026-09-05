package rw.itunda.agents

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.AgentTillReconciliation
import rw.itunda.core.domain.TillReconciliationStatus
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.idempotency.IdempotentReplay
import rw.itunda.core.security.CurrentUser
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Real gap found and fixed 2026-09-05 (feedback_idempotency_key_sweep): before that
 * fix, submitTillCount called agentService.submitTillCount directly, with no
 * Idempotency-Key protection -- a lost response after a successful till count would
 * resubmit here and hit TillReconciliationAlreadySubmittedException on the retry,
 * showing the operator a confusing conflict for a count that actually already
 * succeeded. This file exists to make sure that wiring can't silently regress: the
 * controller must route the business action through idempotencyService.replayOrExecute
 * rather than calling AgentService directly, and a replayed key must never
 * re-invoke the business action.
 */
class AgentOperatorControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    Given("a first-time till count submission") {
        val agentService = mockk<AgentService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = AgentOperatorController(agentService, idempotencyService)

        val reconciliation = AgentTillReconciliation(
            id = "tillrec_1",
            agentId = "agent_1",
            businessDate = LocalDate.now(),
            expectedCash = BigDecimal("10000"),
            countedCash = BigDecimal("10000"),
            variance = BigDecimal.ZERO,
            submittedByUserId = "user_1",
            status = TillReconciliationStatus.MATCHED,
        )
        every { agentService.submitTillCount("user_1", BigDecimal("10000")) } returns reconciliation

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute(
                "POST /api/v1/agent/till-reconciliations",
                "key-1",
                any(),
                capture(actionSlot),
            )
        } answers { actionSlot.captured.invoke() }

        When("submitting the count") {
            val response = controller.submitTillCount(
                AgentOperatorController.SubmitTillCountRequest(BigDecimal("10000")),
                "key-1",
                currentUser,
            )

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) {
                    idempotencyService.replayOrExecute("POST /api/v1/agent/till-reconciliations", "key-1", any(), any())
                }
                verify(exactly = 1) { agentService.submitTillCount("user_1", BigDecimal("10000")) }
                response.statusCode shouldBe HttpStatus.CREATED
                @Suppress("UNCHECKED_CAST")
                val reconciliationInBody = response.body?.get("reconciliation")
                reconciliationInBody shouldBe reconciliation
            }
        }
    }

    Given("a retried till count submission using the same Idempotency-Key as a completed one") {
        val agentService = mockk<AgentService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = AgentOperatorController(agentService, idempotencyService)

        // The real IdempotencyService would detect this as a replay and never invoke
        // the passed-in action lambda at all -- simulated here directly, since what
        // this controller test needs to prove is that it never calls AgentService
        // itself outside of that lambda.
        every {
            idempotencyService.replayOrExecute("POST /api/v1/agent/till-reconciliations", "key-1", any(), any())
        } returns (201 to mapOf("success" to true, "reconciliation" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.submitTillCount(
                AgentOperatorController.SubmitTillCountRequest(BigDecimal("10000")),
                "key-1",
                currentUser,
            )

            Then("the cached response is returned and the business action never runs again") {
                response.body?.get("reconciliation") shouldBe "cached-result"
                verify(exactly = 0) { agentService.submitTillCount(any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
