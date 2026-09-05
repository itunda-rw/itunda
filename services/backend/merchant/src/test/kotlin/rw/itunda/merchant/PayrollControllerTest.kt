package rw.itunda.merchant

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.PayrollEmployee
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import java.math.BigDecimal

/**
 * Real gap found and fixed 2026-09-05 (feedback_idempotency_key_sweep re-audit,
 * same class as GroupAccountController.invite's identical fix): before the fix,
 * `addEmployee` called PayrollService.addEmployee directly with no Idempotency-Key
 * protection, despite this file's own doc comment explicitly (and incorrectly)
 * reasoning "roster writes aren't money-moving, no Idempotency-Key needed" -- the
 * same flawed reasoning this sweep already found and corrected on
 * ForeignCurrencyController/MotoOwnershipController. A lost response after a
 * successful add would resubmit here and hit EmployeeAlreadyOnRosterException on
 * the retry, a confusing conflict for an addition that actually already succeeded.
 * `runPayroll` was already protected; this create endpoint was the outlier.
 */
class PayrollControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    Given("a first-time roster addition") {
        val service = mockk<PayrollService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = PayrollController(service, idempotencyService)

        val employee = mockk<PayrollEmployee>(relaxed = true)
        every { service.addEmployee("user_1", "+250788111222", BigDecimal("100000")) } returns employee

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/merchant/payroll/employees", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("adding the employee") {
            val response = controller.addEmployee(AddPayrollEmployeeRequest("+250788111222", BigDecimal("100000")), "key-1", currentUser)

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) {
                    idempotencyService.replayOrExecute("POST /api/v1/merchant/payroll/employees", "key-1", any(), any())
                }
                verify(exactly = 1) { service.addEmployee("user_1", "+250788111222", BigDecimal("100000")) }
                response.statusCode shouldBe HttpStatus.OK
                response.body?.get("employee") shouldBe employee
            }
        }
    }

    Given("a retried roster addition using the same Idempotency-Key as a completed one") {
        val service = mockk<PayrollService>()
        val idempotencyService = mockk<IdempotencyService>()
        val controller = PayrollController(service, idempotencyService)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/merchant/payroll/employees", "key-1", any(), any())
        } returns (200 to mapOf("success" to true, "employee" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.addEmployee(AddPayrollEmployeeRequest("+250788111222", BigDecimal("100000")), "key-1", currentUser)

            Then("the cached response is returned and the employee is never added again") {
                response.body?.get("employee") shouldBe "cached-result"
                verify(exactly = 0) { service.addEmployee(any(), any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
