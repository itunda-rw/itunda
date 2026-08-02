package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.VendorCashAdvance
import rw.itunda.core.domain.VendorCashAdvanceStatus

interface VendorCashAdvanceRepository : JpaRepository<VendorCashAdvance, String> {
    fun findByMerchantId(merchantId: String): List<VendorCashAdvance>

    // "Active" = REQUESTED or DISBURSED -- blocks a second advance while either one is
    // still outstanding, same ACTIVE_STATUSES shape VupLoanService/StudentLoanService
    // already establish.
    fun findByMerchantIdAndStatusIn(merchantId: String, statuses: List<VendorCashAdvanceStatus>): List<VendorCashAdvance>

    // For VendorCashAdvanceCollectionScheduler -- every DISBURSED advance is a
    // candidate for today's collection sweep.
    fun findByStatus(status: VendorCashAdvanceStatus): List<VendorCashAdvance>
}
