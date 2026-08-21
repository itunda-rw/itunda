package rw.itunda.merchant

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.AccountFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal

data class AddPayrollEmployeeRequest(val phoneNumber: String, val salaryAmount: BigDecimal)

/**
 * Real B2B payroll endpoints -- see PayrollService's own doc comment for why this needs
 * no external credentials, unlike the rest of docs/TOSS_PARITY_MATRIX.md's blocked
 * Merchant-row gaps. Roster reads/writes aren't money-moving (no Idempotency-Key);
 * runPayroll is (same convention as /collect, /card/charge).
 */
@RestController
@RequestMapping("/api/v1/merchant/payroll")
class PayrollController(
    private val payrollService: PayrollService,
    private val idempotencyService: IdempotencyService,
) {
    @PostMapping("/employees")
    fun addEmployee(
        @RequestBody request: AddPayrollEmployeeRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val employee = payrollService.addEmployee(currentUser.userId, request.phoneNumber, request.salaryAmount)
        return ResponseEntity.ok(mapOf("success" to true, "employee" to employee))
    }

    @GetMapping("/employees")
    fun getRoster(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "employees" to payrollService.getRoster(currentUser.userId)))

    @DeleteMapping("/employees/{employeeId}")
    fun removeEmployee(
        @PathVariable employeeId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val employee = payrollService.removeEmployee(currentUser.userId, employeeId)
        return ResponseEntity.ok(mapOf("success" to true, "employee" to employee))
    }

    @PostMapping("/run")
    fun runPayroll(
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/merchant/payroll/run", idempotencyKey, currentUser.userId) {
            200 to payrollService.runPayroll(currentUser.userId)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/runs")
    fun getPayrollHistory(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "runs" to payrollService.getPayrollHistory(currentUser.userId)))

    @GetMapping("/runs/{runId}/payslips")
    fun getPayslips(
        @PathVariable runId: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "payslips" to payrollService.getPayslips(currentUser.userId, runId)))

    @ExceptionHandler(MerchantNotFoundException::class)
    fun handleMerchantNotFound(ex: MerchantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(MerchantNoAccountException::class)
    fun handleMerchantNoAccount(ex: MerchantNoAccountException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(EmployeeNotFoundException::class)
    fun handleEmployeeNotFound(ex: EmployeeNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("EMPLOYEE_PHONE_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(EmployeeAlreadyOnRosterException::class)
    fun handleAlreadyOnRoster(ex: EmployeeAlreadyOnRosterException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("EMPLOYEE_ALREADY_ON_ROSTER", ex.message ?: "Conflict"))

    @ExceptionHandler(EmployeeIsOwnerException::class)
    fun handleEmployeeIsOwner(ex: EmployeeIsOwnerException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("EMPLOYEE_IS_OWNER", ex.message ?: "Bad request"))

    @ExceptionHandler(EmployeeNoAccountException::class)
    fun handleEmployeeNoAccount(ex: EmployeeNoAccountException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("EMPLOYEE_NO_WALLET", ex.message ?: "Unprocessable"))

    @ExceptionHandler(InvalidSalaryAmountException::class)
    fun handleInvalidSalary(ex: InvalidSalaryAmountException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_SALARY_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(EmptyPayrollRosterException::class)
    fun handleEmptyRoster(ex: EmptyPayrollRosterException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("EMPTY_PAYROLL_ROSTER", ex.message ?: "Unprocessable"))

    @ExceptionHandler(PayrollRosterEntryNotFoundException::class)
    fun handleRosterEntryNotFound(ex: PayrollRosterEntryNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("PAYROLL_ROSTER_ENTRY_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(PayrollRunNotFoundException::class)
    fun handleRunNotFound(ex: PayrollRunNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("PAYROLL_RUN_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(AccountFrozenException::class)
    fun handleAccountFrozen(ex: AccountFrozenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("WALLET_FROZEN", ex.message ?: "Account is frozen"))
}
