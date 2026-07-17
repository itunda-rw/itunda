package rw.itunda.merchant

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.PayrollEmployee
import rw.itunda.core.domain.PayrollRun
import rw.itunda.core.domain.Payslip
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.WalletType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.PayrollEmployeeRepository
import rw.itunda.core.repository.PayrollRunRepository
import rw.itunda.core.repository.PayslipRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class EmployeeNotFoundException(message: String) : RuntimeException(message)
class EmployeeAlreadyOnRosterException(message: String) : RuntimeException(message)
class EmployeeIsOwnerException(message: String) : RuntimeException(message)
class EmployeeNoWalletException(message: String) : RuntimeException(message)
class InvalidSalaryAmountException(message: String) : RuntimeException(message)
class EmptyPayrollRosterException(message: String) : RuntimeException(message)
class PayrollRosterEntryNotFoundException(message: String) : RuntimeException(message)
class PayrollRunNotFoundException(message: String) : RuntimeException(message)

/**
 * Real B2B payroll -- closes the gap Merchant.kt's own doc comment named ("B2B payroll
 * genuinely needs real PSP-level infrastructure this repo has no path to certify").
 * That's true for card networks and NIDA, which need an external vendor relationship
 * this repo can't obtain -- but payroll disbursed to an employee's own itunda wallet is
 * a real WALLET-to-WALLET ledger movement between two known itunda accounts, exactly
 * what P2pService.payRequest already proved out (its own doc comment: "the first real
 * wallet-to-wallet money movement in the backend where both sides are known itunda
 * accounts"). No external credentials, no demo simulation needed -- this is real money
 * movement, not a demo, unlike the card/NIDA/reconciliation/external-balance gaps this
 * pass closed with a simulated outcome.
 *
 * One payroll run posts a single, atomic multi-leg ledger transaction (one merchant-
 * wallet DEBIT for the roster total, one employee-wallet CREDIT per active employee) --
 * LedgerService.postLedgerTransaction already supports an arbitrary leg count and locks
 * every account touched in a stable sorted order, so this needed zero ledger-layer
 * changes. All-or-nothing by construction: if the merchant's balance can't cover the
 * full roster, the whole run fails and nobody is partially paid, matching how a real
 * payroll batch either clears or doesn't.
 */
@Service
class PayrollService(
    private val merchantRepository: MerchantRepository,
    private val payrollEmployeeRepository: PayrollEmployeeRepository,
    private val payrollRunRepository: PayrollRunRepository,
    private val payslipRepository: PayslipRepository,
    private val userRepository: UserRepository,
    private val walletRepository: WalletRepository,
    private val ledgerService: LedgerService,
    private val transactionRepository: TransactionRepository,
    private val fraudRuleEngine: FraudRuleEngine,
) {
    private fun getMyMerchant(ownerUserId: String) =
        merchantRepository.findByOwnerUserId(ownerUserId)
            ?: throw MerchantNotFoundException("This account is not registered as a merchant")

    @Transactional
    fun addEmployee(ownerUserId: String, phoneNumber: String, salaryAmount: BigDecimal): PayrollEmployee {
        val merchant = getMyMerchant(ownerUserId)
        if (salaryAmount <= BigDecimal.ZERO) {
            throw InvalidSalaryAmountException("Salary amount must be greater than zero")
        }
        val employeeUser = userRepository.findByPhoneNumber(phoneNumber)
            ?: throw EmployeeNotFoundException("No itunda account found for this phone number")
        if (employeeUser.id == merchant.ownerUserId) {
            throw EmployeeIsOwnerException("Cannot add the business owner as a payroll employee")
        }
        // Every registered itunda user gets a MAIN wallet at signup (AuthService.register),
        // so this is a defensive check, not an expected path -- same reasoning P2pService's
        // own P2pNoWalletException comment gives for the identical check on its side.
        walletRepository.findByUserIdAndType(employeeUser.id, WalletType.MAIN)
            ?: throw EmployeeNoWalletException("This account has no wallet to receive payroll")

        // A prior removeEmployee() call leaves an inactive row here rather than deleting it
        // (see removeEmployee's own comment on why), and the DB's unique (merchant_id,
        // employee_user_id) constraint means a second insert for the same pair would fail
        // outright -- so a rehire reactivates the existing row (with the newly given salary)
        // instead of either blocking forever or attempting an impossible duplicate insert.
        val existing = payrollEmployeeRepository.findByMerchantIdAndEmployeeUserId(merchant.id, employeeUser.id)
        if (existing != null) {
            if (existing.active) {
                throw EmployeeAlreadyOnRosterException("This person is already on the payroll roster")
            }
            existing.active = true
            existing.salaryAmount = salaryAmount
            existing.employeeName = "${employeeUser.firstName} ${employeeUser.lastName}"
            return payrollEmployeeRepository.save(existing)
        }

        val employee = PayrollEmployee(
            id = "payroll_emp_${UUID.randomUUID()}",
            merchantId = merchant.id,
            employeeUserId = employeeUser.id,
            employeeName = "${employeeUser.firstName} ${employeeUser.lastName}",
            salaryAmount = salaryAmount,
        )
        return payrollEmployeeRepository.save(employee)
    }

    fun getRoster(ownerUserId: String): List<PayrollEmployee> {
        val merchant = getMyMerchant(ownerUserId)
        return payrollEmployeeRepository.findByMerchantIdAndActiveTrue(merchant.id)
    }

    @Transactional
    fun removeEmployee(ownerUserId: String, employeeId: String): PayrollEmployee {
        val merchant = getMyMerchant(ownerUserId)
        val employee = payrollEmployeeRepository.findById(employeeId)
            .orElseThrow { PayrollRosterEntryNotFoundException("Payroll roster entry not found") }
        if (employee.merchantId != merchant.id) {
            throw PayrollRosterEntryNotFoundException("Payroll roster entry not found")
        }
        employee.active = false
        return payrollEmployeeRepository.save(employee)
    }

    @Transactional
    fun runPayroll(ownerUserId: String): Map<String, Any?> {
        val merchant = getMyMerchant(ownerUserId)
        val merchantWallet = walletRepository.findById(merchant.walletId)
            .orElseThrow { MerchantNoWalletException("Merchant settlement wallet not found") }
        val roster = payrollEmployeeRepository.findByMerchantIdAndActiveTrue(merchant.id)
        if (roster.isEmpty()) {
            throw EmptyPayrollRosterException("No active employees on the payroll roster")
        }

        // Batch-fetched in one query rather than one findByUserIdAndType call per roster
        // row -- a real N+1 fixed live during this pass's own performance review (a
        // payroll run for a large roster was issuing N wallet lookups instead of 1).
        val walletsByUserId = walletRepository.findByUserIdInAndType(
            roster.map { it.employeeUserId }, WalletType.MAIN,
        ).associateBy { it.userId }
        val employeeWallets = roster.associateWith { employee ->
            walletsByUserId[employee.employeeUserId]
                ?: throw EmployeeNoWalletException("${employee.employeeName} has no wallet to receive payroll")
        }
        val totalAmount = roster.fold(BigDecimal.ZERO) { acc, employee -> acc + employee.salaryAmount }

        val legs = mutableListOf(
            LedgerLeg(merchantWallet.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, totalAmount, "Payroll run - ${merchant.businessName}"),
        )
        roster.forEach { employee ->
            legs.add(
                LedgerLeg(
                    employeeWallets.getValue(employee).id, LedgerAccountType.WALLET, LedgerDirection.CREDIT,
                    employee.salaryAmount, "Salary payment - ${merchant.businessName}",
                ),
            )
        }
        val result = ledgerService.postLedgerTransaction(merchantWallet.currency, legs)

        val run = payrollRunRepository.save(
            PayrollRun(
                id = "payroll_run_${UUID.randomUUID()}",
                merchantId = merchant.id,
                ledgerTransactionId = result.transactionId,
                totalAmount = totalAmount,
                employeeCount = roster.size,
            ),
        )

        // One real Transaction row per employee (not just ledger entries) so a salary
        // payment shows up in the employee's own transaction history and is visible to
        // FraudRuleEngine's velocity/new-recipient checks -- same reasoning
        // MerchantService.collect's own comment gives for why this can't be skipped.
        // fraudRuleEngine.evaluate() is called before transactionRepository.save() for
        // each employee, same ordering P2pService.payRequest and MerchantService.collect
        // already established (evaluating after save lets a transaction match itself).
        val payslips = roster.map { employee ->
            val wallet = employeeWallets.getValue(employee)
            val transaction = Transaction(
                id = "payrolltxn_${UUID.randomUUID()}",
                referenceNumber = "PAYROLL${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
                senderId = merchant.ownerUserId,
                recipientId = employee.employeeUserId,
                fromWalletId = merchantWallet.id,
                toWalletId = wallet.id,
                amount = employee.salaryAmount,
                fee = BigDecimal.ZERO,
                currency = merchantWallet.currency,
                type = TransactionType.TRANSFER,
                status = TransactionStatus.COMPLETED,
                description = "Salary payment - ${merchant.businessName}",
                channel = "PAYROLL",
                completedAt = Instant.now(),
            )
            fraudRuleEngine.evaluate(merchant.ownerUserId, employee.employeeUserId, employee.salaryAmount, transaction.id)
            transactionRepository.save(transaction)
            Payslip(
                id = "payslip_${UUID.randomUUID()}",
                payrollRunId = run.id,
                employeeUserId = employee.employeeUserId,
                employeeName = employee.employeeName,
                amount = employee.salaryAmount,
                transactionId = transaction.id,
            )
        }
        payslipRepository.saveAll(payslips)

        return mapOf(
            "payrollRunId" to run.id,
            "totalAmount" to totalAmount,
            "employeeCount" to roster.size,
            "completedAt" to Instant.now().toString(),
            "payslips" to payslips.map {
                mapOf("employeeName" to it.employeeName, "amount" to it.amount, "transactionId" to it.transactionId)
            },
        )
    }

    fun getPayrollHistory(ownerUserId: String): List<PayrollRun> {
        val merchant = getMyMerchant(ownerUserId)
        return payrollRunRepository.findByMerchantIdOrderByCreatedAtDesc(merchant.id)
    }

    fun getPayslips(ownerUserId: String, payrollRunId: String): List<Payslip> {
        val merchant = getMyMerchant(ownerUserId)
        val run = payrollRunRepository.findById(payrollRunId)
            .orElseThrow { PayrollRunNotFoundException("Payroll run not found") }
        if (run.merchantId != merchant.id) {
            throw PayrollRunNotFoundException("Payroll run not found")
        }
        return payslipRepository.findByPayrollRunId(payrollRunId)
    }
}
