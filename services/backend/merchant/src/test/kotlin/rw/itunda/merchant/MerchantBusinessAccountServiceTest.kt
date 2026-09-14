package rw.itunda.merchant

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.account.AccountNumberGenerator
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.MerchantRepository
import java.math.BigDecimal
import java.util.Optional

/**
 * Real zero-test-coverage gap found live (2026-09-14, test-coverage sweep): this
 * whole class -- including moveToBusiness/moveToPersonal, the actual real
 * account-to-account money movement MerchantBusinessAccountController's live
 * routes call -- had no test file at all.
 */
class MerchantBusinessAccountServiceTest : BehaviorSpec({

    fun account(id: String, userId: String, type: AccountType) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = type, balance = BigDecimal("100000"), availableBalance = BigDecimal("100000"),
    )

    fun newService(
        merchantRepository: MerchantRepository = mockk(),
        accountRepository: AccountRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        ledgerEntryRepository: LedgerEntryRepository = mockk(relaxed = true),
        accountNumberGenerator: AccountNumberGenerator = mockk<AccountNumberGenerator>(relaxed = true),
    ) = MerchantBusinessAccountService(merchantRepository, accountRepository, ledgerService, ledgerEntryRepository, accountNumberGenerator)

    Given("a real registered merchant with no business account yet") {
        val merchantRepository = mockk<MerchantRepository>()
        val accountRepository = mockk<AccountRepository>()
        val service = newService(merchantRepository = merchantRepository, accountRepository = accountRepository)

        val merchant = Merchant(id = "merchant_1", ownerUserId = "owner_1", accountId = "account_personal", businessName = "Kigali Coffee", status = MerchantStatus.ACTIVE)
        every { merchantRepository.findByOwnerUserId("owner_1") } returns merchant
        every { accountRepository.findByUserIdAndType("owner_1", AccountType.BUSINESS) } returns null
        val savedSlot = slot<Account>()
        every { accountRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("they open a real business account") {
            val result = service.openBusinessAccount("owner_1")

            Then("a real BUSINESS-type account is created with a real zero starting balance") {
                result.type shouldBe AccountType.BUSINESS
                result.balance shouldBe BigDecimal.ZERO
                savedSlot.captured.userId shouldBe "owner_1"
            }
        }
    }

    Given("a non-merchant trying to open a business account") {
        val merchantRepository = mockk<MerchantRepository>()
        val service = newService(merchantRepository = merchantRepository)
        every { merchantRepository.findByOwnerUserId("user_2") } returns null

        When("they try") {
            Then("it throws MerchantNotFoundException before ever touching the account table") {
                try {
                    service.openBusinessAccount("user_2")
                    error("expected MerchantNotFoundException")
                } catch (e: MerchantNotFoundException) {
                    // expected
                }
            }
        }
    }

    Given("a merchant who already has a real business account") {
        val merchantRepository = mockk<MerchantRepository>()
        val accountRepository = mockk<AccountRepository>()
        val service = newService(merchantRepository = merchantRepository, accountRepository = accountRepository)
        val merchant = Merchant(id = "merchant_3", ownerUserId = "owner_3", accountId = "account_personal_3", businessName = "Kigali Coffee 3", status = MerchantStatus.ACTIVE)
        every { merchantRepository.findByOwnerUserId("owner_3") } returns merchant
        every { accountRepository.findByUserIdAndType("owner_3", AccountType.BUSINESS) } returns account("account_biz_3", "owner_3", AccountType.BUSINESS)

        When("they try to open a second one") {
            Then("it throws BusinessAccountAlreadyExistsException") {
                try {
                    service.openBusinessAccount("owner_3")
                    error("expected BusinessAccountAlreadyExistsException")
                } catch (e: BusinessAccountAlreadyExistsException) {
                    // expected
                }
            }
        }
    }

    Given("a real merchant with both a personal and a real business account") {
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(accountRepository = accountRepository, ledgerService = ledgerService)

        val businessAccount = account("account_biz_4", "owner_4", AccountType.BUSINESS)
        val personalAccount = account("account_personal_4", "owner_4", AccountType.MAIN)
        every { accountRepository.findByUserIdAndType("owner_4", AccountType.BUSINESS) } returns businessAccount
        every { accountRepository.findByUserIdAndType("owner_4", AccountType.MAIN) } returns personalAccount
        every { accountRepository.findById("account_biz_4") } returns Optional.of(businessAccount)
        every { accountRepository.findById("account_personal_4") } returns Optional.of(personalAccount)
        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction(any(), capture(legsSlot)) } returns LedgerPostResult("ledgertxn_move", emptyList())

        When("they sweep 10,000 RWF from personal into the real business account") {
            service.moveToBusiness("owner_4", BigDecimal("10000"))

            Then("it real-debits the personal account and real-credits the business account, same amount") {
                val legs = legsSlot.captured
                legs.size shouldBe 2
                val debitLeg = legs.first { it.direction == LedgerDirection.DEBIT }
                debitLeg.accountId shouldBe "account_personal_4"
                debitLeg.accountType shouldBe LedgerAccountType.WALLET
                debitLeg.amount shouldBe BigDecimal("10000")
                val creditLeg = legs.first { it.direction == LedgerDirection.CREDIT }
                creditLeg.accountId shouldBe "account_biz_4"
                creditLeg.amount shouldBe BigDecimal("10000")
            }
        }

        When("they draw 5,000 RWF back out to their real personal account") {
            service.moveToPersonal("owner_4", BigDecimal("5000"))

            Then("it real-debits the business account and real-credits the personal account, same amount") {
                val legs = legsSlot.captured
                val debitLeg = legs.first { it.direction == LedgerDirection.DEBIT }
                debitLeg.accountId shouldBe "account_biz_4"
                debitLeg.amount shouldBe BigDecimal("5000")
                val creditLeg = legs.first { it.direction == LedgerDirection.CREDIT }
                creditLeg.accountId shouldBe "account_personal_4"
                creditLeg.amount shouldBe BigDecimal("5000")
            }
        }

        When("they try to move a real zero amount") {
            Then("moveToBusiness throws InvalidMoveAmountException before ever touching the ledger") {
                try {
                    service.moveToBusiness("owner_4", BigDecimal.ZERO)
                    error("expected InvalidMoveAmountException")
                } catch (e: InvalidMoveAmountException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
            Then("moveToPersonal throws InvalidMoveAmountException before ever touching the ledger") {
                try {
                    service.moveToPersonal("owner_4", BigDecimal("-100"))
                    error("expected InvalidMoveAmountException")
                } catch (e: InvalidMoveAmountException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a merchant with a real business account but no real personal account on file") {
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(accountRepository = accountRepository, ledgerService = ledgerService)
        every { accountRepository.findByUserIdAndType("owner_5", AccountType.BUSINESS) } returns account("account_biz_5", "owner_5", AccountType.BUSINESS)
        every { accountRepository.findByUserIdAndType("owner_5", AccountType.MAIN) } returns null

        When("they try to move money to their business account") {
            Then("it throws MerchantNoAccountException before touching the ledger") {
                try {
                    service.moveToBusiness("owner_5", BigDecimal("5000"))
                    error("expected MerchantNoAccountException")
                } catch (e: MerchantNoAccountException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a merchant with no real business account yet, trying to move money") {
        val accountRepository = mockk<AccountRepository>()
        val service = newService(accountRepository = accountRepository)
        every { accountRepository.findByUserIdAndType("owner_6", AccountType.BUSINESS) } returns null

        When("they try moveToBusiness") {
            Then("it throws BusinessAccountNotFoundException, pointing them to open one first") {
                try {
                    service.moveToBusiness("owner_6", BigDecimal("5000"))
                    error("expected BusinessAccountNotFoundException")
                } catch (e: BusinessAccountNotFoundException) {
                    // expected
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
