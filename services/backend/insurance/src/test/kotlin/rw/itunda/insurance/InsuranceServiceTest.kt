package rw.itunda.insurance

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.InsuranceClaim
import rw.itunda.core.domain.InsuranceClaimStatus
import rw.itunda.core.domain.InsurancePolicy
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.InsuranceClaimRepository
import rw.itunda.core.repository.InsurancePolicyRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

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
        val insuranceClaimRepository = mockk<InsuranceClaimRepository>()
        val service = InsuranceService(insurancePolicyRepository, walletRepository, ledgerService, insuranceClaimRepository)

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
        val insuranceClaimRepository = mockk<InsuranceClaimRepository>()
        val service = InsuranceService(insurancePolicyRepository, walletRepository, ledgerService, insuranceClaimRepository)

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

    fun policy(id: String, userId: String, status: String = "active") = InsurancePolicy(
        id = id, userId = userId, planId = "ins_1", planName = "Health Shield", category = "health",
        status = status, startDate = LocalDate.now(), endDate = LocalDate.now().plusYears(1),
        monthlyPremium = BigDecimal("15000"), nextPaymentDate = LocalDate.now(), policyNumber = "POL-1",
    )

    Given("a user with a real active policy filing a real claim") {
        val insurancePolicyRepository = mockk<InsurancePolicyRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val insuranceClaimRepository = mockk<InsuranceClaimRepository>()
        val service = InsuranceService(insurancePolicyRepository, walletRepository, ledgerService, insuranceClaimRepository)

        every { insurancePolicyRepository.findById("pol_1") } returns Optional.of(policy("pol_1", "user_1"))
        every { insuranceClaimRepository.save(any()) } answers { firstArg() }

        When("filing a claim against their own active policy") {
            val claim = service.submitClaim("user_1", "pol_1", "Hospital stay", BigDecimal("50000"))

            Then("it creates a real SUBMITTED claim, not auto-approved") {
                claim.status shouldBe InsuranceClaimStatus.SUBMITTED
                claim.amount shouldBe BigDecimal("50000")
                verify(exactly = 1) { insuranceClaimRepository.save(any()) }
            }
        }
    }

    Given("a user trying to claim against someone else's policy") {
        val insurancePolicyRepository = mockk<InsurancePolicyRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val insuranceClaimRepository = mockk<InsuranceClaimRepository>()
        val service = InsuranceService(insurancePolicyRepository, walletRepository, ledgerService, insuranceClaimRepository)

        every { insurancePolicyRepository.findById("pol_2") } returns Optional.of(policy("pol_2", "owner_1"))

        When("attacker files a claim against it") {
            Then("it throws PolicyNotFoundException -- same 404-not-403 pattern used everywhere else") {
                try {
                    service.submitClaim("attacker", "pol_2", "fake claim", BigDecimal("1000"))
                    error("expected PolicyNotFoundException")
                } catch (e: PolicyNotFoundException) {
                    verify(exactly = 0) { insuranceClaimRepository.save(any()) }
                }
            }
        }
    }

    Given("a user trying to claim against a completed (non-active) policy") {
        val insurancePolicyRepository = mockk<InsurancePolicyRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val insuranceClaimRepository = mockk<InsuranceClaimRepository>()
        val service = InsuranceService(insurancePolicyRepository, walletRepository, ledgerService, insuranceClaimRepository)

        every { insurancePolicyRepository.findById("pol_3") } returns Optional.of(policy("pol_3", "user_1", status = "lapsed"))

        When("filing a claim") {
            Then("it throws PolicyNotActiveException") {
                try {
                    service.submitClaim("user_1", "pol_3", "test", BigDecimal("1000"))
                    error("expected PolicyNotActiveException")
                } catch (e: PolicyNotActiveException) {
                    verify(exactly = 0) { insuranceClaimRepository.save(any()) }
                }
            }
        }
    }

    Given("an ADMIN approving a real pending claim") {
        val insurancePolicyRepository = mockk<InsurancePolicyRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val insuranceClaimRepository = mockk<InsuranceClaimRepository>()
        val service = InsuranceService(insurancePolicyRepository, walletRepository, ledgerService, insuranceClaimRepository)

        val claim = InsuranceClaim(id = "claim_1", policyId = "pol_1", userId = "user_1", description = "Hospital stay", amount = BigDecimal("50000"))
        every { insuranceClaimRepository.findById("claim_1") } returns Optional.of(claim)
        every { insuranceClaimRepository.save(any()) } answers { firstArg() }
        every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns wallet("wallet_1", "user_1", WalletType.MAIN)
        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_claim", emptyList())

        When("approving it") {
            val decided = service.decideClaim("claim_1", "admin_1", approve = true, reason = null)

            Then("it pays out from insurance_claims_expense straight into the claimant's real wallet") {
                val expenseLeg = legsSlot.captured.first { it.accountType == LedgerAccountType.INSURANCE_CLAIMS_EXPENSE }
                expenseLeg.direction shouldBe LedgerDirection.DEBIT
                expenseLeg.amount shouldBe BigDecimal("50000")
                val walletLeg = legsSlot.captured.first { it.accountType == LedgerAccountType.WALLET }
                walletLeg.accountId shouldBe "wallet_1"
                walletLeg.direction shouldBe LedgerDirection.CREDIT
                walletLeg.amount shouldBe BigDecimal("50000")
            }
            Then("the claim is marked APPROVED with a real reviewer") {
                decided.status shouldBe InsuranceClaimStatus.APPROVED
                decided.reviewedBy shouldBe "admin_1"
            }
        }
    }

    Given("an ADMIN rejecting a real pending claim") {
        val insurancePolicyRepository = mockk<InsurancePolicyRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val insuranceClaimRepository = mockk<InsuranceClaimRepository>()
        val service = InsuranceService(insurancePolicyRepository, walletRepository, ledgerService, insuranceClaimRepository)

        val claim = InsuranceClaim(id = "claim_2", policyId = "pol_1", userId = "user_1", description = "test", amount = BigDecimal("10000"))
        every { insuranceClaimRepository.findById("claim_2") } returns Optional.of(claim)
        every { insuranceClaimRepository.save(any()) } answers { firstArg() }

        When("rejecting it") {
            val decided = service.decideClaim("claim_2", "admin_1", approve = false, reason = "Not covered by policy")

            Then("it never touches the ledger or the claimant's wallet") {
                decided.status shouldBe InsuranceClaimStatus.REJECTED
                decided.decisionReason shouldBe "Not covered by policy"
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                verify(exactly = 0) { walletRepository.findByUserIdAndType(any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
