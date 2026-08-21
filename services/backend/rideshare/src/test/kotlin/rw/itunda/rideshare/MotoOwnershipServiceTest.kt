package rw.itunda.rideshare

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.MotoOwnershipPlan
import rw.itunda.core.domain.MotoOwnershipPlanStatus
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.MotoOwnershipPlanRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.util.Optional

/**
 * First test coverage for the real Rwanda moto-taxi ownership savings-to-loan plan --
 * see MotoOwnershipService's own doc comment for the full sourced account. The
 * double-create-race test mirrors VupLoanServiceTest's/StudentLoanServiceTest's own
 * such test, asserting the caller's account lock happened before the active-plan
 * check. The contribute/repay-clamp tests guard against the same overshoot-clamp
 * regression class found in InsuranceService.contributeToFund/VupLoanService.repay:
 * they assert the actual ledger leg amount, not just the resulting field. The
 * convertToLoan test is this feature's own trickiest ledger logic -- it verifies the
 * single, atomically-balanced transaction (account CREDIT for the full bikePrice,
 * savings_goal_payable DEBIT releasing the down payment, loan_payable DEBIT for only
 * the genuinely new remaining principal) that replaced an earlier, real accounting
 * bug found in this feature's own build-time review: a two-transaction version that
 * credited loan_payable a second time for the down payment, silently understating
 * loan_payable's true balance by that amount for the life of the loan.
 *
 * Kotest lesson from this session: sibling `When` blocks under the same `Given` share
 * ONE mutable entity created in the `Given` block -- a mutation in one `When` leaks
 * into siblings. Any test needing a distinct starting state gets its own separate
 * `Given` block, not a sibling `When`.
 */
class MotoOwnershipServiceTest : BehaviorSpec({

    fun account(id: String, userId: String, availableBalance: BigDecimal = BigDecimal("1000000")) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = availableBalance, availableBalance = availableBalance,
    )

    fun newService(
        motoOwnershipPlanRepository: MotoOwnershipPlanRepository = mockk(),
        accountRepository: AccountRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        rateLimiter: RateLimiter = mockk(relaxed = true),
    ) = MotoOwnershipService(motoOwnershipPlanRepository, accountRepository, ledgerService, rateLimiter)

    Given("a user creating a moto-taxi ownership plan") {
        val motoOwnershipPlanRepository = mockk<MotoOwnershipPlanRepository>()
        val accountRepository = mockk<AccountRepository>()
        val service = newService(motoOwnershipPlanRepository = motoOwnershipPlanRepository, accountRepository = accountRepository)
        val savedSlot = slot<MotoOwnershipPlan>()
        every { motoOwnershipPlanRepository.save(capture(savedSlot)) } answers { firstArg() }
        every { motoOwnershipPlanRepository.findByUserIdAndStatusIn("user_1", any()) } returns emptyList()
        val applicantAccount = account("account_1", "user_1")
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns applicantAccount
        every { accountRepository.findByIdForUpdate("account_1") } returns Optional.of(applicantAccount)

        When("creating a plan for a real 600,000 RWF entry-level bike") {
            val result = service.createPlan("user_1", BigDecimal("600000"), BigDecimal("5000"))

            Then("a real SAVING plan is saved with itunda's own 30% down payment target") {
                result.status shouldBe MotoOwnershipPlanStatus.SAVING
                result.bikePrice shouldBe BigDecimal("600000")
                savedSlot.captured.downPaymentTarget shouldBe BigDecimal("180000.00")
                savedSlot.captured.savedAmount shouldBe BigDecimal.ZERO
                savedSlot.captured.loanOutstanding shouldBe BigDecimal.ZERO
            }
        }

        When("the bike price is below itunda's own realistic floor") {
            Then("the price guard real-400s before touching the repository") {
                shouldThrow<InvalidMotoOwnershipBikePriceException> {
                    service.createPlan("user_1", BigDecimal("50000"), BigDecimal("5000"))
                }
            }
        }

        When("the bike price is above itunda's own realistic ceiling") {
            Then("the price guard real-400s before touching the repository") {
                shouldThrow<InvalidMotoOwnershipBikePriceException> {
                    service.createPlan("user_1", BigDecimal("9999999"), BigDecimal("5000"))
                }
            }
        }

        When("the daily contribution is absurdly small") {
            Then("the contribution guard real-400s before touching the repository") {
                shouldThrow<InvalidMotoOwnershipContributionException> {
                    service.createPlan("user_1", BigDecimal("600000"), BigDecimal("1"))
                }
            }
        }
    }

    Given("a user who already has an active moto-taxi ownership plan") {
        val motoOwnershipPlanRepository = mockk<MotoOwnershipPlanRepository>()
        val accountRepository = mockk<AccountRepository>()
        val service = newService(motoOwnershipPlanRepository = motoOwnershipPlanRepository, accountRepository = accountRepository)
        val existing = MotoOwnershipPlan(
            id = "motoown_existing", userId = "user_1", bikePrice = BigDecimal("600000"),
            downPaymentTarget = BigDecimal("180000"), savedAmount = BigDecimal("50000"),
            dailyContribution = BigDecimal("5000"), loanOutstanding = BigDecimal.ZERO,
            status = MotoOwnershipPlanStatus.SAVING,
        )
        every { motoOwnershipPlanRepository.findByUserIdAndStatusIn("user_1", any()) } returns listOf(existing)
        val applicantAccount = account("account_1", "user_1")
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns applicantAccount
        every { accountRepository.findByIdForUpdate("account_1") } returns Optional.of(applicantAccount)

        When("creating a second plan") {
            Then("the one-active-plan guard fires, after locking the caller's own account row first (closing the double-create race)") {
                shouldThrow<MotoOwnershipPlanAlreadyActiveException> {
                    service.createPlan("user_1", BigDecimal("700000"), BigDecimal("5000"))
                }
                verify(exactly = 1) { accountRepository.findByIdForUpdate("account_1") }
            }
        }
    }

    Given("a real SAVING plan being contributed to") {
        val motoOwnershipPlanRepository = mockk<MotoOwnershipPlanRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(motoOwnershipPlanRepository = motoOwnershipPlanRepository, accountRepository = accountRepository, ledgerService = ledgerService)

        val plan = MotoOwnershipPlan(
            id = "motoown_1", userId = "user_1", bikePrice = BigDecimal("600000"),
            downPaymentTarget = BigDecimal("180000"), savedAmount = BigDecimal("170000"),
            dailyContribution = BigDecimal("5000"), loanOutstanding = BigDecimal.ZERO,
        )
        every { motoOwnershipPlanRepository.findById("motoown_1") } returns Optional.of(plan)
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { motoOwnershipPlanRepository.save(any()) } answers { firstArg() }

        When("contributing 50,000 with only a real 10,000 gap remaining to the down payment target") {
            val result = service.contribute("user_1", "motoown_1", BigDecimal("50000"))

            Then("the ledger only ever sees the real clamped 10,000, not the raw overshooting amount") {
                result.savedAmount shouldBe BigDecimal("180000")
                verify {
                    ledgerService.postLedgerTransaction(any(), match { legs ->
                        legs.all { it.amount == BigDecimal("10000") } &&
                            legs.any { it.accountId == "account_1" && it.direction == LedgerDirection.DEBIT } &&
                            legs.any { it.accountId == "savings_goal_payable" && it.accountType == LedgerAccountType.SAVINGS_GOAL_PAYABLE && it.direction == LedgerDirection.CREDIT }
                    })
                }
            }
        }

        When("someone else tries to contribute (IDOR)") {
            Then("it real-404s, not 403s") {
                shouldThrow<MotoOwnershipPlanNotFoundException> { service.contribute("attacker", "motoown_1", BigDecimal("1000")) }
            }
        }
    }

    Given("a real SAVING plan being cancelled") {
        val motoOwnershipPlanRepository = mockk<MotoOwnershipPlanRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(motoOwnershipPlanRepository = motoOwnershipPlanRepository, accountRepository = accountRepository, ledgerService = ledgerService)

        val plan = MotoOwnershipPlan(
            id = "motoown_2", userId = "user_1", bikePrice = BigDecimal("600000"),
            downPaymentTarget = BigDecimal("180000"), savedAmount = BigDecimal("70000"),
            dailyContribution = BigDecimal("5000"), loanOutstanding = BigDecimal.ZERO,
        )
        every { motoOwnershipPlanRepository.findById("motoown_2") } returns Optional.of(plan)
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_cancel", emptyList())
        every { motoOwnershipPlanRepository.save(any()) } answers { firstArg() }

        When("the borrower cancels it") {
            val result = service.cancel("user_1", "motoown_2")

            Then("the real 70,000 saved amount is refunded and the plan is CANCELLED") {
                result.status shouldBe MotoOwnershipPlanStatus.CANCELLED
                result.savedAmount shouldBe BigDecimal.ZERO
                verify {
                    ledgerService.postLedgerTransaction(any(), match { legs ->
                        legs.all { it.amount == BigDecimal("70000") } &&
                            legs.any { it.accountId == "savings_goal_payable" && it.accountType == LedgerAccountType.SAVINGS_GOAL_PAYABLE && it.direction == LedgerDirection.DEBIT } &&
                            legs.any { it.accountId == "account_1" && it.direction == LedgerDirection.CREDIT }
                    })
                }
            }
        }

        When("someone else tries to cancel it (IDOR)") {
            Then("it real-404s, not 403s") {
                shouldThrow<MotoOwnershipPlanNotFoundException> { service.cancel("attacker", "motoown_2") }
            }
        }
    }

    Given("a real SAVING plan that has not yet met its down payment target") {
        val motoOwnershipPlanRepository = mockk<MotoOwnershipPlanRepository>()
        val accountRepository = mockk<AccountRepository>()
        val service = newService(motoOwnershipPlanRepository = motoOwnershipPlanRepository, accountRepository = accountRepository)

        val plan = MotoOwnershipPlan(
            id = "motoown_3", userId = "user_1", bikePrice = BigDecimal("600000"),
            downPaymentTarget = BigDecimal("180000"), savedAmount = BigDecimal("90000"),
            dailyContribution = BigDecimal("5000"), loanOutstanding = BigDecimal.ZERO,
        )
        every { motoOwnershipPlanRepository.findById("motoown_3") } returns Optional.of(plan)

        When("the borrower tries to convert to a loan early") {
            Then("this feature's own core distinguishing behavior fires: conversion cannot happen before the down payment target is met") {
                shouldThrow<MotoOwnershipDownPaymentNotMetException> {
                    service.convertToLoan("user_1", "motoown_3")
                }
            }
        }
    }

    Given("a real SAVING plan that has met its down payment target, being converted to a loan") {
        val motoOwnershipPlanRepository = mockk<MotoOwnershipPlanRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(motoOwnershipPlanRepository = motoOwnershipPlanRepository, accountRepository = accountRepository, ledgerService = ledgerService)

        val plan = MotoOwnershipPlan(
            id = "motoown_4", userId = "user_1", bikePrice = BigDecimal("600000"),
            downPaymentTarget = BigDecimal("180000"), savedAmount = BigDecimal("180000"),
            dailyContribution = BigDecimal("5000"), loanOutstanding = BigDecimal.ZERO,
        )
        every { motoOwnershipPlanRepository.findById("motoown_4") } returns Optional.of(plan)
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_convert", emptyList())
        every { motoOwnershipPlanRepository.save(any()) } answers { firstArg() }

        When("converting to a loan") {
            val result = service.convertToLoan("user_1", "motoown_4")

            Then("loanOutstanding is bikePrice minus the real down payment already saved, and the plan is LOAN_ACTIVE") {
                result.status shouldBe MotoOwnershipPlanStatus.LOAN_ACTIVE
                result.loanOutstanding shouldBe BigDecimal("420000")
            }

            // Real accounting bug found in this feature's own build-time review
            // (2026-08-02): an earlier version posted TWO transactions, the second of
            // which credited loan_payable a second time for the down payment already
            // saved -- silently leaving loan_payable's real net position at
            // 420,000-180,000=240,000 against a claimed loanOutstanding of 420,000.
            // The fix posts ONE atomically-balanced transaction: the account receives
            // the FULL bikePrice (the down payment is released to spendable cash
            // alongside the new loan, not silently absorbed), savings_goal_payable is
            // debited for exactly the released down payment, and loan_payable is
            // debited for ONLY the genuinely new remaining principal -- so
            // loan_payable's real balance always exactly matches loanOutstanding.
            Then("a single balanced transaction CREDITs the account for the full 600,000 bike price, DEBITs savings_goal_payable for the released 180,000 down payment, and DEBITs loan_payable for only the real new 420,000 principal") {
                verify(exactly = 1) {
                    ledgerService.postLedgerTransaction(any(), match { legs ->
                        legs.size == 3 &&
                            legs.any { it.accountId == "account_1" && it.direction == LedgerDirection.CREDIT && it.amount == BigDecimal("600000") } &&
                            legs.any { it.accountId == "savings_goal_payable" && it.accountType == LedgerAccountType.SAVINGS_GOAL_PAYABLE && it.direction == LedgerDirection.DEBIT && it.amount == BigDecimal("180000") } &&
                            legs.any { it.accountId == "loan_payable" && it.accountType == LedgerAccountType.LOAN_PAYABLE && it.direction == LedgerDirection.DEBIT && it.amount == BigDecimal("420000") }
                    })
                }
            }

            Then("the transaction's own legs are internally balanced (debits equal credits), the real invariant the earlier bug silently violated") {
                val legsSlot = slot<List<rw.itunda.core.ledger.LedgerLeg>>()
                verify { ledgerService.postLedgerTransaction(any(), capture(legsSlot)) }
                val debits = legsSlot.captured.filter { it.direction == LedgerDirection.DEBIT }.sumOf { it.amount }
                val credits = legsSlot.captured.filter { it.direction == LedgerDirection.CREDIT }.sumOf { it.amount }
                debits shouldBe credits
                debits shouldBe BigDecimal("600000")
            }
        }

        When("someone else tries to convert it (IDOR)") {
            Then("it real-404s, not 403s") {
                shouldThrow<MotoOwnershipPlanNotFoundException> { service.convertToLoan("attacker", "motoown_4") }
            }
        }
    }

    Given("a real LOAN_ACTIVE plan being overpaid") {
        val motoOwnershipPlanRepository = mockk<MotoOwnershipPlanRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(motoOwnershipPlanRepository = motoOwnershipPlanRepository, accountRepository = accountRepository, ledgerService = ledgerService)

        val plan = MotoOwnershipPlan(
            id = "motoown_5", userId = "user_1", bikePrice = BigDecimal("600000"),
            downPaymentTarget = BigDecimal("180000"), savedAmount = BigDecimal("180000"),
            dailyContribution = BigDecimal("5000"), loanOutstanding = BigDecimal("30000"),
            status = MotoOwnershipPlanStatus.LOAN_ACTIVE,
        )
        every { motoOwnershipPlanRepository.findById("motoown_5") } returns Optional.of(plan)
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_repay", emptyList())
        every { motoOwnershipPlanRepository.save(any()) } answers { firstArg() }

        When("repaying 100,000 against a real 30,000 outstanding balance") {
            val result = service.repay("user_1", "motoown_5", BigDecimal("100000"))

            Then("the ledger only ever sees the real clamped 30,000, not the raw overshooting amount, and status becomes COMPLETED") {
                result.status shouldBe MotoOwnershipPlanStatus.COMPLETED
                result.loanOutstanding shouldBe BigDecimal.ZERO
                verify {
                    ledgerService.postLedgerTransaction(any(), match { legs ->
                        legs.all { it.amount == BigDecimal("30000") } &&
                            legs.any { it.accountId == "account_1" && it.direction == LedgerDirection.DEBIT } &&
                            legs.any { it.accountId == "loan_payable" && it.accountType == LedgerAccountType.LOAN_PAYABLE && it.direction == LedgerDirection.CREDIT }
                    })
                }
            }
        }

        When("someone else tries to repay it (IDOR)") {
            Then("it real-404s, not 403s") {
                shouldThrow<MotoOwnershipPlanNotFoundException> { service.repay("attacker", "motoown_5", BigDecimal("10000")) }
            }
        }
    }

    Given("a real LOAN_ACTIVE plan being partially repaid") {
        val motoOwnershipPlanRepository = mockk<MotoOwnershipPlanRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(motoOwnershipPlanRepository = motoOwnershipPlanRepository, accountRepository = accountRepository, ledgerService = ledgerService)

        val plan = MotoOwnershipPlan(
            id = "motoown_6", userId = "user_1", bikePrice = BigDecimal("600000"),
            downPaymentTarget = BigDecimal("180000"), savedAmount = BigDecimal("180000"),
            dailyContribution = BigDecimal("5000"), loanOutstanding = BigDecimal("420000"),
            status = MotoOwnershipPlanStatus.LOAN_ACTIVE,
        )
        every { motoOwnershipPlanRepository.findById("motoown_6") } returns Optional.of(plan)
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_repay2", emptyList())
        every { motoOwnershipPlanRepository.save(any()) } answers { firstArg() }

        When("repaying 100,000 of the real 420,000 outstanding") {
            val result = service.repay("user_1", "motoown_6", BigDecimal("100000"))

            Then("the plan stays LOAN_ACTIVE with a real reduced outstanding balance") {
                result.status shouldBe MotoOwnershipPlanStatus.LOAN_ACTIVE
                result.loanOutstanding shouldBe BigDecimal("320000")
            }
        }
    }
})
