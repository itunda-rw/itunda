package rw.itunda.account

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.LinkedAccount
import rw.itunda.core.domain.LinkedAccountStatus
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountAutoTopUpSetting
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.provider.ProviderConnector
import rw.itunda.core.repository.LinkedAccountRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.AccountAutoTopUpSettingRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.util.Optional

/** AutoTopUpService.configure test coverage -- extracted from AutoTopUpServiceTest.kt
 * (2026-09-04) the moment adding a real reconfigure-path regression test pushed that
 * file past 500 lines for the first time. Configuration (create/update a rule) and
 * trigger-evaluation (does a real shortfall fire a real pull) are genuinely different
 * concerns sharing one service class -- this file owns the former. */
class AutoTopUpConfigureServiceTest : BehaviorSpec({

    fun account(id: String, userId: String, balance: String) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal(balance), availableBalance = BigDecimal(balance),
    )

    fun linkedAccount(id: String, userId: String, status: LinkedAccountStatus = LinkedAccountStatus.LINKED) = LinkedAccount(
        id = id, userId = userId, provider = "MTN", externalAccountNumberMasked = "•••• 1234", status = status,
    )

    Given("configuring a real auto top-up rule") {
        val accountAutoTopUpSettingRepository = mockk<AccountAutoTopUpSettingRepository>()
        val accountRepository = mockk<AccountRepository>()
        val linkedAccountRepository = mockk<LinkedAccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val providerConnector = mockk<ProviderConnector>()
        val service = AutoTopUpService(accountAutoTopUpSettingRepository, accountRepository, linkedAccountRepository, ledgerService, transactionRepository, providerConnector)

        When("configuring for the first time with a real LINKED account") {
            every { accountRepository.findById("account_1") } returns Optional.of(account("account_1", "user_1", "5000"))
            every { linkedAccountRepository.findById("linked_1") } returns Optional.of(linkedAccount("linked_1", "user_1"))
            every { accountAutoTopUpSettingRepository.findByAccountId("account_1") } returns null
            every { accountAutoTopUpSettingRepository.save(any()) } answers { firstArg() }

            val result = service.configure("user_1", "account_1", "linked_1", BigDecimal("2000"), BigDecimal("10000"), 3, true)

            Then("it real-creates a new setting with the exact real threshold/top-up amounts") {
                result.accountId shouldBe "account_1"
                result.thresholdAmount shouldBe BigDecimal("2000")
                result.topUpAmount shouldBe BigDecimal("10000")
                result.enabled shouldBe true
            }
        }

        // Real regression test (2026-09-04): this is a genuine upsert -- see
        // configure's own `if (existing != null)` branch -- but only the CREATE path
        // had test coverage before this. This is exactly the code path a real,
        // separately-found client bug hit (Android/iOS omitted dailyTriggerCap on
        // every save, silently resetting an existing config's cap to 3 --
        // feedback_put_full_replace_vs_patch_partial): the SERVICE'S own update logic
        // was always correct (it faithfully applies whatever dailyTriggerCap it's
        // given), but that correctness had never actually been asserted here.
        When("reconfiguring an account that already has a real setting") {
            // Every field below is deliberately given a DIFFERENT before/after value --
            // an earlier version of this test reused dailyTriggerCap=5 on both sides,
            // which passed even with the real update line commented out (caught by
            // deliberately breaking it before trusting this test, per this repo's own
            // "verify the check actually fires" discipline). A tautological before==after
            // assertion can't distinguish "correctly updated" from "silently untouched."
            val existing = AccountAutoTopUpSetting(
                id = "auto_topup_1", userId = "user_1", accountId = "account_1", linkedAccountId = "linked_1",
                thresholdAmount = BigDecimal("2000"), topUpAmount = BigDecimal("10000"), dailyTriggerCap = 5, enabled = true,
            )
            every { accountRepository.findById("account_1") } returns Optional.of(account("account_1", "user_1", "5000"))
            every { linkedAccountRepository.findById("linked_1") } returns Optional.of(linkedAccount("linked_1", "user_1"))
            every { accountAutoTopUpSettingRepository.findByAccountId("account_1") } returns existing
            every { accountAutoTopUpSettingRepository.save(any()) } answers { firstArg() }

            val result = service.configure("user_1", "account_1", "linked_1", BigDecimal("3000"), BigDecimal("15000"), 8, false)

            Then("it real-updates the SAME setting row in place, not a new one, applying every field it was given") {
                result.id shouldBe "auto_topup_1"
                result.thresholdAmount shouldBe BigDecimal("3000")
                result.topUpAmount shouldBe BigDecimal("15000")
                result.dailyTriggerCap shouldBe 8
                result.enabled shouldBe false
            }
        }

        // Real IDOR fix (2026-08-02): this used to throw AccountNotOwnedException (403)
        // -- and unlike a POST-body accountId, /api/v1/account/{accountId}/auto-topup
        // takes accountId as a real URL path variable, directly probeable/enumerable.
        // Now 404, never revealing that account_2 is a real account id.
        When("configuring against a account that isn't the caller's own") {
            every { accountRepository.findById("account_2") } returns Optional.of(account("account_2", "owner_1", "5000"))

            Then("it throws AccountNotFoundException before touching the linked account") {
                try {
                    service.configure("attacker", "account_2", "linked_1", BigDecimal("2000"), BigDecimal("10000"), 3, true)
                    error("expected AccountNotFoundException")
                } catch (e: AccountNotFoundException) {
                    verify(exactly = 0) { linkedAccountRepository.findById(any()) }
                }
            }
        }

        // Real IDOR fix (2026-08-02): this used to throw
        // AutoTopUpLinkedAccountNotOwnedException (403), confirming to the caller that
        // "linked_owned_by_other" is a real linked-account id they just don't own. Now
        // the same AutoTopUpLinkedAccountNotFoundException (404) as a genuinely bogus
        // id, matching the fix already applied to requireOwnedAccount above.
        When("configuring against a linked account owned by someone else") {
            every { accountRepository.findById("account_1") } returns Optional.of(account("account_1", "user_1", "5000"))
            every { linkedAccountRepository.findById("linked_owned_by_other") } returns Optional.of(linkedAccount("linked_owned_by_other", "someone_else"))

            Then("it throws AutoTopUpLinkedAccountNotFoundException, never revealing the linked account exists") {
                try {
                    service.configure("user_1", "account_1", "linked_owned_by_other", BigDecimal("2000"), BigDecimal("10000"), 3, true)
                    error("expected AutoTopUpLinkedAccountNotFoundException")
                } catch (e: AutoTopUpLinkedAccountNotFoundException) {
                    // expected
                }
            }
        }

        When("configuring against a linked account that's no longer LINKED") {
            every { accountRepository.findById("account_1") } returns Optional.of(account("account_1", "user_1", "5000"))
            every { linkedAccountRepository.findById("linked_unlinked") } returns Optional.of(linkedAccount("linked_unlinked", "user_1", LinkedAccountStatus.UNLINKED))

            Then("it throws AutoTopUpLinkedAccountNotLinkedException") {
                try {
                    service.configure("user_1", "account_1", "linked_unlinked", BigDecimal("2000"), BigDecimal("10000"), 3, true)
                    error("expected AutoTopUpLinkedAccountNotLinkedException")
                } catch (e: AutoTopUpLinkedAccountNotLinkedException) {
                    // expected
                }
            }
        }

        When("configuring with a zero top-up amount") {
            every { accountRepository.findById("account_1") } returns Optional.of(account("account_1", "user_1", "5000"))

            Then("it throws AutoTopUpInvalidAmountException before touching the linked account") {
                try {
                    service.configure("user_1", "account_1", "linked_1", BigDecimal("2000"), BigDecimal.ZERO, 3, true)
                    error("expected AutoTopUpInvalidAmountException")
                } catch (e: AutoTopUpInvalidAmountException) {
                    verify(exactly = 0) { linkedAccountRepository.findById(any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
