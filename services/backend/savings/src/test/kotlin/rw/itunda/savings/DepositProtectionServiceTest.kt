package rw.itunda.savings

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.bigdecimal.shouldBeEqualIgnoringScale
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.DepositProtectionFund
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.DepositProtectionFundRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

/**
 * Real zero-test-coverage gap found live (2026-09-14, test-coverage sweep): this
 * whole class -- real, live via SavingsController.getDepositProtectionStatus and
 * DepositProtectionScheduler's own real daily accrual -- had no test file at all.
 */
class DepositProtectionServiceTest : BehaviorSpec({

    Given("the fund row doesn't exist yet") {
        val fundRepository = mockk<DepositProtectionFundRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = DepositProtectionService(fundRepository, accountRepository, ledgerService)

        every { fundRepository.findById("system") } returns Optional.empty()
        every { fundRepository.save(any()) } answers { firstArg() }

        When("getOrCreateFund is called for the real first time") {
            val fund = service.getOrCreateFund()

            Then("a real fund row is created with a real zero starting reserve") {
                fund.reserveBalance shouldBeEqualIgnoringScale BigDecimal.ZERO
            }
        }
    }

    Given("an existing fund row and a real user with covered deposits") {
        val fundRepository = mockk<DepositProtectionFundRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = DepositProtectionService(fundRepository, accountRepository, ledgerService)

        val fund = DepositProtectionFund(reserveBalance = BigDecimal("1000000"))
        every { fundRepository.findById("system") } returns Optional.of(fund)
        every { accountRepository.sumBalanceByUserIdAndTypeIn("user_1", any()) } returns BigDecimal("600000")

        When("they check their real coverage status") {
            val status = service.getStatus("user_1")

            Then("their real deposits are correctly capped at the fund's own real coverage limit") {
                status.yourTotalDeposits shouldBeEqualIgnoringScale BigDecimal("600000")
                status.yourCoveredBalance shouldBeEqualIgnoringScale BigDecimal("500000")
                status.fundReserveBalance shouldBeEqualIgnoringScale BigDecimal("1000000")
            }
        }
    }

    Given("a fund that has never received a contribution") {
        val fund = DepositProtectionFund(reserveBalance = BigDecimal.ZERO, lastContributionAt = null)
        val service = DepositProtectionService(mockk(), mockk(), mockk())

        Then("isContributionDue is real-true -- a brand-new fund must not wait a full interval before its first contribution") {
            service.isContributionDue(fund) shouldBe true
        }
    }

    Given("a fund contributed to less than a real day ago") {
        val fund = DepositProtectionFund(reserveBalance = BigDecimal.ZERO, lastContributionAt = Instant.now().minusSeconds(3600))
        val service = DepositProtectionService(mockk(), mockk(), mockk())

        Then("isContributionDue is real-false") {
            service.isContributionDue(fund) shouldBe false
        }
    }

    Given("a fund last contributed to more than a real day ago") {
        val fund = DepositProtectionFund(reserveBalance = BigDecimal.ZERO, lastContributionAt = Instant.now().minusSeconds(90_000))
        val service = DepositProtectionService(mockk(), mockk(), mockk())

        Then("isContributionDue is real-true") {
            service.isContributionDue(fund) shouldBe true
        }
    }

    Given("a real fund and total covered deposits across all users") {
        val fundRepository = mockk<DepositProtectionFundRepository>(relaxed = true)
        every { fundRepository.save(any()) } answers { firstArg() }
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = DepositProtectionService(fundRepository, accountRepository, ledgerService)

        val fund = DepositProtectionFund(reserveBalance = BigDecimal("1000000"), contributionRateBps = 50)
        every { accountRepository.sumBalanceByTypeIn(any()) } returns BigDecimal("100000000")
        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_dpf", emptyList())

        When("accrueContribution runs for the real daily tick") {
            service.accrueContribution(fund)

            Then("it real-posts a balanced ledger transaction for the real daily-divided annual rate") {
                val legs = legsSlot.captured
                legs.size shouldBe 2
                val debitLeg = legs.first { it.direction == LedgerDirection.DEBIT }
                debitLeg.accountId shouldBe "deposit_protection_expense"
                debitLeg.accountType shouldBe LedgerAccountType.DEPOSIT_PROTECTION_EXPENSE
                val creditLeg = legs.first { it.direction == LedgerDirection.CREDIT }
                creditLeg.accountId shouldBe "deposit_protection_reserve"
                creditLeg.amount shouldBe debitLeg.amount
                // 100,000,000 * (50/10000) / 365 ~= 1369.86
                debitLeg.amount shouldBeEqualIgnoringScale BigDecimal("1369.86")
            }
            Then("the real fund reserve balance and last-contribution timestamp are both updated") {
                fund.reserveBalance shouldBeEqualIgnoringScale BigDecimal("1001369.86")
                (fund.lastContributionAt != null) shouldBe true
                verify(exactly = 1) { fundRepository.save(fund) }
            }
        }
    }

    Given("a real fund with zero total covered deposits across the whole platform") {
        val fundRepository = mockk<DepositProtectionFundRepository>(relaxed = true)
        every { fundRepository.save(any()) } answers { firstArg() }
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = DepositProtectionService(fundRepository, accountRepository, ledgerService)

        val fund = DepositProtectionFund(reserveBalance = BigDecimal("1000000"))
        every { accountRepository.sumBalanceByTypeIn(any()) } returns BigDecimal.ZERO

        When("accrueContribution runs") {
            service.accrueContribution(fund)

            Then("no real ledger transaction is posted for a zero contribution, but the tick is still recorded") {
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                (fund.lastContributionAt != null) shouldBe true
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
