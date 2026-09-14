package rw.itunda.merchant

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.PayrollEmployee
import rw.itunda.core.domain.PayrollRun
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.PayrollEmployeeRepository
import rw.itunda.core.repository.PayrollRunRepository
import rw.itunda.core.repository.PayslipRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.util.Optional

class PayrollServiceTest : BehaviorSpec({

    fun account(id: String, userId: String) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal("500000"), availableBalance = BigDecimal("500000"),
    )

    fun user(id: String, phone: String, first: String, last: String) = User(
        id = id, phoneNumber = phone, firstName = first, lastName = last, passwordHash = "hash",
    )

    val merchant = Merchant(
        id = "merchant_1", ownerUserId = "owner_1", accountId = "account_merchant",
        businessName = "Kigali Coffee", status = MerchantStatus.ACTIVE,
    )

    fun buildService(
        merchantRepository: MerchantRepository = mockk(),
        payrollEmployeeRepository: PayrollEmployeeRepository = mockk(),
        payrollRunRepository: PayrollRunRepository = mockk(),
        payslipRepository: PayslipRepository = mockk(relaxed = true),
        userRepository: UserRepository = mockk(),
        accountRepository: AccountRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        transactionRepository: TransactionRepository = mockk(relaxed = true),
        fraudRuleEngine: FraudRuleEngine = mockk(relaxed = true),
        notificationRepository: NotificationRepository = mockk<NotificationRepository>(relaxed = true).also { repo -> every { repo.save(any()) } answers { firstArg() } },
        pushNotificationService: PushNotificationService = mockk(relaxed = true),
    ) = PayrollService(
        merchantRepository, payrollEmployeeRepository, payrollRunRepository, payslipRepository,
        userRepository, accountRepository, ledgerService, transactionRepository, fraudRuleEngine,
        notificationRepository, pushNotificationService,
    )

    Given("a registered merchant adding an employee to the payroll roster") {
        val merchantRepository = mockk<MerchantRepository>()
        val payrollEmployeeRepository = mockk<PayrollEmployeeRepository>()
        val userRepository = mockk<UserRepository>()
        val accountRepository = mockk<AccountRepository>()
        every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
        every { payrollEmployeeRepository.save(any()) } answers { firstArg() }
        val service = buildService(
            merchantRepository = merchantRepository, payrollEmployeeRepository = payrollEmployeeRepository,
            userRepository = userRepository, accountRepository = accountRepository,
        )

        When("the phone number resolves to a real, distinct itunda user with a account") {
            every { userRepository.findByPhoneNumber("+250788111222") } returns user("emp_1", "+250788111222", "Alice", "U")
            every { payrollEmployeeRepository.findByMerchantIdAndEmployeeUserId("merchant_1", "emp_1") } returns null
            every { accountRepository.findByUserIdAndType("emp_1", AccountType.MAIN) } returns account("account_emp_1", "emp_1")

            val result = service.addEmployee("owner_1", "+250788111222", BigDecimal("150000"))

            Then("it's added to the roster with the real name composed from the user record") {
                result.employeeUserId shouldBe "emp_1"
                result.employeeName shouldBe "Alice U"
                result.salaryAmount shouldBe BigDecimal("150000")
                result.active shouldBe true
            }
        }

        When("the phone number doesn't resolve to any itunda account") {
            every { userRepository.findByPhoneNumber("+250700000000") } returns null

            Then("it real-fails, not a fabricated roster entry") {
                try {
                    service.addEmployee("owner_1", "+250700000000", BigDecimal("100000"))
                    error("expected EmployeeNotFoundException")
                } catch (e: EmployeeNotFoundException) {
                    // expected
                }
            }
        }

        When("the phone number resolves to the merchant owner themselves") {
            every { userRepository.findByPhoneNumber("+250788999000") } returns user("owner_1", "+250788999000", "Jean", "Owner")

            Then("it's rejected -- an owner can't be their own payroll employee") {
                try {
                    service.addEmployee("owner_1", "+250788999000", BigDecimal("100000"))
                    error("expected EmployeeIsOwnerException")
                } catch (e: EmployeeIsOwnerException) {
                    // expected
                }
            }
        }

        When("the person is already on the roster") {
            every { userRepository.findByPhoneNumber("+250788111222") } returns user("emp_1", "+250788111222", "Alice", "U")
            every { accountRepository.findByUserIdAndType("emp_1", AccountType.MAIN) } returns account("account_emp_1", "emp_1")
            every { payrollEmployeeRepository.findByMerchantIdAndEmployeeUserId("merchant_1", "emp_1") } returns
                PayrollEmployee(id = "payroll_emp_1", merchantId = "merchant_1", employeeUserId = "emp_1", employeeName = "Alice U", salaryAmount = BigDecimal("100000"))

            Then("it real-conflicts, not a silent duplicate") {
                try {
                    service.addEmployee("owner_1", "+250788111222", BigDecimal("100000"))
                    error("expected EmployeeAlreadyOnRosterException")
                } catch (e: EmployeeAlreadyOnRosterException) {
                    // expected
                }
            }
        }

        When("the person was previously removed (inactive row already exists for this pair)") {
            every { userRepository.findByPhoneNumber("+250788111222") } returns user("emp_1", "+250788111222", "Alice", "U")
            val inactive = PayrollEmployee(id = "payroll_emp_1", merchantId = "merchant_1", employeeUserId = "emp_1", employeeName = "Alice U", salaryAmount = BigDecimal("100000"), active = false)
            every { payrollEmployeeRepository.findByMerchantIdAndEmployeeUserId("merchant_1", "emp_1") } returns inactive
            every { accountRepository.findByUserIdAndType("emp_1", AccountType.MAIN) } returns account("account_emp_1", "emp_1")

            val result = service.addEmployee("owner_1", "+250788111222", BigDecimal("175000"))

            Then("the existing row is reactivated with the new salary, not blocked or duplicated") {
                result.id shouldBe "payroll_emp_1"
                result.active shouldBe true
                result.salaryAmount shouldBe BigDecimal("175000")
            }
        }

        When("the employee's own firstName+lastName would overflow employeeName's own 255-char column once joined with a space") {
            val longFirst = "x".repeat(234)
            val longLast = "y".repeat(255)
            every { userRepository.findByPhoneNumber("+250788333444") } returns user("emp_2", "+250788333444", longFirst, longLast)
            every { payrollEmployeeRepository.findByMerchantIdAndEmployeeUserId("merchant_1", "emp_2") } returns null
            every { accountRepository.findByUserIdAndType("emp_2", AccountType.MAIN) } returns account("account_emp_2", "emp_2")

            val result = service.addEmployee("owner_1", "+250788333444", BigDecimal("150000"))

            Then("the stored employeeName is truncated to the real 255-char safe bound, not the naive 490") {
                result.employeeName.length shouldBe 255
            }
        }

        When("the salary amount is zero or negative") {
            Then("it's rejected before any lookup runs") {
                try {
                    service.addEmployee("owner_1", "+250788111222", BigDecimal.ZERO)
                    error("expected InvalidSalaryAmountException")
                } catch (e: InvalidSalaryAmountException) {
                    // expected
                }
                verify(exactly = 0) { userRepository.findByPhoneNumber(any()) }
            }
        }
    }

    Given("a merchant with a real active roster of two employees") {
        val merchantRepository = mockk<MerchantRepository>()
        val payrollEmployeeRepository = mockk<PayrollEmployeeRepository>()
        val payrollRunRepository = mockk<PayrollRunRepository>()
        val payslipRepository = mockk<PayslipRepository>(relaxed = true)
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.saveAll(any<List<Transaction>>()) } answers { firstArg() }
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }

        every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
        val merchantAccount = account("account_merchant", "owner_1")
        every { accountRepository.findById("account_merchant") } returns Optional.of(merchantAccount)

        val emp1 = PayrollEmployee(id = "payroll_emp_1", merchantId = "merchant_1", employeeUserId = "emp_1", employeeName = "Alice U", salaryAmount = BigDecimal("150000"))
        val emp2 = PayrollEmployee(id = "payroll_emp_2", merchantId = "merchant_1", employeeUserId = "emp_2", employeeName = "Bob T", salaryAmount = BigDecimal("120000"))
        every { payrollEmployeeRepository.findByMerchantIdAndActiveTrue("merchant_1") } returns listOf(emp1, emp2)
        // Batch-fetched in one call (findByUserIdInAndType), not one findByUserIdAndType
        // call per roster row -- see PayrollService.runPayroll's own comment on the N+1
        // this replaced.
        every { accountRepository.findByUserIdInAndType(listOf("emp_1", "emp_2"), AccountType.MAIN) } returns
            listOf(account("account_emp_1", "emp_1"), account("account_emp_2", "emp_2"))

        val service = buildService(
            merchantRepository = merchantRepository, payrollEmployeeRepository = payrollEmployeeRepository,
            payrollRunRepository = payrollRunRepository, payslipRepository = payslipRepository,
            accountRepository = accountRepository, ledgerService = ledgerService,
            transactionRepository = transactionRepository, fraudRuleEngine = fraudRuleEngine,
            notificationRepository = notificationRepository, pushNotificationService = pushNotificationService,
        )

        When("payroll is run") {
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns
                LedgerPostResult("ledgertxn_payroll_1", emptyList())
            every { payrollRunRepository.save(any()) } answers { firstArg() }

            val result = service.runPayroll("owner_1")

            Then("it posts one balanced ledger transaction: merchant debit == sum of employee credits") {
                val legs = legsSlot.captured
                legs.size shouldBe 3
                val debitLeg = legs.first { it.direction == LedgerDirection.DEBIT }
                debitLeg.accountId shouldBe "account_merchant"
                debitLeg.accountType shouldBe LedgerAccountType.WALLET
                debitLeg.amount shouldBe BigDecimal("270000")
                val creditLegs = legs.filter { it.direction == LedgerDirection.CREDIT }
                creditLegs.map { it.accountId }.toSet() shouldBe setOf("account_emp_1", "account_emp_2")
                creditLegs.sumOf { it.amount } shouldBe BigDecimal("270000")
            }

            Then("the result carries the real total, employee count, and per-employee payslip breakdown") {
                result["totalAmount"] shouldBe BigDecimal("270000")
                result["employeeCount"] shouldBe 2
                @Suppress("UNCHECKED_CAST")
                val payslips = result["payslips"] as List<Map<String, Any?>>
                payslips.size shouldBe 2
            }

            Then("a real Transaction row is saved per employee, fraud-evaluated before saving, batched in ONE saveAll") {
                val transactionsSlot = slot<List<Transaction>>()
                verify(exactly = 1) { transactionRepository.saveAll(capture(transactionsSlot)) }
                transactionsSlot.captured.size shouldBe 2
                verify(exactly = 0) { transactionRepository.save(any()) }
                verify(exactly = 2) { fraudRuleEngine.evaluate("owner_1", any(), any(), any()) }
            }

            // Real sibling-asymmetry fix (2026-09-13) -- this class's own doc comment
            // claims parity with P2pService.payRequest, which notifies both sides of
            // every transfer; this real salary payment notified neither before this fix.
            Then("each employee is notified their salary was received, and the owner gets one run-summary notification") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "emp_1" && it.type == "SALARY_RECEIVED" }) }
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "emp_2" && it.type == "SALARY_RECEIVED" }) }
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "owner_1" && it.type == "PAYROLL_RUN_COMPLETED" }) }
                verify(exactly = 1) { pushNotificationService.sendToUser("emp_1", "Salary received", any()) }
                verify(exactly = 1) { pushNotificationService.sendToUser("emp_2", "Salary received", any()) }
                verify(exactly = 1) { pushNotificationService.sendToUser("owner_1", "Payroll run completed", any()) }
            }
        }

        When("an employee on the roster has no account") {
            every { accountRepository.findByUserIdInAndType(listOf("emp_1", "emp_2"), AccountType.MAIN) } returns
                listOf(account("account_emp_1", "emp_1"))

            Then("the whole run fails before any ledger posting -- nobody gets partially paid") {
                try {
                    service.runPayroll("owner_1")
                    error("expected EmployeeNoAccountException")
                } catch (e: EmployeeNoAccountException) {
                    // expected
                }
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }
    }

    Given("a merchant with an empty payroll roster") {
        val merchantRepository = mockk<MerchantRepository>()
        val payrollEmployeeRepository = mockk<PayrollEmployeeRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
        every { accountRepository.findById("account_merchant") } returns Optional.of(account("account_merchant", "owner_1"))
        every { payrollEmployeeRepository.findByMerchantIdAndActiveTrue("merchant_1") } returns emptyList()
        val service = buildService(
            merchantRepository = merchantRepository, payrollEmployeeRepository = payrollEmployeeRepository,
            accountRepository = accountRepository, ledgerService = ledgerService,
        )

        When("payroll is run") {
            Then("it real-fails rather than posting a real, empty, no-op ledger transaction") {
                try {
                    service.runPayroll("owner_1")
                    error("expected EmptyPayrollRosterException")
                } catch (e: EmptyPayrollRosterException) {
                    // expected
                }
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }
    }

    Given("a merchant removing an employee from the roster") {
        val merchantRepository = mockk<MerchantRepository>()
        val payrollEmployeeRepository = mockk<PayrollEmployeeRepository>()
        every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
        val service = buildService(merchantRepository = merchantRepository, payrollEmployeeRepository = payrollEmployeeRepository)

        When("the roster entry belongs to this merchant") {
            val employee = PayrollEmployee(id = "payroll_emp_1", merchantId = "merchant_1", employeeUserId = "emp_1", employeeName = "Alice U", salaryAmount = BigDecimal("150000"))
            every { payrollEmployeeRepository.findById("payroll_emp_1") } returns Optional.of(employee)
            every { payrollEmployeeRepository.save(any()) } answers { firstArg() }

            val result = service.removeEmployee("owner_1", "payroll_emp_1")

            Then("it's deactivated, not deleted -- a real audit trail for past payroll runs stays intact") {
                result.active shouldBe false
            }
        }

        When("the roster entry belongs to a different merchant") {
            val otherMerchantsEmployee = PayrollEmployee(id = "payroll_emp_9", merchantId = "merchant_other", employeeUserId = "emp_9", employeeName = "Someone Else", salaryAmount = BigDecimal("50000"))
            every { payrollEmployeeRepository.findById("payroll_emp_9") } returns Optional.of(otherMerchantsEmployee)

            Then("it real-404s rather than leaking or mutating another merchant's roster") {
                try {
                    service.removeEmployee("owner_1", "payroll_emp_9")
                    error("expected PayrollRosterEntryNotFoundException")
                } catch (e: PayrollRosterEntryNotFoundException) {
                    // expected
                }
            }
        }
    }

    Given("payroll run history") {
        val merchantRepository = mockk<MerchantRepository>()
        val payrollRunRepository = mockk<PayrollRunRepository>()
        every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
        val runs = listOf(
            PayrollRun(id = "payroll_run_1", merchantId = "merchant_1", ledgerTransactionId = "ledgertxn_1", totalAmount = BigDecimal("270000"), employeeCount = 2),
        )
        every { payrollRunRepository.findByMerchantIdOrderByCreatedAtDesc("merchant_1") } returns runs
        val service = buildService(merchantRepository = merchantRepository, payrollRunRepository = payrollRunRepository)

        When("listed") {
            val result = service.getPayrollHistory("owner_1")

            Then("it reflects the real persisted runs for this merchant only") {
                result shouldBe runs
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
