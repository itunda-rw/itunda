package rw.itunda.account

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.LedgerEntry
import rw.itunda.core.domain.SpendingBudget
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.SpendingBudgetRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.YearMonth

/**
 * Split out of AccountServiceTest.kt (2026-08-21), following SpendingInsightService.kt's
 * own extraction out of AccountService.kt -- see that class's own doc comment.
 */
class SpendingInsightServiceTest : BehaviorSpec({

    fun account(id: String, userId: String, balance: String) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal(balance), availableBalance = BigDecimal(balance),
    )

    Given("a user whose ledger has debits from real modules that share rail_suspense") {
        val accountRepository = mockk<AccountRepository>()
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val spendingBudgetRepository = mockk<SpendingBudgetRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = SpendingInsightService(accountRepository, ledgerEntryRepository, spendingBudgetRepository, notificationRepository, pushNotificationService)

        fun entry(id: String, txnId: String, accountId: String, accountType: LedgerAccountType, direction: LedgerDirection, amount: String, memo: String = "test") = LedgerEntry(
            id = id, transactionId = txnId, accountId = accountId, accountType = accountType, direction = direction,
            amount = BigDecimal(amount), currency = "RWF", balanceAfter = BigDecimal.ZERO, memo = memo,
        )

        every { accountRepository.findByUserId("user_9") } returns listOf(account("account_9", "user_9", "0"))

        // Live-discovered while testing this against a real backend: BillsService.payBill,
        // BillsService.buyAirtime, and AccountService.confirmTransfer all post to the same
        // "rail_suspense" clearing account -- accountType alone can't tell them apart, so this
        // fixture matches the real memo prefixes each service actually writes.
        val billDebit = entry("e1", "txn_bill", "account_9", LedgerAccountType.WALLET, LedgerDirection.DEBIT, "500", "Bill payment bill_2")
        val billCounterpart = entry("e1b", "txn_bill", "rail_suspense", LedgerAccountType.RAIL_SUSPENSE, LedgerDirection.CREDIT, "500", "Biller settlement bill_2")
        val airtimeDebit = entry("e6", "txn_airtime", "account_9", LedgerAccountType.WALLET, LedgerDirection.DEBIT, "200", "Airtime 0788000000")
        val airtimeCounterpart = entry("e6b", "txn_airtime", "rail_suspense", LedgerAccountType.RAIL_SUSPENSE, LedgerDirection.CREDIT, "200", "Airtime settlement 0788000000")
        val insuranceDebit = entry("e2", "txn_ins", "account_9", LedgerAccountType.WALLET, LedgerDirection.DEBIT, "1000")
        val insuranceCounterpart = entry("e3", "txn_ins", "insurance_premium_revenue", LedgerAccountType.INSURANCE_PREMIUM_REVENUE, LedgerDirection.CREDIT, "1000")
        val transferDebit = entry("e4", "txn_transfer", "account_9", LedgerAccountType.WALLET, LedgerDirection.DEBIT, "2000", "Transfer to 0788999999")
        val transferCounterpart = entry("e5", "txn_transfer", "rail_suspense", LedgerAccountType.RAIL_SUSPENSE, LedgerDirection.CREDIT, "1980")
        // A genuine "no distinguishing counterpart" case -- an internal account-to-account debit
        // with only another ACCOUNT-type sibling.
        val internalDebit = entry("e7", "txn_internal", "account_9", LedgerAccountType.WALLET, LedgerDirection.DEBIT, "300")
        val internalCounterpart = entry("e7b", "txn_internal", "account_savings_9", LedgerAccountType.WALLET, LedgerDirection.CREDIT, "300")

        every { ledgerEntryRepository.findByAccountIdInOrderByCreatedAtDesc(setOf("account_9")) } returns
            listOf(transferDebit, insuranceDebit, billDebit, airtimeDebit, internalDebit)
        // Real N+1 fix (2026-07-19 sweep): one batched findByTransactionIdIn stub instead
        // of one findByTransactionId stub per transaction id, matching the real service's
        // own fix -- order of the input list doesn't matter here since the service groups
        // the result by transactionId itself.
        every { ledgerEntryRepository.findByTransactionIdIn(match { it.toSet() == setOf("txn_transfer", "txn_ins", "txn_bill", "txn_airtime", "txn_internal") }) } returns
            listOf(billDebit, billCounterpart, airtimeDebit, airtimeCounterpart, insuranceDebit, insuranceCounterpart, transferDebit, transferCounterpart, internalDebit, internalCounterpart)

        When("computing the spending insight") {
            val result = service.getSpendingInsight("user_9")

            Then("bills, airtime, and transfers are correctly split despite sharing rail_suspense") {
                result.totalSpent shouldBe BigDecimal("4000")
                val byName = result.categories.associate { it.name to it.amount }
                byName["Transfers"] shouldBe BigDecimal("2000")
                byName["Bills"] shouldBe BigDecimal("500")
                byName["Airtime"] shouldBe BigDecimal("200")
                byName["Insurance"] shouldBe BigDecimal("1000")
                byName["Other"] shouldBe BigDecimal("300")
            }
            Then("the largest category comes first") {
                result.categories.first().name shouldBe "Transfers"
            }
        }

        When("a 1000 RWF Bills budget is checked against 500 actually spent on Bills") {
            val budget = SpendingBudget(id = "budget_1", userId = "user_9", category = "Bills", monthlyLimit = BigDecimal("1000"), month = YearMonth.now().toString())
            every { spendingBudgetRepository.findByUserIdAndMonth("user_9", any()) } returns listOf(budget)
            every { spendingBudgetRepository.save(any()) } answers { firstArg() }

            val budgets = service.getBudgets("user_9")

            Then("it reports the real spent amount, 50% used, and UNDER status") {
                budgets.size shouldBe 1
                budgets[0].spent shouldBe BigDecimal("500")
                budgets[0].percentUsed shouldBe 50
                budgets[0].status shouldBe rw.itunda.account.BudgetStatus.UNDER
            }
            Then("no notification is written -- under threshold") {
                verify(exactly = 0) { notificationRepository.save(any()) }
            }
        }

        When("an overall 3500 RWF budget is checked against the real 4000 RWF actually spent") {
            val budget = SpendingBudget(id = "budget_2", userId = "user_9", category = null, monthlyLimit = BigDecimal("3500"), month = YearMonth.now().toString())
            every { spendingBudgetRepository.findByUserIdAndMonth("user_9", any()) } returns listOf(budget)
            every { spendingBudgetRepository.save(any()) } answers { firstArg() }
            every { notificationRepository.save(any()) } answers { firstArg() }

            val budgets = service.getBudgets("user_9")

            Then("it correctly reports OVER status against the real total spend") {
                budgets[0].spent shouldBe BigDecimal("4000")
                budgets[0].status shouldBe rw.itunda.account.BudgetStatus.OVER
            }
            Then("it writes one real over-budget notification, not a duplicate") {
                verify(exactly = 1) { notificationRepository.save(any()) }
            }

            // Real fix (2026-09-13, push-before-commit ordering sweep): notifiedOver must
            // be saved BEFORE the push fires -- otherwise a rollback after the push leaves
            // the flag unset and the very next GET /budgets poll resends it.
            Then("the notifiedOver flag is saved before the push is sent") {
                verifyOrder {
                    spendingBudgetRepository.save(budget)
                    pushNotificationService.sendToUser("user_9", any(), any())
                }
            }
        }

        When("an overall 4500 RWF budget is checked against the real 4000 RWF actually spent -- 88%, NEAR but not OVER") {
            val budget = SpendingBudget(id = "budget_3", userId = "user_9", category = null, monthlyLimit = BigDecimal("4500"), month = YearMonth.now().toString())
            every { spendingBudgetRepository.findByUserIdAndMonth("user_9", any()) } returns listOf(budget)
            every { spendingBudgetRepository.save(any()) } answers { firstArg() }
            every { notificationRepository.save(any()) } answers { firstArg() }

            val budgets = service.getBudgets("user_9")

            Then("it correctly reports NEAR status") {
                budgets[0].status shouldBe rw.itunda.account.BudgetStatus.NEAR
            }

            // Real fix (2026-09-13, push-before-commit ordering sweep) -- same as the
            // OVER branch's own fix above.
            Then("the notifiedNear flag is saved before the push is sent") {
                verifyOrder {
                    spendingBudgetRepository.save(budget)
                    pushNotificationService.sendToUser("user_9", any(), any())
                }
            }
        }
    }

    // Real N+1 fix (2026-09-13, structural N+1 re-sweep): getSpendingInsight/
    // getMonthlySpendingReport used to call ledgerEntryRepository.findByAccountId...
    // once PER account instead of batching -- a real user with a GROUP/Ikimina account
    // and a MAIN account, not just one.
    Given("a real user with two real accounts (MAIN and GROUP)") {
        val accountRepository = mockk<AccountRepository>()
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val spendingBudgetRepository = mockk<SpendingBudgetRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = SpendingInsightService(accountRepository, ledgerEntryRepository, spendingBudgetRepository, notificationRepository, pushNotificationService)

        fun entry(id: String, txnId: String, accountId: String, amount: String) = LedgerEntry(
            id = id, transactionId = txnId, accountId = accountId, accountType = LedgerAccountType.WALLET,
            direction = LedgerDirection.DEBIT, amount = BigDecimal(amount), currency = "RWF", balanceAfter = BigDecimal.ZERO, memo = "test",
        )

        val mainAccount = account("account_main", "user_multi", "0")
        val groupAccount = Account(
            id = "account_group", userId = "user_multi", accountNumber = "ACC-account_group", accountName = "Group account",
            type = AccountType.GROUP, balance = BigDecimal.ZERO, availableBalance = BigDecimal.ZERO,
        )
        every { accountRepository.findByUserId("user_multi") } returns listOf(mainAccount, groupAccount)
        val mainDebit = entry("entry_main", "txn_main", "account_main", "1000")
        val groupDebit = entry("entry_group", "txn_group", "account_group", "500")
        every {
            ledgerEntryRepository.findByAccountIdInOrderByCreatedAtDesc(match { it.toSet() == setOf("account_main", "account_group") })
        } returns listOf(mainDebit, groupDebit)
        every { ledgerEntryRepository.findByTransactionIdIn(match { it.toSet() == setOf("txn_main", "txn_group") }) } returns listOf(mainDebit, groupDebit)

        When("computing the spending insight") {
            val result = service.getSpendingInsight("user_multi")

            Then("both accounts' debits are resolved in a single batched call and both amounts are counted") {
                result.totalSpent shouldBe BigDecimal("1500")
                verify(exactly = 1) { ledgerEntryRepository.findByAccountIdInOrderByCreatedAtDesc(any()) }
            }
        }
    }

    Given("a real fee-charging transfer, which posts THREE legs, not two") {
        val accountRepository = mockk<AccountRepository>()
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val spendingBudgetRepository = mockk<SpendingBudgetRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = SpendingInsightService(accountRepository, ledgerEntryRepository, spendingBudgetRepository, notificationRepository, pushNotificationService)

        fun entry(id: String, txnId: String, accountId: String, accountType: LedgerAccountType, direction: LedgerDirection, amount: String, memo: String = "test") = LedgerEntry(
            id = id, transactionId = txnId, accountId = accountId, accountType = accountType, direction = direction,
            amount = BigDecimal(amount), currency = "RWF", balanceAfter = BigDecimal.ZERO, memo = memo,
        )

        // Real bug found live during this session's own N+1-fix verification: a real
        // fee-charging transfer posts a ACCOUNT debit, a RAIL_SUSPENSE credit, AND a
        // FEE_REVENUE credit in the same transaction -- deliberately using ids where the
        // fee leg sorts first, reproducing the exact real-world ordering that surfaced
        // this bug (MySQL returns rows in no guaranteed order absent an ORDER BY).
        val feeLeg = entry("entry_1_fee", "txn_fee_transfer", "fee_revenue", LedgerAccountType.FEE_REVENUE, LedgerDirection.CREDIT, "50", "Transfer fee")
        val accountDebit = entry("entry_2_account", "txn_fee_transfer", "account_9", LedgerAccountType.WALLET, LedgerDirection.DEBIT, "5050", "Transfer to +250788555999")
        val railLeg = entry("entry_3_rail", "txn_fee_transfer", "rail_suspense", LedgerAccountType.RAIL_SUSPENSE, LedgerDirection.CREDIT, "5000", "Rail settlement for +250788555999")

        every { accountRepository.findByUserId("user_fee") } returns listOf(account("account_9", "user_fee", "0"))
        every { ledgerEntryRepository.findByAccountIdInOrderByCreatedAtDesc(setOf("account_9")) } returns listOf(accountDebit)
        every { ledgerEntryRepository.findByTransactionIdIn(listOf("txn_fee_transfer")) } returns listOf(feeLeg, accountDebit, railLeg)

        When("computing the spending insight") {
            val result = service.getSpendingInsight("user_fee")

            Then("it's categorized as Transfers, not Fees -- FEE_REVENUE is deprioritized when a more meaningful sibling exists") {
                val byName = result.categories.associate { it.name to it.amount }
                byName["Transfers"] shouldBe BigDecimal("5050")
                byName["Fees"] shouldBe null
            }
        }
    }

    // Real budget-threshold push (2026-07-28) -- see maybeNotifyBudgetThreshold's own
    // doc comment.
    Given("a real overall budget already past its real monthly limit, never yet notified") {
        val accountRepository = mockk<AccountRepository>()
        val ledgerEntryRepository = mockk<LedgerEntryRepository>()
        val spendingBudgetRepository = mockk<SpendingBudgetRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = SpendingInsightService(accountRepository, ledgerEntryRepository, spendingBudgetRepository, notificationRepository, pushNotificationService)

        val month = YearMonth.now().toString()
        val budget = SpendingBudget(id = "budget_1", userId = "user_1", category = null, monthlyLimit = BigDecimal("1000"), month = month)
        val debit = LedgerEntry(
            id = "entry_1", transactionId = "txn_1", accountId = "account_1", accountType = LedgerAccountType.WALLET,
            direction = LedgerDirection.DEBIT, amount = BigDecimal("1200"), currency = "RWF", balanceAfter = BigDecimal.ZERO, memo = "test",
        )
        every { spendingBudgetRepository.findByUserIdAndMonth("user_1", month) } returns listOf(budget)
        every { accountRepository.findByUserId("user_1") } returns listOf(account("account_1", "user_1", "0"))
        every { ledgerEntryRepository.findByAccountIdInOrderByCreatedAtDesc(setOf("account_1")) } returns listOf(debit)
        every { ledgerEntryRepository.findByTransactionIdIn(listOf("txn_1")) } returns emptyList()
        every { notificationRepository.save(any()) } answers { firstArg() }
        every { spendingBudgetRepository.save(any()) } answers { firstArg() }

        service.getBudgets("user_1")

        Then("it real-saves an in-app BUDGET_OVER notification and a real push, both exactly once") {
            verify(exactly = 1) { notificationRepository.save(match { it.userId == "user_1" && it.type == "BUDGET_OVER" }) }
            verify(exactly = 1) { pushNotificationService.sendToUser("user_1", "Budget exceeded", any()) }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
