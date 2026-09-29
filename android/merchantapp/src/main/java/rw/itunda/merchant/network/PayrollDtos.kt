package rw.itunda.merchant.network

// Split out of ApiService.kt (2026-08-30, same file-size-lint "extract instead of
// growing a baselined file" discipline used repeatedly this session) -- that file was
// already at its exact recorded baseline with zero headroom, needed to make real room
// for the merchant-updates/photo-gallery endpoints (see MerchantExtrasDtos.kt). Same
// package as ApiService.kt's own `interface ApiService`, so its payroll methods resolve
// these types with no import needed -- Kotlin same-package visibility, not re-exported.
//
// Real B2B payroll -- real account-to-account money movement (see PayrollController.kt's
// own doc comment), real on merchant-mfe/web only until now -- zero native UI on
// either merchantapp or ItundaMerchantApp despite the backend being mature.
data class AddPayrollEmployeeRequest(val phoneNumber: String, val salaryAmount: java.math.BigDecimal)
data class PayrollEmployeeDto(
    val id: String, val merchantId: String, val employeeUserId: String, val employeeName: String,
    val salaryAmount: java.math.BigDecimal, val active: Boolean, val createdAt: String,
)
data class PayrollEmployeeResponse(val success: Boolean, val employee: PayrollEmployeeDto)
data class PayrollRosterResponse(val success: Boolean, val employees: List<PayrollEmployeeDto>)
data class PayslipDto(
    val id: String, val payrollRunId: String, val employeeUserId: String, val employeeName: String,
    val amount: java.math.BigDecimal, val transactionId: String, val createdAt: String,
)
// PayrollService.runPayroll's own response returns a lighter line-item shape than the
// full Payslip entity (no id/payrollRunId/employeeUserId/createdAt) -- distinct from
// PayslipDto above, which mirrors getPayslips()'s real entity-backed response.
data class RunPayslipDto(val employeeName: String, val amount: java.math.BigDecimal, val transactionId: String)
data class PayrollRunResponse(
    val success: Boolean, val payrollRunId: String, val totalAmount: java.math.BigDecimal,
    val employeeCount: Int, val completedAt: String, val payslips: List<RunPayslipDto>,
)
data class PayrollRunDto(
    val id: String, val merchantId: String, val ledgerTransactionId: String,
    val totalAmount: java.math.BigDecimal, val employeeCount: Int, val createdAt: String,
)
data class PayrollHistoryResponse(val success: Boolean, val runs: List<PayrollRunDto>)
data class PayslipsResponse(val success: Boolean, val payslips: List<PayslipDto>)
