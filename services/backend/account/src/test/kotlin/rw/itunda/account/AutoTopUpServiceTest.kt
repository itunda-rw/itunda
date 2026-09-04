package rw.itunda.account

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.LinkedAccount
import rw.itunda.core.domain.LinkedAccountStatus
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountAutoTopUpSetting
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.provider.ProviderConnector
import rw.itunda.core.provider.ProviderDeclinedException
import rw.itunda.core.repository.LinkedAccountRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.AccountAutoTopUpSettingRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Optional

/** First test coverage for the real Naver Pay Money 자동충전 (auto-charge) equivalent. */
class AutoTopUpServiceTest : BehaviorSpec({

    fun account(id: String, userId: String, balance: String) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal(balance), availableBalance = BigDecimal(balance),
    )

    fun linkedAccount(id: String, userId: String, status: LinkedAccountStatus = LinkedAccountStatus.LINKED) = LinkedAccount(
        id = id, userId = userId, provider = "MTN", externalAccountNumberMasked = "•••• 1234", status = status,
    )

    // Given("configuring a real auto top-up rule") -- moved to
    // AutoTopUpConfigureServiceTest.kt (2026-09-04, file-size extraction).

    Given("evaluating a real account whose balance has dropped below its real threshold") {
        val accountAutoTopUpSettingRepository = mockk<AccountAutoTopUpSettingRepository>()
        val accountRepository = mockk<AccountRepository>()
        val linkedAccountRepository = mockk<LinkedAccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val providerConnector = mockk<ProviderConnector>()
        val service = AutoTopUpService(accountAutoTopUpSettingRepository, accountRepository, linkedAccountRepository, ledgerService, transactionRepository, providerConnector)

        val lowAccount = account("account_1", "user_1", "1000")
        val setting = AccountAutoTopUpSetting(
            id = "auto_topup_1", userId = "user_1", accountId = "account_1", linkedAccountId = "linked_1",
            thresholdAmount = BigDecimal("2000"), topUpAmount = BigDecimal("10000"), dailyTriggerCap = 3,
        )

        When("the real provider pull succeeds") {
            every { accountRepository.findById("account_1") } returns Optional.of(lowAccount)
            every { accountAutoTopUpSettingRepository.findByAccountId("account_1") } returns setting
            every { linkedAccountRepository.findById("linked_1") } returns Optional.of(linkedAccount("linked_1", "user_1"))
            every { providerConnector.attempt(any(), any()) } returns Unit
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_1", emptyList())
            every { accountAutoTopUpSettingRepository.save(any()) } answers { firstArg() }

            val result = service.evaluateAndTopUp("user_1", "account_1")

            Then("it real-triggers, crediting the account and debiting rail_suspense for the exact real top-up amount") {
                result.triggered shouldBe true
                val legs = legsSlot.captured
                legs.first { it.accountId == "account_1" }.direction shouldBe rw.itunda.core.domain.LedgerDirection.CREDIT
                legs.first { it.accountId == "account_1" }.amount shouldBe BigDecimal("10000")
                legs.first { it.accountId == "rail_suspense" }.direction shouldBe rw.itunda.core.domain.LedgerDirection.DEBIT
            }
            Then("it real-records a Transaction of type DEPOSIT via the auto_topup channel") {
                verify(exactly = 1) { transactionRepository.save(match { it.type == rw.itunda.core.domain.TransactionType.DEPOSIT && it.channel == "auto_topup" && it.amount == BigDecimal("10000") }) }
            }
            Then("it real-increments triggersToday") {
                setting.triggersToday shouldBe 1
            }
            // Real bug found live (2026-08-02): evaluateAndTopUp reads this exact
            // entity, check-then-acts on triggersToday/enabled/threshold, pulls real
            // money over an external rail, THEN writes triggersToday back -- the same
            // check-then-act shape SupportTicket/Ikimina/Holding's own @Version fixes
            // already address. This asserts the mechanism the fix now relies on: the
            // SAME versioned setting instance that was read is the one actually passed
            // to save(), so a concurrent second evaluateAndTopUp call on a stale
            // version real-409s via the existing global
            // ObjectOptimisticLockingFailureException handler instead of silently
            // bypassing the real daily trigger cap.
            Then("the same versioned setting instance that was read is the one saved") {
                val savedSlot = slot<AccountAutoTopUpSetting>()
                verify(exactly = 1) { accountAutoTopUpSettingRepository.save(capture(savedSlot)) }
                savedSlot.captured shouldBe setting
                savedSlot.captured.version shouldBe setting.version
            }
        }

        When("the real provider declines the pull") {
            every { accountRepository.findById("account_1") } returns Optional.of(lowAccount)
            val freshSetting = AccountAutoTopUpSetting(
                id = "auto_topup_2", userId = "user_1", accountId = "account_1", linkedAccountId = "linked_1",
                thresholdAmount = BigDecimal("2000"), topUpAmount = BigDecimal("10000"), dailyTriggerCap = 3,
            )
            every { accountAutoTopUpSettingRepository.findByAccountId("account_1") } returns freshSetting
            every { linkedAccountRepository.findById("linked_1") } returns Optional.of(linkedAccount("linked_1", "user_1"))
            every { providerConnector.attempt(any(), any()) } throws ProviderDeclinedException("MTN Mobile Money declined")

            val result = service.evaluateAndTopUp("user_1", "account_1")

            Then("it real-reports not triggered and never touches the ledger, leaving the account balance untouched") {
                result.triggered shouldBe false
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }

        When("the account balance is already at or above the real threshold") {
            val healthyAccount = account("account_1", "user_1", "5000")
            every { accountRepository.findById("account_1") } returns Optional.of(healthyAccount)
            val freshSetting = AccountAutoTopUpSetting(
                id = "auto_topup_3", userId = "user_1", accountId = "account_1", linkedAccountId = "linked_1",
                thresholdAmount = BigDecimal("2000"), topUpAmount = BigDecimal("10000"), dailyTriggerCap = 3,
            )
            every { accountAutoTopUpSettingRepository.findByAccountId("account_1") } returns freshSetting

            val result = service.evaluateAndTopUp("user_1", "account_1")

            Then("it real-reports not triggered without ever calling the real provider") {
                result.triggered shouldBe false
                verify(exactly = 0) { providerConnector.attempt(any(), any()) }
            }
        }

        When("the real daily trigger cap has already been reached today") {
            every { accountRepository.findById("account_1") } returns Optional.of(lowAccount)
            val cappedSetting = AccountAutoTopUpSetting(
                id = "auto_topup_4", userId = "user_1", accountId = "account_1", linkedAccountId = "linked_1",
                thresholdAmount = BigDecimal("2000"), topUpAmount = BigDecimal("10000"), dailyTriggerCap = 2,
                triggersToday = 2, lastTriggerDate = LocalDate.now(),
            )
            every { accountAutoTopUpSettingRepository.findByAccountId("account_1") } returns cappedSetting

            val result = service.evaluateAndTopUp("user_1", "account_1")

            Then("it real-reports not triggered without ever calling the real provider") {
                result.triggered shouldBe false
                verify(exactly = 0) { providerConnector.attempt(any(), any()) }
            }
        }

        When("the real daily trigger cap was reached YESTERDAY, not today") {
            every { accountRepository.findById("account_1") } returns Optional.of(lowAccount)
            val staleSetting = AccountAutoTopUpSetting(
                id = "auto_topup_5", userId = "user_1", accountId = "account_1", linkedAccountId = "linked_1",
                thresholdAmount = BigDecimal("2000"), topUpAmount = BigDecimal("10000"), dailyTriggerCap = 2,
                triggersToday = 2, lastTriggerDate = LocalDate.now().minusDays(1),
            )
            every { accountAutoTopUpSettingRepository.findByAccountId("account_1") } returns staleSetting
            every { linkedAccountRepository.findById("linked_1") } returns Optional.of(linkedAccount("linked_1", "user_1"))
            every { providerConnector.attempt(any(), any()) } returns Unit
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_2", emptyList())
            every { accountAutoTopUpSettingRepository.save(any()) } answers { firstArg() }

            val result = service.evaluateAndTopUp("user_1", "account_1")

            Then("it real-resets the daily counter for the new real day and triggers") {
                result.triggered shouldBe true
                staleSetting.triggersToday shouldBe 1
            }
        }

        When("auto top-up is disabled for this account") {
            every { accountRepository.findById("account_1") } returns Optional.of(lowAccount)
            val disabledSetting = AccountAutoTopUpSetting(
                id = "auto_topup_6", userId = "user_1", accountId = "account_1", linkedAccountId = "linked_1",
                thresholdAmount = BigDecimal("2000"), topUpAmount = BigDecimal("10000"), enabled = false,
            )
            every { accountAutoTopUpSettingRepository.findByAccountId("account_1") } returns disabledSetting

            val result = service.evaluateAndTopUp("user_1", "account_1")

            Then("it real-reports not triggered without ever calling the real provider") {
                result.triggered shouldBe false
                verify(exactly = 0) { providerConnector.attempt(any(), any()) }
            }
        }
    }

    Given("a mix of real enabled and disabled auto top-up settings") {
        val accountAutoTopUpSettingRepository = mockk<AccountAutoTopUpSettingRepository>()
        val accountRepository = mockk<AccountRepository>()
        val linkedAccountRepository = mockk<LinkedAccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>()
        val providerConnector = mockk<ProviderConnector>()
        val service = AutoTopUpService(accountAutoTopUpSettingRepository, accountRepository, linkedAccountRepository, ledgerService, transactionRepository, providerConnector)

        When("AutoTopUpScheduler asks which real settings it should sweep") {
            val enabledOnly = listOf(
                AccountAutoTopUpSetting(id = "auto_topup_7", userId = "user_1", accountId = "account_1", linkedAccountId = "linked_1", thresholdAmount = BigDecimal("2000"), topUpAmount = BigDecimal("10000"), enabled = true),
            )
            every { accountAutoTopUpSettingRepository.findByEnabledTrue() } returns enabledOnly

            val result = service.getEnabledSettings()

            Then("it returns only the real enabled settings, matching the scheduler's own real automation gap this closes") {
                result shouldBe enabledOnly
            }
        }
    }

    // Real Naver Pay Money "결제 시 부족분 자동 충전" (2026-07-27) -- see
    // AutoTopUpService.topUpShortfall's own doc comment.
    Given("a real account with a real enabled auto top-up setting, hitting a real shortfall") {
        val accountAutoTopUpSettingRepository = mockk<AccountAutoTopUpSettingRepository>()
        val accountRepository = mockk<AccountRepository>()
        val linkedAccountRepository = mockk<LinkedAccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val providerConnector = mockk<ProviderConnector>(relaxed = true)
        val service = AutoTopUpService(accountAutoTopUpSettingRepository, accountRepository, linkedAccountRepository, ledgerService, transactionRepository, providerConnector)

        val setting = AccountAutoTopUpSetting(
            id = "auto_topup_1", userId = "user_1", accountId = "account_1", linkedAccountId = "linked_1",
            thresholdAmount = BigDecimal("2000"), topUpAmount = BigDecimal("10000"), enabled = true,
        )
        every { accountRepository.findById("account_1") } returns Optional.of(account("account_1", "user_1", "500"))
        every { accountAutoTopUpSettingRepository.findByAccountId("account_1") } returns setting
        every { linkedAccountRepository.findById("linked_1") } returns Optional.of(linkedAccount("linked_1", "user_1"))
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_shortfall_1", emptyList())

        When("a real 350 RWF shortfall is reported") {
            val result = service.topUpShortfall("user_1", "account_1", BigDecimal("350"))

            Then("it real-tops-up rounded UP to the nearest real 1,000 RWF unit, not the configured recurring topUpAmount") {
                result.triggered shouldBe true
                val legsSlot = slot<List<LedgerLeg>>()
                verify(exactly = 1) { ledgerService.postLedgerTransaction(any(), capture(legsSlot)) }
                legsSlot.captured.first { it.accountId == "account_1" }.amount shouldBe BigDecimal("1000")
            }

            Then("it never touches the recurring scheduler's own real daily trigger cadence") {
                setting.triggersToday shouldBe 0
                setting.lastTriggerDate shouldBe null
            }
        }
    }

    Given("a real account with auto top-up disabled, hitting a real shortfall") {
        val accountAutoTopUpSettingRepository = mockk<AccountAutoTopUpSettingRepository>()
        val accountRepository = mockk<AccountRepository>()
        val linkedAccountRepository = mockk<LinkedAccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val providerConnector = mockk<ProviderConnector>(relaxed = true)
        val service = AutoTopUpService(accountAutoTopUpSettingRepository, accountRepository, linkedAccountRepository, ledgerService, transactionRepository, providerConnector)

        val setting = AccountAutoTopUpSetting(
            id = "auto_topup_2", userId = "user_1", accountId = "account_1", linkedAccountId = "linked_1",
            thresholdAmount = BigDecimal("2000"), topUpAmount = BigDecimal("10000"), enabled = false,
        )
        every { accountRepository.findById("account_1") } returns Optional.of(account("account_1", "user_1", "500"))
        every { accountAutoTopUpSettingRepository.findByAccountId("account_1") } returns setting

        When("a real shortfall is reported") {
            val result = service.topUpShortfall("user_1", "account_1", BigDecimal("350"))

            Then("it honestly does not trigger, and the ledger is never touched") {
                result.triggered shouldBe false
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }
    }

    Given("a real account with no auto top-up setting configured at all") {
        val accountAutoTopUpSettingRepository = mockk<AccountAutoTopUpSettingRepository>()
        val accountRepository = mockk<AccountRepository>()
        val linkedAccountRepository = mockk<LinkedAccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val providerConnector = mockk<ProviderConnector>(relaxed = true)
        val service = AutoTopUpService(accountAutoTopUpSettingRepository, accountRepository, linkedAccountRepository, ledgerService, transactionRepository, providerConnector)

        every { accountRepository.findById("account_1") } returns Optional.of(account("account_1", "user_1", "500"))
        every { accountAutoTopUpSettingRepository.findByAccountId("account_1") } returns null

        When("a real shortfall is reported") {
            val result = service.topUpShortfall("user_1", "account_1", BigDecimal("350"))

            Then("it honestly does not trigger, matching the overwhelming common case") {
                result.triggered shouldBe false
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }
    }

    Given("a real account whose provider declines the real shortfall pull") {
        val accountAutoTopUpSettingRepository = mockk<AccountAutoTopUpSettingRepository>()
        val accountRepository = mockk<AccountRepository>()
        val linkedAccountRepository = mockk<LinkedAccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val providerConnector = mockk<ProviderConnector>()
        val service = AutoTopUpService(accountAutoTopUpSettingRepository, accountRepository, linkedAccountRepository, ledgerService, transactionRepository, providerConnector)

        val setting = AccountAutoTopUpSetting(
            id = "auto_topup_3", userId = "user_1", accountId = "account_1", linkedAccountId = "linked_1",
            thresholdAmount = BigDecimal("2000"), topUpAmount = BigDecimal("10000"), enabled = true,
        )
        every { accountRepository.findById("account_1") } returns Optional.of(account("account_1", "user_1", "500"))
        every { accountAutoTopUpSettingRepository.findByAccountId("account_1") } returns setting
        every { linkedAccountRepository.findById("linked_1") } returns Optional.of(linkedAccount("linked_1", "user_1"))
        every { providerConnector.attempt(any(), any()) } throws ProviderDeclinedException("Insufficient funds on linked account")

        When("a real shortfall is reported") {
            val result = service.topUpShortfall("user_1", "account_1", BigDecimal("350"))

            Then("the real account balance is left honestly untouched") {
                result.triggered shouldBe false
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }
    }

    // Real Toss Bank/Toss Pay separation (2026-08-21) -- topUpPayFromMain/
    // ensureSufficientPayBalance had zero direct test coverage until now (only
    // exercised indirectly through every consumer's own mocked-collaborator tests,
    // e.g. MerchantServiceTest). See docs/DESIGN_REFERENCES.md §257.
    Given("real itunda Pay money auto-funded from a real itunda Bank account") {
        val accountAutoTopUpSettingRepository = mockk<AccountAutoTopUpSettingRepository>(relaxed = true)
        val accountRepository = mockk<AccountRepository>()
        val linkedAccountRepository = mockk<LinkedAccountRepository>(relaxed = true)
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val providerConnector = mockk<ProviderConnector>(relaxed = true)
        val service = AutoTopUpService(accountAutoTopUpSettingRepository, accountRepository, linkedAccountRepository, ledgerService, transactionRepository, providerConnector)

        When("topUpPayFromMain runs for a real 3000 RWF shortfall and Bank covers it") {
            every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_main", "user_1", "50000")
            val legsSlot = slot<List<LedgerLeg>>()
            every { ledgerService.postLedgerTransaction(any(), capture(legsSlot)) } returns LedgerPostResult("ledgertxn_paytopup", emptyList())

            val result = service.topUpPayFromMain("user_1", "account_pay", BigDecimal("3000"))

            Then("it real-posts a balanced Bank-debit/Pay-credit transaction for exactly the shortfall") {
                result.triggered shouldBe true
                legsSlot.captured.first { it.accountId == "account_main" }.amount shouldBe BigDecimal("3000")
                legsSlot.captured.first { it.accountId == "account_pay" }.amount shouldBe BigDecimal("3000")
            }
        }

        When("topUpPayFromMain is attempted for a user with no real Bank account") {
            every { accountRepository.findByUserIdAndType("user_2", AccountType.MAIN) } returns null

            val result = service.topUpPayFromMain("user_2", "account_pay", BigDecimal("3000"))

            Then("it honestly does not trigger, and the ledger is never touched") {
                result.triggered shouldBe false
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }

        When("ensureSufficientPayBalance sees an account that already covers the required amount") {
            val sufficientAccount = account("account_pay3", "user_1", "10000").also { it.type = AccountType.PAY }

            val result = service.ensureSufficientPayBalance("user_1", sufficientAccount, BigDecimal("3000"))

            Then("it returns the same account unchanged, never touching the ledger") {
                result shouldBe sufficientAccount
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }

        When("ensureSufficientPayBalance sees a short account that Bank covers") {
            val shortAccount = account("account_pay4", "user_1", "1000").also { it.type = AccountType.PAY }
            val toppedUpAccount = account("account_pay4", "user_1", "10000").also { it.type = AccountType.PAY }
            every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_main2", "user_1", "50000")
            every { accountRepository.findById("account_pay4") } returns Optional.of(toppedUpAccount)
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_ensure", emptyList())

            val result = service.ensureSufficientPayBalance("user_1", shortAccount, BigDecimal("3000"))

            Then("it real-tops-up from Bank for exactly the real shortfall and returns the fresh, re-read account") {
                result shouldBe toppedUpAccount
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
