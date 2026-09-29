package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.VupLoan
import rw.itunda.core.domain.VupLoanStatus

interface VupLoanRepository : JpaRepository<VupLoan, String> {
    fun findByUserId(userId: String): List<VupLoan>

    // "One active loan at a time" eligibility check -- active means
    // REQUESTED/DISBURSED/OVERDUE, not REPAID.
    fun findByUserIdAndStatusIn(userId: String, statuses: List<VupLoanStatus>): List<VupLoan>

    fun findByStatus(status: VupLoanStatus): List<VupLoan>

    // Real ops loan-default review queue (2026-09-06) -- OVERDUE loans an admin
    // hasn't reviewed yet. reviewedAt IS NULL rather than a separate boolean:
    // once decide() sets it, the loan drops off this queue whether it was
    // written off or just acknowledged, same convention
    // PropertyOwnershipSubmissionRepository's own status-based queue uses.
    fun findByStatusAndReviewedAtIsNull(status: VupLoanStatus, pageable: Pageable): Page<VupLoan>
}
