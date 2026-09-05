package rw.itunda.loans

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.StudentLoan
import rw.itunda.core.domain.StudentLoanLevel
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Real gaps found and fixed 2026-09-05 (feedback_idempotency_key_sweep): before the
 * fix, both `apply` and `declareGraduated` called StudentLoanService directly with
 * no Idempotency-Key protection -- a lost response after either action succeeding
 * would resubmit here and hit a confusing AlreadyActive/NotDisbursed conflict for an
 * action that actually already succeeded. `disburse`/`repay` were already
 * protected. This file exists to make sure that wiring can't silently regress.
 */
class StudentLoanControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    Given("a first-time loan application") {
        val service = mockk<StudentLoanService>()
        val idempotencyService = mockk<IdempotencyService>()
        val scheduler = mockk<StudentLoanGraceEndReminderScheduler>(relaxed = true)
        val controller = StudentLoanController(service, idempotencyService, scheduler)

        val loan = mockk<StudentLoan>(relaxed = true)
        val graduationDate = LocalDate.of(2028, 6, 30)
        every {
            service.applyForLoan("user_1", StudentLoanLevel.UNDERGRADUATE, BigDecimal("2000000"), BigDecimal("500000"), graduationDate)
        } returns loan

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/loans/student/apply", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("applying") {
            val response = controller.apply(
                ApplyForStudentLoanRequest(StudentLoanLevel.UNDERGRADUATE, BigDecimal("2000000"), BigDecimal("500000"), graduationDate),
                "key-1",
                currentUser,
            )

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/loans/student/apply", "key-1", any(), any()) }
                verify(exactly = 1) {
                    service.applyForLoan("user_1", StudentLoanLevel.UNDERGRADUATE, BigDecimal("2000000"), BigDecimal("500000"), graduationDate)
                }
                response.statusCode shouldBe HttpStatus.CREATED
                response.body?.get("loan") shouldBe loan
            }
        }
    }

    Given("a retried loan application using the same Idempotency-Key as a completed one") {
        val service = mockk<StudentLoanService>()
        val idempotencyService = mockk<IdempotencyService>()
        val scheduler = mockk<StudentLoanGraceEndReminderScheduler>(relaxed = true)
        val controller = StudentLoanController(service, idempotencyService, scheduler)
        val graduationDate = LocalDate.of(2028, 6, 30)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/loans/student/apply", "key-1", any(), any())
        } returns (201 to mapOf("success" to true, "loan" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.apply(
                ApplyForStudentLoanRequest(StudentLoanLevel.UNDERGRADUATE, BigDecimal("2000000"), BigDecimal("500000"), graduationDate),
                "key-1",
                currentUser,
            )

            Then("the cached response is returned and the loan is never applied for again") {
                response.body?.get("loan") shouldBe "cached-result"
                verify(exactly = 0) { service.applyForLoan(any(), any(), any(), any(), any()) }
            }
        }
    }

    Given("a first-time declare-graduated request") {
        val service = mockk<StudentLoanService>()
        val idempotencyService = mockk<IdempotencyService>()
        val scheduler = mockk<StudentLoanGraceEndReminderScheduler>(relaxed = true)
        val controller = StudentLoanController(service, idempotencyService, scheduler)

        val loan = mockk<StudentLoan>(relaxed = true)
        every { service.declareGraduated("user_1", "loan_1") } returns loan

        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every {
            idempotencyService.replayOrExecute("POST /api/v1/loans/student/loan_1/declare-graduated", "key-1", any(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("declaring graduated") {
            val response = controller.declareGraduated("loan_1", "key-1", currentUser)

            Then("it routes through the real idempotency service, keyed to this exact route") {
                verify(exactly = 1) {
                    idempotencyService.replayOrExecute("POST /api/v1/loans/student/loan_1/declare-graduated", "key-1", any(), any())
                }
                verify(exactly = 1) { service.declareGraduated("user_1", "loan_1") }
                response.statusCode shouldBe HttpStatus.OK
                response.body?.get("loan") shouldBe loan
            }
        }
    }

    Given("a retried declare-graduated request using the same Idempotency-Key as a completed one") {
        val service = mockk<StudentLoanService>()
        val idempotencyService = mockk<IdempotencyService>()
        val scheduler = mockk<StudentLoanGraceEndReminderScheduler>(relaxed = true)
        val controller = StudentLoanController(service, idempotencyService, scheduler)

        every {
            idempotencyService.replayOrExecute("POST /api/v1/loans/student/loan_1/declare-graduated", "key-1", any(), any())
        } returns (200 to mapOf("success" to true, "loan" to "cached-result"))

        When("retrying with the same key") {
            val response = controller.declareGraduated("loan_1", "key-1", currentUser)

            Then("the cached response is returned and the loan is never marked graduated again") {
                response.body?.get("loan") shouldBe "cached-result"
                verify(exactly = 0) { service.declareGraduated(any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
