package rw.itunda.savings

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.InterestJar
import java.math.BigDecimal
import java.time.Instant

/**
 * First test coverage for InterestAccrualScheduler -- and a real, previously-live bug
 * fix, not just a missing test. `InterestJarService.accrueInterest` calls
 * `ledgerService.postLedgerTransaction` with no try/catch of its own, and this loop
 * had none either: a single bad jar (a since-deleted account, a transient ledger
 * error) would throw uncaught and silently stop accrual for every OTHER real due jar
 * the same day. Fixed alongside this test (same commit) with a per-jar try/catch,
 * matching every other resilient scheduler in this codebase.
 */
class InterestAccrualSchedulerTest : BehaviorSpec({

    fun jar(userId: String) = InterestJar(
        userId = userId, accountId = "account_$userId", balance = BigDecimal("50000"), rate = 7.5,
        earnedThisMonth = BigDecimal.ZERO, earnedTotal = BigDecimal.ZERO,
        lastPaidAt = Instant.now(), nextPayoutAt = Instant.now(),
    )

    Given("3 interest jars due for accrual, where accruing the middle one fails") {
        val interestJarService = mockk<InterestJarService>()
        val j1 = jar("u1")
        val j2 = jar("u2")
        val j3 = jar("u3")
        every { interestJarService.getJarsDueForAccrual() } returns listOf(j1, j2, j3)
        every { interestJarService.accrueInterest(j1) } returns Unit
        every { interestJarService.accrueInterest(j2) } throws RuntimeException("unexpected ledger error")
        every { interestJarService.accrueInterest(j3) } returns Unit
        val scheduler = InterestAccrualScheduler(interestJarService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 jars are still accrued -- the middle failure doesn't stop accrual for the rest") {
                verify(exactly = 1) { interestJarService.accrueInterest(j1) }
                verify(exactly = 1) { interestJarService.accrueInterest(j2) }
                verify(exactly = 1) { interestJarService.accrueInterest(j3) }
            }
        }
    }

    Given("no interest jars due for accrual") {
        val interestJarService = mockk<InterestJarService>()
        every { interestJarService.getJarsDueForAccrual() } returns emptyList()
        val scheduler = InterestAccrualScheduler(interestJarService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is accrued, no exception is thrown") {
                verify(exactly = 0) { interestJarService.accrueInterest(any()) }
            }
        }
    }
})
