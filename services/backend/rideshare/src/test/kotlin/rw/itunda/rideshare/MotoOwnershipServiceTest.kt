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
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.MotoOwnershipPlanRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.util.Optional

/**
 * First test coverage for the real Rwanda moto-taxi ownership savings-to-loan plan --
 * see MotoOwnershipService's own doc comment for the full sourced account. The
 * double-create-race test mirrors VupLoanServiceTest's/StudentLoanServiceTest's own
 * such test, asserting the caller's wallet lock happened before the active-plan
 * check. The contribute/repay-clamp tests guard against the same overshoot-clamp
 * regression class found in InsuranceService.contributeToFund/VupLoanService.repay:
 * they assert the actual ledger leg amount, not just the resulting field. The
 * convertToLoan test is this feature's own trickiest ledger logic -- it verifies BOTH
 * the disbursement legs AND the separate savings-to-loan-payable transfer legs, with
 * their direction checked carefully.
 *
 * Kotest lesson from this session: sibling `When` blocks under the same `Given` share
 * ONE mutable entity created in the `Given` block -- a mutation in one `When` leaks
 * into siblings. Any test needing a distinct starting state gets its own separate
 * `Given` block, not a sibling `When`.
 */
class MotoOwnershipServiceTest : BehaviorSpec({

    fun wallet(id: String, userId: String, availableBalance: BigDecimal = BigDecimal("1000000")) = Wallet(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test wallet",
        type = WalletType.MAIN, balance = availableBalance, availableBalance = availableBalance,
    )

    fun newService(
        motoOwnershipPlanRepository: MotoOwnershipPlanRepository = mockk(),
        walletRepository: WalletRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        rateLimiter: RateLimiter = mockk(relaxed = true),
    ) = MotoOwnershipService(motoOwnershipPlanRepository, walletRepository, ledgerService, rateLimiter)

    Given("a user creating a moto-taxi ownership plan") {
        val motoOwnershipPlanRepository = mockk<MotoOwnershipPlanRepository>()
        val walletRepository = mockk<WalletRepository>()
        val service = newService(motoOwnershipPlanRepository = motoOwnershipPlanRepository, walletRepository = walletRepository)
        val savedSlot = slot<MotoOwnershipPlan>()
        every { motoOwnershipPlanRepository.save(capture(savedSlot)) } answers { firstArg() }
        every { motoOwnershipPlanRepository.findByUserIdAndStatusIn("user_1", any()) } returns emptyList()
        val applicantWallet = wallet("wallet_1", "user_1")
        every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns applicantWallet
        every { walletRepository.findByIdForUpdate("wallet_1") } returns Optional.of(applicantWallet)

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
        val walletRepository = mockk<WalletRepository>()
        val service = newService(motoOwnershipPlanRepository = motoOwnershipPlanRepository, walletRepository = walletRepository)
        val existing = MotoOwnershipPlan(
            id = "motoown_existing", userId = "user_1", bikePrice = BigDecimal("600000"),
            downPaymentTarget = BigDecimal("180000"), savedAmount = BigDecimal("50000"),
            dailyContribution = BigDecimal("5000"), loanOutstanding = BigDecimal.ZERO,
            status = MotoOwnershipPlanStatus.SAVING,
        )
        every { motoOwnershipPlanRepository.findByUserIdAndStatusIn("user_1", any()) } returns listOf(existing)
        val applicantWallet = wallet("wallet_1", "user_1")
        every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns applicantWallet
        every { walletRepository.findByIdForUpdate("wallet_1") } returns Optional.of(applicantWallet)

        When("creating a second plan") {
            Then("the one-active-plan guard fires, after locking the caller's own wallet row first (closing the double-create race)") {
                shouldThrow<MotoOwnershipPlanAlreadyActiveException> {
                    service.createPlan("user_1", BigDecimal("700000"), BigDecimal("5000"))
                }
                verify(exactly = 1) { walletRepository.findByIdForUpdate("wallet_1") }
            }
        }
    }

    Given("a real SAVING plan being contributed to") {
        val motoOwnershipPlanRepository = mockk<MotoOwnershipPlanRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(motoOwnershipPlanRepository = motoOwnershipPlanRepository, walletRepository = walletRepository, ledgerService = ledgerService)

        val plan = MotoOwnershipPlan(
            id = "motoown_1", userId = "user_1", bikePrice = BigDecimal("600000"),
            downPaymentTarget = BigDecimal("180000"), savedAmount = BigDecimal("170000"),
            dailyContribution = BigDecimal("5000"), loanOutstanding = BigDecimal.ZERO,
        )
        every { motoOwnershipPlanRepository.findById("motoown_1") } returns Optional.of(plan)
        every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns wallet("wallet_1", "user_1")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { motoOwnershipPlanRepository.save(any()) } answers { firstArg() }

        When("contributing 50,000 with only a real 10,000 gap remaining to the down payment target") {
            val result = service.contribute("user_1", "motoown_1", BigDecimal("50000"))

            Then("the ledger only ever sees the real clamped 10,000, not the raw overshooting amount") {
                result.savedAmount shouldBe BigDecimal("180000")
                verify {
                    ledgerService.postLedgerTransaction(any(), match { legs ->
                        legs.all { it.amount == BigDecimal("10000") } &&
                            legs.any { it.accountId == "wallet_1" && it.direction == LedgerDirection.DEBIT } &&
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
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(motoOwnershipPlanRepository = motoOwnershipPlanRepository, walletRepository = walletRepository, ledgerService = ledgerService)

        val plan = MotoOwnershipPlan(
            id = "motoown_2", userId = "user_1", bikePrice = BigDecimal("600000"),
            downPaymentTarget = BigDecimal("180000"), savedAmount = BigDecimal("70000"),
            dailyContribution = BigDecimal("5000"), loanOutstanding = BigDecimal.ZERO,
        )
        every { motoOwnershipPlanRepository.findById("motoown_2") } returns Optional.of(plan)
        every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns wallet("wallet_1", "user_1")
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
                            legs.any { it.accountId == "wallet_1" && it.direction == LedgerDirection.CREDIT }
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
        val walletRepository = mockk<WalletRepository>()
        val service = newService(motoOwnershipPlanRepository = motoOwnershipPlanRepository, walletRepository = walletRepository)

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
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(motoOwnershipPlanRepository = motoOwnershipPlanRepository, walletRepository = walletRepository, ledgerService = ledgerService)

        val plan = MotoOwnershipPlan(
            id = "motoown_4", userId = "user_1", bikePrice = BigDecimal("600000"),
            downPaymentTarget = BigDecimal("180000"), savedAmount = BigDecimal("180000"),
            dailyContribution = BigDecimal("5000"), loanOutstanding = BigDecimal.ZERO,
        )
        every { motoOwnershipPlanRepository.findById("motoown_4") } returns Optional.of(plan)
        every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns wallet("wallet_1", "user_1")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_convert", emptyList())
        every { motoOwnershipPlanRepository.save(any()) } answers { firstArg() }

        When("converting to a loan") {
            val result = service.convertToLoan("user_1", "motoown_4")

            Then("loanOutstanding is bikePrice minus the real down payment already saved, and the plan is LOAN_ACTIVE") {
                result.status shouldBe MotoOwnershipPlanStatus.LOAN_ACTIVE
                result.loanOutstanding shouldBe BigDecimal("420000")
            }

            Then("the disbursement transaction CREDITs the wallet and DEBITs loan_payable for the real remaining 420,000 balance") {
                verify {
                    ledgerService.postLedgerTransaction(any(), match { legs ->
                        legs.size == 2 &&
                            legs.any { it.accountId == "wallet_1" && it.direction == LedgerDirection.CREDIT && it.amount == BigDecimal("420000") } &&
                            legs.any { it.accountId == "loan_payable" && it.accountType == LedgerAccountType.LOAN_PAYABLE && it.direction == LedgerDirection.DEBIT && it.amount == BigDecimal("420000") }
                    })
                }
            }

            Then("a SEPARATE transaction DEBITs savings_goal_payable and CREDITs loan_payable for the real 180,000 down payment already saved -- the trickiest ledger direction in this feature") {
                verify {
                    ledgerService.postLedgerTransaction(any(), match { legs ->
                        legs.size == 2 &&
                            legs.any { it.accountId == "savings_goal_payable" && it.accountType == LedgerAccountType.SAVINGS_GOAL_PAYABLE && it.direction == LedgerDirection.DEBIT && it.amount == BigDecimal("180000") } &&
                            legs.any { it.accountId == "loan_payable" && it.accountType == LedgerAccountType.LOAN_PAYABLE && it.direction == LedgerDirection.CREDIT && it.amount == BigDecimal("180000") }
                    })
                }
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
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(motoOwnershipPlanRepository = motoOwnershipPlanRepository, walletRepository = walletRepository, ledgerService = ledgerService)

        val plan = MotoOwnershipPlan(
            id = "motoown_5", userId = "user_1", bikePrice = BigDecimal("600000"),
            downPaymentTarget = BigDecimal("180000"), savedAmount = BigDecimal("180000"),
            dailyContribution = BigDecimal("5000"), loanOutstanding = BigDecimal("30000"),
            status = MotoOwnershipPlanStatus.LOAN_ACTIVE,
        )
        every { motoOwnershipPlanRepository.findById("motoown_5") } returns Optional.of(plan)
        every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns wallet("wallet_1", "user_1")
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
                            legs.any { it.accountId == "wallet_1" && it.direction == LedgerDirection.DEBIT } &&
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
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(motoOwnershipPlanRepository = motoOwnershipPlanRepository, walletRepository = walletRepository, ledgerService = ledgerService)

        val plan = MotoOwnershipPlan(
            id = "motoown_6", userId = "user_1", bikePrice = BigDecimal("600000"),
            downPaymentTarget = BigDecimal("180000"), savedAmount = BigDecimal("180000"),
            dailyContribution = BigDecimal("5000"), loanOutstanding = BigDecimal("420000"),
            status = MotoOwnershipPlanStatus.LOAN_ACTIVE,
        )
        every { motoOwnershipPlanRepository.findById("motoown_6") } returns Optional.of(plan)
        every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns wallet("wallet_1", "user_1")
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
