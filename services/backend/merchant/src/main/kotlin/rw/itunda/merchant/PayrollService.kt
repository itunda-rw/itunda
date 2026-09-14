package rw.itunda.merchant

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.PayrollEmployee
import rw.itunda.core.domain.PayrollRun
import rw.itunda.core.domain.Payslip
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.AccountType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.PayrollEmployeeRepository
import rw.itunda.core.repository.PayrollRunRepository
import rw.itunda.core.repository.PayslipRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class EmployeeNotFoundException(message: String) : RuntimeException(message)
class EmployeeAlreadyOnRosterException(message: String) : RuntimeException(message)
class EmployeeIsOwnerException(message: String) : RuntimeException(message)
class EmployeeNoAccountException(message: String) : RuntimeException(message)
class InvalidSalaryAmountException(message: String) : RuntimeException(message)
class EmptyPayrollRosterException(message: String) : RuntimeException(message)
class PayrollRosterEntryNotFoundException(message: String) : RuntimeException(message)
class PayrollRunNotFoundException(message: String) : RuntimeException(message)

/**
 * Real B2B payroll -- closes the gap Merchant.kt's own doc comment named ("B2B payroll
 * genuinely needs real PSP-level infrastructure this repo has no path to certify").
 * That's true for card networks and NIDA, which need an external vendor relationship
 * this repo can't obtain -- but payroll disbursed to an employee's own itunda account is
 * a real ACCOUNT-to-ACCOUNT ledger movement between two known itunda accounts, exactly
 * what P2pService.payRequest already proved out (its own doc comment: "the first real
 * account-to-account money movement in the backend where both sides are known itunda
 * accounts"). No external credentials, no demo simulation needed -- this is real money
 * movement, not a demo, unlike the card/NIDA/reconciliation/external-balance gaps this
 * pass closed with a simulated outcome.
 *
 * One payroll run posts a single, atomic multi-leg ledger transaction (one merchant-
 * account DEBIT for the roster total, one employee-account CREDIT per active employee) --
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
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService,
    private val transactionRepository: TransactionRepository,
    private val fraudRuleEngine: FraudRuleEngine,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
) {
    private val log = LoggerFactory.getLogger(PayrollService::class.java)

    // Real gap found 2026-09-05: PayrollEmployee.employeeName has no explicit @Column
    // length (Hibernate's 255 default), but firstName (bounded 234 chars by
    // AuthService.register) and lastName (bounded 255, not concatenated anywhere at
    // registration time) can combine to ~490 chars once joined with a space here --
    // nearly double employeeName's own column capacity, for a user who could register
    // TODAY, not just legacy data. Truncating rather than rejecting the addEmployee
    // call: the employee's own registered name isn't this merchant's call to reject,
    // and a truncated payroll-roster display name is a far better outcome than a raw
    // STRICT_TRANS_TABLES 500 on an otherwise-valid payroll action.
    private fun employeeDisplayName(firstName: String, lastName: String) =
        "$firstName $lastName".take(255)

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
        // Every registered itunda user gets a MAIN account at signup (AuthService.register),
        // so this is a defensive check, not an expected path -- same reasoning P2pService's
        // own P2pNoAccountException comment gives for the identical check on its side.
        accountRepository.findByUserIdAndType(employeeUser.id, AccountType.MAIN)
            ?: throw EmployeeNoAccountException("This account has no account to receive payroll")

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
            existing.employeeName = employeeDisplayName(employeeUser.firstName, employeeUser.lastName)
            return payrollEmployeeRepository.save(existing)
        }

        val employee = PayrollEmployee(
            id = "payroll_emp_${UUID.randomUUID()}",
            merchantId = merchant.id,
            employeeUserId = employeeUser.id,
            employeeName = employeeDisplayName(employeeUser.firstName, employeeUser.lastName),
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
        val merchantAccount = accountRepository.findById(merchant.accountId)
            .orElseThrow { MerchantNoAccountException("Merchant settlement account not found") }
        val roster = payrollEmployeeRepository.findByMerchantIdAndActiveTrue(merchant.id)
        if (roster.isEmpty()) {
            throw EmptyPayrollRosterException("No active employees on the payroll roster")
        }

        // Batch-fetched in one query rather than one findByUserIdAndType call per roster
        // row -- a real N+1 fixed live during this pass's own performance review (a
        // payroll run for a large roster was issuing N account lookups instead of 1).
        val accountsByUserId = accountRepository.findByUserIdInAndType(
            roster.map { it.employeeUserId }, AccountType.MAIN,
        ).associateBy { it.userId }
        val employeeAccounts = roster.associateWith { employee ->
            accountsByUserId[employee.employeeUserId]
                ?: throw EmployeeNoAccountException("${employee.employeeName} has no account to receive payroll")
        }
        val totalAmount = roster.fold(BigDecimal.ZERO) { acc, employee -> acc + employee.salaryAmount }

        val legs = mutableListOf(
            LedgerLeg(merchantAccount.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, totalAmount, "Payroll run - ${merchant.businessName}"),
        )
        roster.forEach { employee ->
            legs.add(
                LedgerLeg(
                    employeeAccounts.getValue(employee).id, LedgerAccountType.WALLET, LedgerDirection.CREDIT,
                    employee.salaryAmount, "Salary payment - ${merchant.businessName}",
                ),
            )
        }
        val result = ledgerService.postLedgerTransaction(merchantAccount.currency, legs)

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
        // fraudRuleEngine.evaluate() is called before this batch's own transactionRepository
        // .saveAll() below, same ordering P2pService.payRequest and MerchantService.collect
        // already established (evaluating after save lets a transaction match itself).
        // Real N+1 fix (2026-09-14, closure-syntax sweep continuation): transactionRepository
        // .save() used to run once per roster employee here -- the exact same "half the
        // discipline applied" shape this method's own accountRepository batching above
        // already fixed, just one step further down in the same method. transaction.id
        // is a client-generated UUID (not DB-assigned), so building every Transaction
        // first and saving them all in one saveAll() below changes nothing about
        // ordering or the fraud-evaluation-before-persistence guarantee.
        val transactions = roster.map { employee ->
            val account = employeeAccounts.getValue(employee)
            val transaction = Transaction(
                id = "payrolltxn_${UUID.randomUUID()}",
                referenceNumber = "PAYROLL${System.currentTimeMillis()}${UUID.randomUUID().toString().take(4)}",
                senderId = merchant.ownerUserId,
                recipientId = employee.employeeUserId,
                fromAccountId = merchantAccount.id,
                toAccountId = account.id,
                amount = employee.salaryAmount,
                fee = BigDecimal.ZERO,
                currency = merchantAccount.currency,
                type = TransactionType.TRANSFER,
                status = TransactionStatus.COMPLETED,
                description = "Salary payment - ${merchant.businessName}",
                channel = "PAYROLL",
                completedAt = Instant.now(),
            )
            fraudRuleEngine.evaluate(merchant.ownerUserId, employee.employeeUserId, employee.salaryAmount, transaction.id)
            transaction
        }
        transactionRepository.saveAll(transactions)
        val payslips = roster.zip(transactions).map { (employee, transaction) ->
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

        // Real sibling-asymmetry fix (2026-09-13) -- this class's own doc comment
        // claims parity with P2pService.payRequest ("the first real account-to-account
        // money movement... where both sides are known itunda accounts"), which
        // notifies both sides on every transfer. This real salary payment notified
        // neither: an employee's account is credited with zero way to know it
        // happened except by polling their transaction history.
        roster.forEach { employee -> notifySalaryReceived(employee, merchant.businessName) }
        notifyPayrollRunCompleted(merchant.ownerUserId, merchant.businessName, roster.size, totalAmount)

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

    // Best-effort: a notification failure must never roll back or fail money that
    // already moved, same "auxiliary side-effect can't block real money movement"
    // discipline P2pNotificationService.notifyMoneyReceived already establishes.
    private fun notifySalaryReceived(employee: PayrollEmployee, businessName: String) {
        try {
            val title = "Salary received"
            val body = "$businessName paid you ${employee.salaryAmount} RWF."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = employee.employeeUserId, type = "SALARY_RECEIVED",
                    title = title, body = body, isRead = false, createdAt = Instant.now(),
                    dataJson = "{\"amount\":\"${employee.salaryAmount}\"}",
                ),
            )
            sendPushAfterCommit(employee.employeeUserId, title, body)
        } catch (e: Exception) {
            log.warn("Failed to notify employee {} of salary received", employee.employeeUserId, e)
        }
    }

    private fun notifyPayrollRunCompleted(ownerUserId: String, businessName: String, employeeCount: Int, totalAmount: BigDecimal) {
        try {
            val title = "Payroll run completed"
            val body = "You paid $employeeCount employee(s) a total of $totalAmount RWF at $businessName."
            notificationRepository.save(
                Notification(
                    id = "notif_${UUID.randomUUID()}", userId = ownerUserId, type = "PAYROLL_RUN_COMPLETED",
                    title = title, body = body, isRead = false, createdAt = Instant.now(),
                    dataJson = "{\"employeeCount\":\"$employeeCount\",\"totalAmount\":\"$totalAmount\"}",
                ),
            )
            sendPushAfterCommit(ownerUserId, title, body)
        } catch (e: Exception) {
            log.warn("Failed to notify merchant owner {} of payroll run completion", ownerUserId, e)
        }
    }

    private fun sendPushAfterCommit(userId: String, title: String, body: String) {
        val send = { pushNotificationService.sendToUser(userId, title, body) }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
    }
}
