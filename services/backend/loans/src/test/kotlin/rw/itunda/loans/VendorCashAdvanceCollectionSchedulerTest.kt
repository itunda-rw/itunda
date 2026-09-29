package rw.itunda.loans

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.VendorCashAdvance
import rw.itunda.core.domain.VendorCashAdvanceStatus
import java.math.BigDecimal

/**
 * First test coverage for VendorCashAdvanceCollectionScheduler -- and a real,
 * previously-live bug fix, not just a missing test. `runDailyCollection`'s own
 * `Boolean` return only covers the deliberate "nothing to collect" case -- it calls
 * `ledgerService.postLedgerTransaction` with no try/catch of its own, so a genuinely
 * unexpected failure throws uncaught. This loop had none either, so that exception
 * would silently stop collection for every OTHER real due advance in the same tick.
 * Fixed alongside this test (same commit) with a per-advance try/catch.
 */
class VendorCashAdvanceCollectionSchedulerTest : BehaviorSpec({

    fun advance(id: String) = VendorCashAdvance(
        id = id, merchantId = "merchant_$id", principalAmount = BigDecimal("100000"),
        feeAmount = BigDecimal("8000"), totalOwed = BigDecimal("108000"), remainingOwed = BigDecimal("108000"),
        collectionRatePercent = 15.0, status = VendorCashAdvanceStatus.DISBURSED,
    )

    Given("3 advances due for collection, where collecting the middle one fails") {
        val vendorCashAdvanceService = mockk<VendorCashAdvanceService>()
        val a1 = advance("a1")
        val a2 = advance("a2")
        val a3 = advance("a3")
        every { vendorCashAdvanceService.getAdvancesDueForCollection() } returns listOf(a1, a2, a3)
        every { vendorCashAdvanceService.isDueForCollection(any()) } returns true
        every { vendorCashAdvanceService.runDailyCollection(a1) } returns true
        every { vendorCashAdvanceService.runDailyCollection(a2) } throws RuntimeException("unexpected ledger error")
        every { vendorCashAdvanceService.runDailyCollection(a3) } returns true
        val scheduler = VendorCashAdvanceCollectionScheduler(vendorCashAdvanceService)

        When("the sweep runs") {
            scheduler.run()

            Then("all 3 are still attempted -- the middle failure doesn't stop collection for the rest") {
                verify(exactly = 1) { vendorCashAdvanceService.runDailyCollection(a1) }
                verify(exactly = 1) { vendorCashAdvanceService.runDailyCollection(a2) }
                verify(exactly = 1) { vendorCashAdvanceService.runDailyCollection(a3) }
            }
        }
    }

    Given("no advances due for collection") {
        val vendorCashAdvanceService = mockk<VendorCashAdvanceService>()
        every { vendorCashAdvanceService.getAdvancesDueForCollection() } returns emptyList()
        val scheduler = VendorCashAdvanceCollectionScheduler(vendorCashAdvanceService)

        When("the sweep runs") {
            scheduler.run()

            Then("nothing is collected, no exception is thrown") {
                verify(exactly = 0) { vendorCashAdvanceService.runDailyCollection(any()) }
            }
        }
    }
})
