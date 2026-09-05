package rw.itunda.loans

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.VupLoan
import rw.itunda.core.domain.VupLoanPurpose
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import java.math.BigDecimal

/**
 * Real gap found and fixed 2026-09-05 (feedback_idempotency_key_sweep): before the
 * fix, `apply` called VupLoanService.applyForLoan directly with no Idempotency-Key
 * protection -- a lost response after a successful apply would resubmit here and
 * hit VupLoanAlreadyActiveException on the retry, a confusing conflict for an
 * application that actually already succeeded. `disburse`/`repay` were already
 * protected; this create endpoint was the outlier. This file exists to make sure
 * that wiring can't silently regress.
 */
class VupLoanControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    Given("a first-time loan application") {
        val service = mockk<VupLoanService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = VupLoanController(service, idempotencyService)

        val loan = mockk<VupLoan>(relaxed = true)
        every { service.applyForLoan("user_1", 1, VupLoanPurpose.FARMING, BigDecimal("50000")) } returns loan

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/loans/vup/apply", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("applying") {
            val response = controller.apply(
                ApplyForVupLoanRequest(declaredUbudeheCategory = 1, purpose = VupLoanPurpose.FARMING, amount = BigDecimal("50000")),
                "key-1",
                currentUser,
            )

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/loans/vup/apply", "key-1", any(), any()) }
                verify(exactly = 1) { service.applyForLoan("user_1", 1, VupLoanPurpose.FARMING, BigDecimal("50000")) }
                response.statusCode shouldBe HttpStatus.CREATED
                response.body?.get("loan") shouldBe loan
            }
        }
    }

    Given("a retried loan application using the same Idempotency-Key as a completed one") {
        val service = mockk<VupLoanService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = VupLoanController(service, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/loans/vup/apply", "key-1", any(), any())
        } returns (201 to mapOf("success" to true, "loan" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.apply(
                ApplyForVupLoanRequest(declaredUbudeheCategory = 1, purpose = VupLoanPurpose.FARMING, amount = BigDecimal("50000")),
                "key-1",
                currentUser,
            )

            Then("the cached response is returned and the loan is never applied for again") {
                response.body?.get("loan") shouldBe "cached-result"
                verify(exactly = 0) { service.applyForLoan(any(), any(), any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
