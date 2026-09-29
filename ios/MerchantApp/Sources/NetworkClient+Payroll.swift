import Foundation

// Split out of NetworkClient.swift (2026-08-30, same file-size-lint "extract instead
// of growing a baselined file" discipline as NetworkClient+VisitorAnalytics.swift,
// which already established this exact pattern -- post/postWithHeader/delete/
// EmptyBody widened from private to internal there, zero line-count change, so this
// extension can reuse them). NetworkClient.swift was already at its exact recorded
// baseline with zero headroom, needed to make real room for the merchant-updates/
// photo-gallery endpoints (see NetworkClient+MerchantExtras.swift).
//
// Real B2B payroll -- real account-to-account money movement (see PayrollController.kt's
// own doc comment), real on merchant-mfe/web + Android only until now -- zero iOS UI.
struct AddPayrollEmployeeRequest: Encodable { let phoneNumber: String; let salaryAmount: Double }
struct PayrollEmployeeDto: Decodable, Identifiable {
    let id: String
    let merchantId: String
    let employeeUserId: String
    let employeeName: String
    let salaryAmount: Double
    let active: Bool
    let createdAt: String
}
struct PayrollEmployeeResponse: Decodable { let success: Bool; let employee: PayrollEmployeeDto }
struct PayrollRosterResponse: Decodable { let success: Bool; let employees: [PayrollEmployeeDto] }
struct PayslipDto: Decodable, Identifiable {
    let id: String
    let payrollRunId: String
    let employeeUserId: String
    let employeeName: String
    let amount: Double
    let transactionId: String
    let createdAt: String
}
// PayrollService.runPayroll's own response returns a lighter line-item shape than the
// full Payslip entity (no id/payrollRunId/employeeUserId/createdAt).
struct RunPayslipDto: Decodable, Identifiable { let employeeName: String; let amount: Double; let transactionId: String; var id: String { transactionId } }
struct PayrollRunResponse: Decodable {
    let success: Bool
    let payrollRunId: String
    let totalAmount: Double
    let employeeCount: Int
    let completedAt: String
    let payslips: [RunPayslipDto]
}
struct PayrollRunDto: Decodable, Identifiable {
    let id: String
    let merchantId: String
    let ledgerTransactionId: String
    let totalAmount: Double
    let employeeCount: Int
    let createdAt: String
}
struct PayrollHistoryResponse: Decodable { let success: Bool; let runs: [PayrollRunDto] }
struct PayslipsResponse: Decodable { let success: Bool; let payslips: [PayslipDto] }

extension MerchantNetworkClient {
    // Real B2B payroll -- see PayrollController.kt's own doc comment. merchant-mfe/
    // Android already have this; this is the first iOS client.
    func addPayrollEmployee(_ request: AddPayrollEmployeeRequest) async throws -> PayrollEmployeeResponse {
        try await postWithHeader("api/v1/merchant/payroll/employees", body: request, header: ("Idempotency-Key", UUID().uuidString))
    }
    func getPayrollRoster() async throws -> PayrollRosterResponse { try await get("api/v1/merchant/payroll/employees") }
    func removePayrollEmployee(_ employeeId: String) async throws -> PayrollEmployeeResponse {
        try await delete("api/v1/merchant/payroll/employees/\(employeeId)")
    }
    func runPayroll() async throws -> PayrollRunResponse {
        try await postWithHeader("api/v1/merchant/payroll/run", body: EmptyBody(), header: ("Idempotency-Key", UUID().uuidString))
    }
    func getPayrollHistory() async throws -> PayrollHistoryResponse { try await get("api/v1/merchant/payroll/runs") }
    func getPayslips(_ runId: String) async throws -> PayslipsResponse { try await get("api/v1/merchant/payroll/runs/\(runId)/payslips") }
}
