package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.OverdraftAccount
import rw.itunda.core.domain.OverdraftAccountStatus

interface OverdraftAccountRepository : JpaRepository<OverdraftAccount, String> {
    fun findByUserId(userId: String): List<OverdraftAccount>
    fun findByUserIdAndStatus(userId: String, status: OverdraftAccountStatus): OverdraftAccount?

    // Real daily interest accrual sweep (2026-07-27) -- see
    // OverdraftInterestAccrualScheduler's own doc comment. Coarse repo filter (every
    // real ACTIVE account), exact due-or-not/zero-balance logic in the service, same
    // discipline this codebase's other scheduled sweeps already use.
    fun findByStatus(status: OverdraftAccountStatus): List<OverdraftAccount>
}
