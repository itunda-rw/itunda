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
 */
@Component
class VendorCashAdvanceCollectionScheduler(private val vendorCashAdvanceService: VendorCashAdvanceService) {
    private val log = LoggerFactory.getLogger(VendorCashAdvanceCollectionScheduler::class.java)

    @Scheduled(fixedDelay = 60000)
    fun run() {
        val due = vendorCashAdvanceService.getAdvancesDueForCollection().filter { vendorCashAdvanceService.isDueForCollection(it) }
        for (advance in due) {
            val collected = vendorCashAdvanceService.runDailyCollection(advance)
            if (collected) {
                log.info("Collected against Isoko Vendor Cash Advance {} (remaining owed {})", advance.id, advance.remainingOwed)
            }
        }
    }
}
