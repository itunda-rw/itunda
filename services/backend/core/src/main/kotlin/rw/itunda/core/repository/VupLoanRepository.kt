package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.VupLoan
import rw.itunda.core.domain.VupLoanStatus

interface VupLoanRepository : JpaRepository<VupLoan, String> {
    fun findByUserId(userId: String): List<VupLoan>

    // "One active loan at a time" eligibility check -- active means
    // REQUESTED/DISBURSED/OVERDUE, not REPAID.
    fun findByUserIdAndStatusIn(userId: String, statuses: List<VupLoanStatus>): List<VupLoan>

    fun findByStatus(status: VupLoanStatus): List<VupLoan>
}
