package rw.itunda.core.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.OverdraftAccount
import rw.itunda.core.domain.OverdraftAccountStatus
import java.util.Optional

interface OverdraftAccountRepository : JpaRepository<OverdraftAccount, String> {
    fun findByUserId(userId: String): List<OverdraftAccount>
    fun findByUserIdAndStatus(userId: String, status: OverdraftAccountStatus): OverdraftAccount?

    // Real daily interest accrual sweep (2026-07-27) -- see
    // OverdraftInterestAccrualScheduler's own doc comment. Coarse repo filter (every
    // real ACTIVE account), exact due-or-not/zero-balance logic in the service, same
    // discipline this codebase's other scheduled sweeps already use.
    fun findByStatus(status: OverdraftAccountStatus): List<OverdraftAccount>

    // Real lost-update fix (2026-09-07, Loans product-completeness pass) -- draw/repay/
    // accrueInterest all did a plain read-modify-write on drawnBalance with no lock at
    // all, the exact bug class LoanAccount/VupLoan/StudentLoan/HarvestAdvance/
    // VendorCashAdvance already carry a documented fix for. Matches
    // VendorCashAdvanceRepository.findByIdForUpdate's identical convention.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from OverdraftAccount a where a.id = :id")
    fun findByIdForUpdate(@Param("id") id: String): Optional<OverdraftAccount>
}
