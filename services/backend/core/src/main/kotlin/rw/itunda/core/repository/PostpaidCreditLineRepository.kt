package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.PostpaidCreditLine
import java.math.BigDecimal

interface PostpaidCreditLineRepository : JpaRepository<PostpaidCreditLine, String> {
    fun findByUserId(userId: String): PostpaidCreditLine?

    // Real ongoing late-fee accrual sweep -- see PostpaidCreditAccrualScheduler's own
    // doc comment. A real line keeps accruing while overdue regardless of ACTIVE vs
    // already-SUSPENDED status (unlike a fully-repaid line, real balance zero, which
    // never appears here at all) -- coarse repo filter (every real nonzero balance),
    // exact overdue-or-not/24h-since-last-accrual logic in the service, same discipline
    // this codebase's other scheduled sweeps (OverdraftInterestAccrualScheduler) use.
    fun findByCurrentBalanceGreaterThan(amount: BigDecimal): List<PostpaidCreditLine>
}
