package rw.itunda.insurance

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.InsurancePolicyRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal

/**
 * First test coverage for insurance -- specifically a regression guard for a real bug
 * fixed earlier this same session: enrollInPlan used to call a *different*, mocked
 * LedgerService (getAccount() fabricated a random account, .save() calls were
 * commented out) so premium debits silently never happened despite the parity matrix
 * describing this flow as ledger-backed. It now posts through the real
 * rw.itunda.core.ledger.LedgerService -- this file asserts the actual ledger legs it
 * posts, not just that *a* ledger call happens.
 */
class InsuranceServiceTest : BehaviorSpec({

    fun wallet(id: String, userId: String, type: WalletType) = Wallet(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test wallet",
        type = type, balance = BigDecimal("100000"), availableBalance = BigDecimal("100000"),
    )

    Given("a user with a MAIN wallet enrolling in a real plan") {
        val insurancePolicyRepository = mockk<InsurancePolicyRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = InsuranceService(insurancePolicyRepository, walletRepository, ledgerService)

        every { walletRepository.findByUserId("user_1") } returns listOf(
            wallet("wallet_main", "user_1", WalletType.MAIN),
            wallet("wallet_savings", "user_1", WalletType.SAVINGS),
        )
        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { insurancePolicyRepository.save(any()) } answers { firstArg() }

        When("enrolling in Health Shield (15,000 RWF/month)") {
            val policy = service.enrollInPlan("user_1", "ins_1")

            Then("it debits the MAIN wallet specifically, not just any wallet the user has") {
                val walletLeg = legsSlot.captured.first { it.accountType == LedgerAccountType.WALLET }
                walletLeg.accountId shouldBe "wallet_main"
                walletLeg.direction shouldBe LedgerDirection.DEBIT
                walletLeg.amount shouldBe BigDecimal("15000")
            }
            Then("it credits insurance_premium_revenue for the exact same amount -- this is the real ledger, not the fake one this session found and deleted") {
                val revenueLeg = legsSlot.captured.first { it.accountType == LedgerAccountType.INSURANCE_PREMIUM_REVENUE }
                revenueLeg.accountId shouldBe "insurance_premium_revenue"
                revenueLeg.direction shouldBe LedgerDirection.CREDIT
                revenueLeg.amount shouldBe BigDecimal("15000")
                verify(exactly = 1) { ledgerService.postLedgerTransaction("RWF", any()) }
            }
            Then("it creates a real, saved policy tied to the plan") {
                policy.planName shouldBe "Health Shield"
                policy.category shouldBe "health"
                policy.status shouldBe "active"
                verify(exactly = 1) { insurancePolicyRepository.save(any()) }
            }
        }

        When("enrolling in a plan that doesn't exist") {
            Then("it throws PlanNotFoundException before touching the ledger") {
                try {
                    service.enrollInPlan("user_1", "ins_does_not_exist")
                    error("expected PlanNotFoundException")
                } catch (e: PlanNotFoundException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("a user with no wallets at all") {
        val insurancePolicyRepository = mockk<InsurancePolicyRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = InsuranceService(insurancePolicyRepository, walletRepository, ledgerService)

        every { walletRepository.findByUserId("user_2") } returns emptyList()

        When("enrolling in a plan") {
            Then("it throws NoWalletException before touching the ledger") {
                try {
                    service.enrollInPlan("user_2", "ins_1")
                    error("expected NoWalletException")
                } catch (e: NoWalletException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
