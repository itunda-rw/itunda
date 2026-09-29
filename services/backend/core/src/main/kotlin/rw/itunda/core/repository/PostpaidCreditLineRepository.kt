package rw.itunda.core.repository

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.PostpaidCreditLine
import java.math.BigDecimal
import java.util.Optional

interface PostpaidCreditLineRepository : JpaRepository<PostpaidCreditLine, String> {
    fun findByUserId(userId: String): PostpaidCreditLine?

    // Real ongoing late-fee accrual sweep -- see PostpaidCreditAccrualScheduler's own
    // doc comment. A real line keeps accruing while overdue regardless of ACTIVE vs
    // already-SUSPENDED status (unlike a fully-repaid line, real balance zero, which
    // never appears here at all) -- coarse repo filter (every real nonzero balance),
    // exact overdue-or-not/24h-since-last-accrual logic in the service, same discipline
    // this codebase's other scheduled sweeps (OverdraftInterestAccrualScheduler) use.
    fun findByCurrentBalanceGreaterThan(amount: BigDecimal): List<PostpaidCreditLine>

    // Real lost-update fix (2026-09-07, Loans product-completeness pass) -- spend/repay/
    // accrueLateFee all did a plain read-modify-write on currentBalance with no lock at
    // all, the exact bug class LoanAccount/VupLoan/StudentLoan/HarvestAdvance/
    // VendorCashAdvance already carry a documented fix for. Matches
    // VendorCashAdvanceRepository.findByIdForUpdate's identical convention.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from PostpaidCreditLine l where l.id = :id")
    fun findByIdForUpdate(@Param("id") id: String): Optional<PostpaidCreditLine>
}
