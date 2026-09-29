package rw.itunda.core.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.VendorCashAdvance
import rw.itunda.core.domain.VendorCashAdvanceStatus
import java.util.Optional

interface VendorCashAdvanceRepository : JpaRepository<VendorCashAdvance, String> {
    fun findByMerchantId(merchantId: String): List<VendorCashAdvance>

    // "Active" = REQUESTED or DISBURSED -- blocks a second advance while either one is
    // still outstanding, same ACTIVE_STATUSES shape VupLoanService/StudentLoanService
    // already establish.
    fun findByMerchantIdAndStatusIn(merchantId: String, statuses: List<VendorCashAdvanceStatus>): List<VendorCashAdvance>

    // For VendorCashAdvanceCollectionScheduler -- every DISBURSED advance is a
    // candidate for today's collection sweep.
    fun findByStatus(status: VendorCashAdvanceStatus): List<VendorCashAdvance>

    // Real hardening (concurrency-audit thread) -- VendorCashAdvance already carries
    // @Version from day one, so a losing concurrent writer's whole transaction rolls
    // back atomically (not a fund-leak either way), but runDailyCollection reads this
    // row unlocked before mutating remainingOwed and posting real ledger legs -- adding
    // this lock avoids wasted ledger-posting work + a raw
    // ObjectOptimisticLockingFailureException if the scheduled sweep races a manual
    // repayEarly on the same advance. Matches the established convention
    // (WalletRepository/AccountRepository/SavingsGoalRepository.findByIdForUpdate)
    // elsewhere in this codebase.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from VendorCashAdvance a where a.id = :id")
    fun findByIdForUpdate(@Param("id") id: String): Optional<VendorCashAdvance>
}
