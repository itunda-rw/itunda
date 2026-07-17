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
import rw.itunda.core.domain.User
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.PayrollEmployeeRepository
import rw.itunda.core.repository.PayrollRunRepository
import rw.itunda.core.repository.PayslipRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.util.Optional

class PayrollServiceTest : BehaviorSpec({

    fun wallet(id: String, userId: String) = Wallet(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test wallet",
        type = WalletType.MAIN, balance = BigDecimal("500000"), availableBalance = BigDecimal("500000"),
    )

    fun user(id: String, phone: String, first: String, last: String) = User(
        id = id, phoneNumber = phone, firstName = first, lastName = last, passwordHash = "hash",
    )

    val merchant = Merchant(
        id = "merchant_1", ownerUserId = "owner_1", walletId = "wallet_merchant",
        businessName = "Kigali Coffee", status = MerchantStatus.ACTIVE,
    )

    fun buildService(
        merchantRepository: MerchantRepository = mockk(),
        payrollEmployeeRepository: PayrollEmployeeRepository = mockk(),
        payrollRunRepository: PayrollRunRepository = mockk(),
        payslipRepository: PayslipRepository = mockk(relaxed = true),
        userRepository: UserRepository = mockk(),
        walletRepository: WalletRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        transactionRepository: TransactionRepository = mockk(relaxed = true),
        fraudRuleEngine: FraudRuleEngine = mockk(relaxed = true),
    ) = PayrollService(
        merchantRepository, payrollEmployeeRepository, payrollRunRepository, payslipRepository,
        userRepository, walletRepository, ledgerService, transactionRepository, fraudRuleEngine,
    )

    Given("a registered merchant adding an employee to the payroll roster") {
        val merchantRepository = mockk<MerchantRepository>()
        val payrollEmployeeRepository = mockk<PayrollEmployeeRepository>()
        val userRepository = mockk<UserRepository>()
        val walletRepository = mockk<WalletRepository>()
        every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
        every { payrollEmployeeRepository.save(any()) } answers { firstArg() }
        val service = buildService(
            merchantRepository = merchantRepository, payrollEmployeeRepository = payrollEmployeeRepository,
            userRepository = userRepository, walletRepository = walletRepository,
        )

        When("the phone number resolves to a real, distinct itunda user with a wallet") {
            every { userRepository.findByPhoneNumber("+250788111222") } returns user("emp_1", "+250788111222", "Alice", "U")
            every { payrollEmployeeRepository.findByMerchantIdAndEmployeeUserId("merchant_1", "emp_1") } returns null
            every { walletRepository.findByUserIdAndType("emp_1", WalletType.MAIN) } returns wallet("wallet_emp_1", "emp_1")

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
            every { walletRepository.findByUserIdAndType("emp_1", WalletType.MAIN) } returns wallet("wallet_emp_1", "emp_1")
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
            every { walletRepository.findByUserIdAndType("emp_1", WalletType.MAIN) } returns wallet("wallet_emp_1", "emp_1")

            val result = service.addEmployee("owner_1", "+250788111222", BigDecimal("175000"))

            Then("the existing row is reactivated with the new salary, not blocked or duplicated") {
                result.id shouldBe "payroll_emp_1"
                result.active shouldBe true
                result.salaryAmount shouldBe BigDecimal("175000")
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
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val fraudRuleEngine = mockk<FraudRuleEngine>(relaxed = true)

        every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
        val merchantWallet = wallet("wallet_merchant", "owner_1")
        every { walletRepository.findById("wallet_merchant") } returns Optional.of(merchantWallet)

        val emp1 = PayrollEmployee(id = "payroll_emp_1", merchantId = "merchant_1", employeeUserId = "emp_1", employeeName = "Alice U", salaryAmount = BigDecimal("150000"))
        val emp2 = PayrollEmployee(id = "payroll_emp_2", merchantId = "merchant_1", employeeUserId = "emp_2", employeeName = "Bob T", salaryAmount = BigDecimal("120000"))
        every { payrollEmployeeRepository.findByMerchantIdAndActiveTrue("merchant_1") } returns listOf(emp1, emp2)
        // Batch-fetched in one call (findByUserIdInAndType), not one findByUserIdAndType
        // call per roster row -- see PayrollService.runPayroll's own comment on the N+1
        // this replaced.
        every { walletRepository.findByUserIdInAndType(listOf("emp_1", "emp_2"), WalletType.MAIN) } returns
            listOf(wallet("wallet_emp_1", "emp_1"), wallet("wallet_emp_2", "emp_2"))

        val service = buildService(
            merchantRepository = merchantRepository, payrollEmployeeRepository = payrollEmployeeRepository,
            payrollRunRepository = payrollRunRepository, payslipRepository = payslipRepository,
            walletRepository = walletRepository, ledgerService = ledgerService,
            transactionRepository = transactionRepository, fraudRuleEngine = fraudRuleEngine,
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
                debitLeg.accountId shouldBe "wallet_merchant"
                debitLeg.accountType shouldBe LedgerAccountType.WALLET
                debitLeg.amount shouldBe BigDecimal("270000")
                val creditLegs = legs.filter { it.direction == LedgerDirection.CREDIT }
                creditLegs.map { it.accountId }.toSet() shouldBe setOf("wallet_emp_1", "wallet_emp_2")
                creditLegs.sumOf { it.amount } shouldBe BigDecimal("270000")
            }

            Then("the result carries the real total, employee count, and per-employee payslip breakdown") {
                result["totalAmount"] shouldBe BigDecimal("270000")
                result["employeeCount"] shouldBe 2
                @Suppress("UNCHECKED_CAST")
                val payslips = result["payslips"] as List<Map<String, Any?>>
                payslips.size shouldBe 2
            }

            Then("a real Transaction row is saved per employee, fraud-evaluated before saving") {
                verify(exactly = 2) { transactionRepository.save(any()) }
                verify(exactly = 2) { fraudRuleEngine.evaluate("owner_1", any(), any(), any()) }
            }
        }

        When("an employee on the roster has no wallet") {
            every { walletRepository.findByUserIdInAndType(listOf("emp_1", "emp_2"), WalletType.MAIN) } returns
                listOf(wallet("wallet_emp_1", "emp_1"))

            Then("the whole run fails before any ledger posting -- nobody gets partially paid") {
                try {
                    service.runPayroll("owner_1")
                    error("expected EmployeeNoWalletException")
                } catch (e: EmployeeNoWalletException) {
                    // expected
                }
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }
    }

    Given("a merchant with an empty payroll roster") {
        val merchantRepository = mockk<MerchantRepository>()
        val payrollEmployeeRepository = mockk<PayrollEmployeeRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
        every { walletRepository.findById("wallet_merchant") } returns Optional.of(wallet("wallet_merchant", "owner_1"))
        every { payrollEmployeeRepository.findByMerchantIdAndActiveTrue("merchant_1") } returns emptyList()
        val service = buildService(
            merchantRepository = merchantRepository, payrollEmployeeRepository = payrollEmployeeRepository,
            walletRepository = walletRepository, ledgerService = ledgerService,
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
