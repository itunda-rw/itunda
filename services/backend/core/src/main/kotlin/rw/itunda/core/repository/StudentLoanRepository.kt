package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.StudentLoan
import rw.itunda.core.domain.StudentLoanStatus

interface StudentLoanRepository : JpaRepository<StudentLoan, String> {
    fun findByUserId(userId: String): List<StudentLoan>

    // "One active loan at a time" eligibility check -- active means everything except
    // REPAID.
    fun findByUserIdAndStatusIn(userId: String, statuses: List<StudentLoanStatus>): List<StudentLoan>
}
