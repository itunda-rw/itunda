package rw.itunda.loans

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.StudentLoan
import rw.itunda.core.domain.StudentLoanLevel
import rw.itunda.core.domain.StudentLoanStatus
import java.math.BigDecimal
import java.time.LocalDate

/**
 * First test coverage for StudentLoanGraceEndReminderScheduler -- and a real,
 * previously-live bug fix, not just a missing test. `sendGraceEndReminder` had no
 * try/catch of its own, and this loop had none either. Fixed alongside this test
 * (same commit) with a per-loan try/catch.
 */
class StudentLoanGraceEndReminderSchedulerTest : BehaviorSpec({

    fun loan(id: String) = StudentLoan(
        id = id, userId = "user_$id", level = StudentLoanLevel.UNDERGRADUATE,
        declaredAnnualHouseholdIncome = BigDecimal("1500000"), principalAmount = BigDecimal("500000"),
        outstandingBalance = BigDecimal("500000"), interestRate = 0.11, status = StudentLoanStatus.IN_GRACE_PERIOD,
        expectedGraduationDate = LocalDate.now().plusMonths(1),
    )

    Given("3 loans due soon for a grace-end reminder, where sending the middle one fails") {
        val studentLoanService = mockk<StudentLoanService>()
        val l1 = loan("l1")
        val l2 = loan("l2")
        val l3 = loan("l3")
        every { studentLoanService.getLoansDueSoonForGraceEndReminder() } returns listOf(l1, l2, l3)
        every { studentLoanService.sendGraceEndReminder("l1") } returns Unit
        every { studentLoanService.sendGraceEndReminder("l2") } throws RuntimeException("messaging service unreachable")
        every { studentLoanService.sendGraceEndReminder("l3") } returns Unit
        val scheduler = StudentLoanGraceEndReminderScheduler(studentLoanService)

        When("the sweep runs") {
            val processed = scheduler.processDue()

            Then("all 3 are still attempted -- the middle failure doesn't stop the rest") {
                verify(exactly = 1) { studentLoanService.sendGraceEndReminder("l1") }
                verify(exactly = 1) { studentLoanService.sendGraceEndReminder("l2") }
                verify(exactly = 1) { studentLoanService.sendGraceEndReminder("l3") }
            }
            Then("the real due count still reflects all 3, regardless of the middle failure") {
                processed shouldBe 3
            }
        }
    }

    Given("no loans due soon") {
        val studentLoanService = mockk<StudentLoanService>()
        every { studentLoanService.getLoansDueSoonForGraceEndReminder() } returns emptyList()
        val scheduler = StudentLoanGraceEndReminderScheduler(studentLoanService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is sent, no exception is thrown") {
                verify(exactly = 0) { studentLoanService.sendGraceEndReminder(any()) }
            }
        }
    }
})
