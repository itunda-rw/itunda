package rw.itunda.loans

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Real Isoko Vendor Cash Advance daily collection sweep -- see
 * `VendorCashAdvanceService`'s own doc comment. The *business* cadence is real (a
 * DISBURSED advance is only actually swept once real ~24h has elapsed since its last
 * collection or disbursal, gated by `VendorCashAdvanceService.isDueForCollection`).
 * The *poll* interval below is demo-speed, the same convention
 * `VupLoanOverdueScheduler`/`MotoOwnershipScheduler`'s own doc comments establish:
 * checking every 60 seconds for advances whose real 24h cadence has already elapsed
 * is cheap and correct; it does not mean advances get swept every 60 seconds.
 *
 * Real fix (found via project_itunda_zero_test_coverage_sweep's scheduler audit,
 * 2026-09-05): `runDailyCollection`'s `Boolean` return only covers the deliberate
 * "nothing to collect" case -- it calls `ledgerService.postLedgerTransaction` with no
 * try/catch of its own, so a genuinely unexpected failure throws uncaught. This loop
 * had no try/catch either, so that exception would silently stop collection for every
 * OTHER real due advance in the same tick -- the exact "scheduler
 * transaction-poisoning" bug class this codebase has already found and fixed
 * multiple times elsewhere. Per-advance try/catch closes it.
 */
@Component
class VendorCashAdvanceCollectionScheduler(private val vendorCashAdvanceService: VendorCashAdvanceService) {
    private val log = LoggerFactory.getLogger(VendorCashAdvanceCollectionScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        val due = vendorCashAdvanceService.getAdvancesDueForCollection().filter { vendorCashAdvanceService.isDueForCollection(it) }
        for (advance in due) {
            try {
                val collected = vendorCashAdvanceService.runDailyCollection(advance)
                if (collected) {
                    log.info("Collected against Isoko Vendor Cash Advance {} (remaining owed {})", advance.id, advance.remainingOwed)
                }
            } catch (e: Exception) {
                log.error("Vendor cash advance collection failed for {}", advance.id, e)
            }
        }
    }
}
