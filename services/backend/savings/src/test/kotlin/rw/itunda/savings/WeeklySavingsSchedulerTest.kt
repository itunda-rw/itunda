package rw.itunda.savings

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.WeeklySavingsPlan
import rw.itunda.core.domain.WeeklySavingsPlanStatus
import java.math.BigDecimal
import java.time.Instant

/**
 * First test coverage for WeeklySavingsScheduler -- and a real, previously-live bug
 * fix, not just a missing test. `processDueInstallment`'s own `Boolean` return only
 * covers the deliberate insufficient-funds case -- it calls
 * `ledgerService.postLedgerTransaction` (and `accountRepository.findById(...)
 * .orElseThrow`) with no try/catch of its own, so a genuinely unexpected failure
 * throws uncaught. This loop had no try/catch either, so that exception would
 * silently stop installment processing for every OTHER real due plan in the same
 * tick. Fixed alongside this test (same commit) with a per-plan try/catch.
 */
class WeeklySavingsSchedulerTest : BehaviorSpec({

    fun plan(id: String) = WeeklySavingsPlan(
        id = id, userId = "user_$id", accountId = "account_$id", name = "26-week plan",
        baseWeeklyAmount = BigDecimal("10000"), escalationRate = BigDecimal("0.10"),
        openingWeekday = 1, baseRate = 5.0, bonusRate = 3.0, weeksElapsed = 0,
        streakBroken = false, status = WeeklySavingsPlanStatus.ACTIVE, nextInstallmentDueAt = Instant.now(),
    )

    Given("3 plans due for a weekly installment, where the middle one fails") {
        val weeklySavingsService = mockk<WeeklySavingsService>()
        val p1 = plan("p1")
        val p2 = plan("p2")
        val p3 = plan("p3")
        every { weeklySavingsService.getPlansDueForProcessing() } returns listOf(p1, p2, p3)
        every { weeklySavingsService.processDueInstallment(p1) } returns true
        every { weeklySavingsService.processDueInstallment(p2) } throws RuntimeException("unexpected ledger error")
        every { weeklySavingsService.processDueInstallment(p3) } returns true
        val scheduler = WeeklySavingsScheduler(weeklySavingsService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 are still attempted -- the middle failure doesn't stop the rest") {
                verify(exactly = 1) { weeklySavingsService.processDueInstallment(p1) }
                verify(exactly = 1) { weeklySavingsService.processDueInstallment(p2) }
                verify(exactly = 1) { weeklySavingsService.processDueInstallment(p3) }
            }
        }
    }

    Given("no plans due for processing") {
        val weeklySavingsService = mockk<WeeklySavingsService>()
        every { weeklySavingsService.getPlansDueForProcessing() } returns emptyList()
        val scheduler = WeeklySavingsScheduler(weeklySavingsService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is processed, no exception is thrown") {
                verify(exactly = 0) { weeklySavingsService.processDueInstallment(any()) }
            }
        }
    }
})
