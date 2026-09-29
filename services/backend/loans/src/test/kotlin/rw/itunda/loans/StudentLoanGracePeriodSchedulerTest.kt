package rw.itunda.loans

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.StudentLoan
import rw.itunda.core.domain.StudentLoanLevel
import rw.itunda.core.domain.StudentLoanStatus
import java.math.BigDecimal
import java.time.LocalDate

/**
 * First test coverage for StudentLoanGracePeriodScheduler -- and a real,
 * previously-live bug fix, not just a missing test. `markRepaying` had no try/catch
 * of its own around its repository save, and this loop had none either. Fixed
 * alongside this test (same commit) with a per-loan try/catch.
 */
class StudentLoanGracePeriodSchedulerTest : BehaviorSpec({

    fun loan(id: String) = StudentLoan(
        id = id, userId = "user_$id", level = StudentLoanLevel.UNDERGRADUATE,
        declaredAnnualHouseholdIncome = BigDecimal("1500000"), principalAmount = BigDecimal("500000"),
        outstandingBalance = BigDecimal("500000"), interestRate = 0.11, status = StudentLoanStatus.IN_GRACE_PERIOD,
        expectedGraduationDate = LocalDate.now().minusMonths(1),
    )

    Given("3 loans whose grace period ended, where flagging the middle one fails") {
        val studentLoanService = mockk<StudentLoanService>()
        val l1 = loan("l1")
        val l2 = loan("l2")
        val l3 = loan("l3")
        every { studentLoanService.getLoansDueForGracePeriodEnd() } returns listOf(l1, l2, l3)
        every { studentLoanService.markRepaying(l1) } returns Unit
        every { studentLoanService.markRepaying(l2) } throws RuntimeException("unexpected database error")
        every { studentLoanService.markRepaying(l3) } returns Unit
        val scheduler = StudentLoanGracePeriodScheduler(studentLoanService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 are still attempted -- the middle failure doesn't stop the rest") {
                verify(exactly = 1) { studentLoanService.markRepaying(l1) }
                verify(exactly = 1) { studentLoanService.markRepaying(l2) }
                verify(exactly = 1) { studentLoanService.markRepaying(l3) }
            }
        }
    }

    Given("no loans whose grace period just ended") {
        val studentLoanService = mockk<StudentLoanService>()
        every { studentLoanService.getLoansDueForGracePeriodEnd() } returns emptyList()
        val scheduler = StudentLoanGracePeriodScheduler(studentLoanService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is flagged, no exception is thrown") {
                verify(exactly = 0) { studentLoanService.markRepaying(any()) }
            }
        }
    }
})
