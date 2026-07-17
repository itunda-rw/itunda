package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

/**
 * A real B2B payroll roster row -- one employee an itunda merchant has agreed to pay a
 * fixed recurring salary. Closes the "B2B payroll remains not built" gap named in
 * Merchant.kt's own doc comment: unlike card processing/NIDA/PSP integrations, payroll
 * needs no external credentials at all -- disbursing to an employee's own itunda wallet
 * is the same real WALLET-to-WALLET ledger movement P2pService.payRequest already does,
 * just to many recipients per run instead of one. See rw.itunda.merchant.PayrollService.
 */
@Entity
@Table(name = "payroll_employees")
class PayrollEmployee(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "merchant_id", nullable = false, length = 64)
    val merchantId: String,

    @Column(name = "employee_user_id", nullable = false, length = 64)
    val employeeUserId: String,

    @Column(name = "employee_name", nullable = false)
    var employeeName: String,

    @Column(name = "salary_amount", nullable = false, precision = 18, scale = 2)
    var salaryAmount: BigDecimal,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", merchantId = "", employeeUserId = "", employeeName = "", salaryAmount = BigDecimal.ZERO)
}

/** One real, atomic payroll disbursement -- the audit-trail parent of a batch of Payslips. */
@Entity
@Table(name = "payroll_runs")
class PayrollRun(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "merchant_id", nullable = false, length = 64)
    val merchantId: String,

    @Column(name = "ledger_transaction_id", nullable = false, length = 64)
    val ledgerTransactionId: String,

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    val totalAmount: BigDecimal,

    @Column(name = "employee_count", nullable = false)
    val employeeCount: Int,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", merchantId = "", ledgerTransactionId = "", totalAmount = BigDecimal.ZERO, employeeCount = 0)
}

/** One employee's real line item within a PayrollRun, linked to its own real Transaction row. */
@Entity
@Table(name = "payslips")
class Payslip(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "payroll_run_id", nullable = false, length = 64)
    val payrollRunId: String,

    @Column(name = "employee_user_id", nullable = false, length = 64)
    val employeeUserId: String,

    @Column(name = "employee_name", nullable = false)
    val employeeName: String,

    @Column(nullable = false, precision = 18, scale = 2)
    val amount: BigDecimal,

    @Column(name = "transaction_id", nullable = false, length = 64)
    val transactionId: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", payrollRunId = "", employeeUserId = "", employeeName = "", amount = BigDecimal.ZERO, transactionId = "")
}
