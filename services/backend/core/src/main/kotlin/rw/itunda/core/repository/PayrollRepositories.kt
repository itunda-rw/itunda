package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.PayrollEmployee
import rw.itunda.core.domain.PayrollRun
import rw.itunda.core.domain.Payslip

interface PayrollEmployeeRepository : JpaRepository<PayrollEmployee, String> {
    fun findByMerchantIdAndActiveTrue(merchantId: String): List<PayrollEmployee>
    fun findByMerchantIdAndEmployeeUserId(merchantId: String, employeeUserId: String): PayrollEmployee?
}

interface PayrollRunRepository : JpaRepository<PayrollRun, String> {
    fun findByMerchantIdOrderByCreatedAtDesc(merchantId: String): List<PayrollRun>
}

interface PayslipRepository : JpaRepository<Payslip, String> {
    fun findByPayrollRunId(payrollRunId: String): List<Payslip>
}
