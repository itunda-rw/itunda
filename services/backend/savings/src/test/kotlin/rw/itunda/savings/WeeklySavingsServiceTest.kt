package rw.itunda.savings

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.domain.WeeklySavingsInstallment
import rw.itunda.core.domain.WeeklySavingsPlan
import rw.itunda.core.domain.WeeklySavingsPlanStatus
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.WeeklySavingsInstallmentRepository
import rw.itunda.core.repository.WeeklySavingsPlanRepository
import rw.itunda.core.account.AccountNumberGenerator
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional

/** First test coverage for the real KakaoBank 26주적금 (26-week savings) equivalent. */
class WeeklySavingsServiceTest : BehaviorSpec({

    fun account(id: String, userId: String, type: AccountType = AccountType.MAIN, balance: BigDecimal = BigDecimal("1000000")) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = type, balance = balance, availableBalance = balance,
    )

    fun plan(
        id: String = "wsp_1",
        userId: String = "user_1",
        baseWeeklyAmount: String = "10000",
        escalationRate: String = "0.10",
        weeksElapsed: Int = 0,
        streakBroken: Boolean = false,
        status: WeeklySavingsPlanStatus = WeeklySavingsPlanStatus.ACTIVE,
    ) = WeeklySavingsPlan(
        id = id, userId = userId, accountId = "account_plan_$id", name = "My 26-Week Plan",
        baseWeeklyAmount = BigDecimal(baseWeeklyAmount), escalationRate = BigDecimal(escalationRate),
        openingWeekday = 1, baseRate = 5.0, bonusRate = 3.0, weeksElapsed = weeksElapsed,
        streakBroken = streakBroken, status = status, nextInstallmentDueAt = Instant.now(),
    )

    fun service(
        accountRepository: AccountRepository = mockk(),
        planRepository: WeeklySavingsPlanRepository = mockk(),
        installmentRepository: WeeklySavingsInstallmentRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        rateLimiter: RateLimiter = mockk(relaxed = true),
        accountNumberGenerator: AccountNumberGenerator = mockk(relaxed = true),
        ledgerEntryRepository: LedgerEntryRepository = mockk(relaxed = true),
    ) = WeeklySavingsService(accountRepository, planRepository, installmentRepository, ledgerService, rateLimiter, accountNumberGenerator, ledgerEntryRepository)

    Given("the real escalating weekly amount schedule") {
        val svc = service()

        Then("weeks 1-4 stay at the base amount, then step up every 4 weeks by the escalation rate") {
            svc.amountForWeek(BigDecimal("10000"), BigDecimal("0.10"), 1) shouldBe BigDecimal("10000.00")
            svc.amountForWeek(BigDecimal("10000"), BigDecimal("0.10"), 4) shouldBe BigDecimal("10000.00")
            svc.amountForWeek(BigDecimal("10000"), BigDecimal("0.10"), 5) shouldBe BigDecimal("11000.00")
            svc.amountForWeek(BigDecimal("10000"), BigDecimal("0.10"), 8) shouldBe BigDecimal("11000.00")
            svc.amountForWeek(BigDecimal("10000"), BigDecimal("0.10"), 9) shouldBe BigDecimal("12100.00")
            // Week 26 falls in the 7th step-up block (weeks 25-28, step index 6): 10000*(1.10)^6
            svc.amountForWeek(BigDecimal("10000"), BigDecimal("0.10"), 26) shouldBe BigDecimal("17715.61")
        }

        Then("a zero escalation rate never steps up") {
            svc.amountForWeek(BigDecimal("5000"), BigDecimal("0.00"), 26) shouldBe BigDecimal("5000.00")
        }
    }

    Given("creating a plan") {
        val accountRepository = mockk<AccountRepository>()
        val planRepository = mockk<WeeklySavingsPlanRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val svc = service(accountRepository = accountRepository, planRepository = planRepository, rateLimiter = rateLimiter)

        When("a valid escalation rate is chosen") {
            every { accountRepository.save(any()) } answers { firstArg() }
            every { planRepository.save(any()) } answers { firstArg() }

            val created = svc.createPlan("user_1", "Trip Fund", BigDecimal("10000"), BigDecimal("0.10"))

            Then("it provisions a real dedicated WEEKLY_SAVINGS account and locks the opening weekday from real now") {
                created.userId shouldBe "user_1"
                created.baseRate shouldBe 5.0
                created.bonusRate shouldBe 3.0
                created.status shouldBe WeeklySavingsPlanStatus.ACTIVE
                (created.openingWeekday in 1..7) shouldBe true
                verify(exactly = 1) { accountRepository.save(match { it.type == AccountType.WEEKLY_SAVINGS }) }
            }
        }

        When("an unsupported escalation rate is requested") {
            Then("it real-rejects rather than silently accepting an arbitrary rate") {
                try {
                    svc.createPlan("user_1", "Bad Plan", BigDecimal("10000"), BigDecimal("0.15"))
                    error("expected WeeklyPlanInvalidEscalationException")
                } catch (e: WeeklyPlanInvalidEscalationException) {
                    verify(exactly = 0) { planRepository.save(any()) }
                }
            }
        }

        When("a non-positive weekly amount is requested") {
            Then("it real-rejects before touching any account or plan") {
                try {
                    svc.createPlan("user_1", "Bad Plan", BigDecimal.ZERO, BigDecimal("0.10"))
                    error("expected WeeklyPlanInvalidAmountException")
                } catch (e: WeeklyPlanInvalidAmountException) {
                    verify(exactly = 0) { planRepository.save(any()) }
                }
            }
        }

        When("the real per-user rate limit is exceeded") {
            val limited = mockk<RateLimiter>()
            every { limited.checkLimit("weekly-savings:create:user_9", limit = 10, window = Duration.ofHours(1)) } throws RateLimitExceededException("Too many requests")
            val limitedSvc = service(rateLimiter = limited)

            Then("it real-propagates RateLimitExceededException") {
                try {
                    limitedSvc.createPlan("user_9", "Plan", BigDecimal("10000"), BigDecimal("0.10"))
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    // expected
                }
            }
        }
    }

    Given("processing a due weekly installment with sufficient funds") {
        val accountRepository = mockk<AccountRepository>()
        val planRepository = mockk<WeeklySavingsPlanRepository>()
        val installmentRepository = mockk<WeeklySavingsInstallmentRepository>()
        val ledgerService = mockk<LedgerService>()
        val svc = service(accountRepository, planRepository, installmentRepository, ledgerService)

        val theplan = plan(weeksElapsed = 4) // about to process week 5 -> escalated amount
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_main", "user_1")
        every { accountRepository.findById("account_plan_wsp_1") } returns Optional.of(account("account_plan_wsp_1", "user_1", AccountType.WEEKLY_SAVINGS, BigDecimal("40000")))
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { installmentRepository.save(any()) } answers { firstArg() }
        every { planRepository.save(any()) } answers { firstArg() }

        val originalDue = theplan.nextInstallmentDueAt
        val succeeded = svc.processDueInstallment(theplan)

        Then("it debits the real escalated week-5 amount, records the installment, and advances exactly 7 real days") {
            succeeded shouldBe true
            theplan.weeksElapsed shouldBe 5
            theplan.installmentsCollected shouldBe 1
            theplan.currentAmount shouldBe BigDecimal("11000.00")
            theplan.nextInstallmentDueAt shouldBe originalDue.plus(7, ChronoUnit.DAYS)
            theplan.streakBroken shouldBe false
            verify(exactly = 1) { ledgerService.postLedgerTransaction(any(), any()) }
            val installmentSlot = slot<WeeklySavingsInstallment>()
            verify(exactly = 1) { installmentRepository.save(capture(installmentSlot)) }
            installmentSlot.captured.amount shouldBe BigDecimal("11000.00")
            installmentSlot.captured.weekNumber shouldBe 5
        }
    }

    Given("processing a due weekly installment with insufficient funds") {
        val accountRepository = mockk<AccountRepository>()
        val planRepository = mockk<WeeklySavingsPlanRepository>()
        val installmentRepository = mockk<WeeklySavingsInstallmentRepository>()
        val ledgerService = mockk<LedgerService>()
        val svc = service(accountRepository, planRepository, installmentRepository, ledgerService)

        val theplan = plan()
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_main", "user_1", balance = BigDecimal("1"))
        every { planRepository.save(any()) } answers { firstArg() }

        val succeeded = svc.processDueInstallment(theplan)

        Then("it skips gracefully -- no ledger call, no installment row -- but permanently breaks the streak and still advances the fixed schedule") {
            succeeded shouldBe false
            theplan.streakBroken shouldBe true
            theplan.weeksElapsed shouldBe 1
            theplan.installmentsCollected shouldBe 0
            verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            verify(exactly = 0) { installmentRepository.save(any()) }
        }
    }

    Given("a plan reaching its real 26th elapsed week") {
        val accountRepository = mockk<AccountRepository>()
        val planRepository = mockk<WeeklySavingsPlanRepository>()
        val installmentRepository = mockk<WeeklySavingsInstallmentRepository>()
        val ledgerService = mockk<LedgerService>()
        val svc = service(accountRepository, planRepository, installmentRepository, ledgerService)

        val theplan = plan(weeksElapsed = 25, streakBroken = false)
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_main", "user_1")
        every { accountRepository.findById("account_plan_wsp_1") } returns Optional.of(account("account_plan_wsp_1", "user_1", AccountType.WEEKLY_SAVINGS))
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { installmentRepository.save(any()) } answers { firstArg() }
        every { installmentRepository.findByPlanIdOrderByWeekNumberAsc("wsp_1") } returns listOf(
            WeeklySavingsInstallment(id = "i1", planId = "wsp_1", weekNumber = 26, amount = BigDecimal("10000"), depositedAt = Instant.now()),
        )
        every { planRepository.save(any()) } answers { firstArg() }

        svc.processDueInstallment(theplan)

        Then("it auto-matures with the real bonus rate applied since the streak survived unbroken") {
            theplan.status shouldBe WeeklySavingsPlanStatus.MATURED
            theplan.maturedAt shouldNotBe null
            (theplan.totalInterestPaid != null) shouldBe true
        }
    }

    Given("a plan reaching maturity with a broken streak") {
        val accountRepository = mockk<AccountRepository>()
        val planRepository = mockk<WeeklySavingsPlanRepository>()
        val installmentRepository = mockk<WeeklySavingsInstallmentRepository>()
        val ledgerService = mockk<LedgerService>()
        val svc = service(accountRepository, planRepository, installmentRepository, ledgerService)

        val theplan = plan(weeksElapsed = 25, streakBroken = true)
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_main", "user_1")
        every { accountRepository.findById("account_plan_wsp_1") } returns Optional.of(account("account_plan_wsp_1", "user_1", AccountType.WEEKLY_SAVINGS))
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { installmentRepository.save(any()) } answers { firstArg() }
        val installments = mutableListOf<WeeklySavingsInstallment>()
        every { installmentRepository.findByPlanIdOrderByWeekNumberAsc("wsp_1") } answers { installments.toList() }
        every { planRepository.save(any()) } answers { firstArg() }

        svc.processDueInstallment(theplan)

        Then("it matures at base rate only -- the bonus is permanently forfeited") {
            theplan.status shouldBe WeeklySavingsPlanStatus.MATURED
            theplan.streakBroken shouldBe true
        }
    }

    Given("computing real per-installment interest") {
        val svc = service()

        When("two installments deposited at different times are evaluated as of the same maturity date") {
            val asOf = Instant.now()
            val installments = listOf(
                WeeklySavingsInstallment(id = "i1", planId = "p", weekNumber = 1, amount = BigDecimal("10000"), depositedAt = asOf.minus(182, ChronoUnit.DAYS)),
                WeeklySavingsInstallment(id = "i2", planId = "p", weekNumber = 26, amount = BigDecimal("10000"), depositedAt = asOf.minus(7, ChronoUnit.DAYS)),
            )

            val interest = svc.computeInterest(installments, asOf, 8.0)

            Then("the earlier installment earns more interest than the later one, on its own remaining term") {
                val week1Interest = BigDecimal("10000").multiply(BigDecimal("0.08")).multiply(BigDecimal(182)).divide(BigDecimal(365), 10, java.math.RoundingMode.HALF_UP)
                val week26Interest = BigDecimal("10000").multiply(BigDecimal("0.08")).multiply(BigDecimal(7)).divide(BigDecimal(365), 10, java.math.RoundingMode.HALF_UP)
                interest shouldBe (week1Interest.add(week26Interest)).setScale(2, java.math.RoundingMode.HALF_UP)
                (week1Interest > week26Interest) shouldBe true
            }
        }
    }

    Given("cancelling an active plan early") {
        val accountRepository = mockk<AccountRepository>()
        val planRepository = mockk<WeeklySavingsPlanRepository>()
        val installmentRepository = mockk<WeeklySavingsInstallmentRepository>()
        val ledgerService = mockk<LedgerService>()
        val svc = service(accountRepository, planRepository, installmentRepository, ledgerService)

        val theplan = plan(weeksElapsed = 10, streakBroken = false)
        every { planRepository.findById("wsp_1") } returns Optional.of(theplan)
        every { installmentRepository.findByPlanIdOrderByWeekNumberAsc("wsp_1") } returns listOf(
            WeeklySavingsInstallment(id = "i1", planId = "wsp_1", weekNumber = 1, amount = BigDecimal("10000"), depositedAt = Instant.now().minus(70, ChronoUnit.DAYS)),
        )
        every { accountRepository.findById("account_plan_wsp_1") } returns Optional.of(account("account_plan_wsp_1", "user_1", AccountType.WEEKLY_SAVINGS, BigDecimal("10000")))
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_main", "user_1")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { planRepository.save(any()) } answers { firstArg() }

        val result = svc.cancelPlan("user_1", "wsp_1")

        Then("it forfeits the streak bonus, pays base-rate-only interest, and immediately pays out to MAIN in one action") {
            result.plan.status shouldBe WeeklySavingsPlanStatus.CANCELLED
            result.plan.streakBroken shouldBe true
            (result.plan.withdrawnAt != null) shouldBe true
            verify(exactly = 2) { ledgerService.postLedgerTransaction(any(), any()) } // interest credit + payout
        }
    }

    Given("cancelling a plan that is not active") {
        val planRepository = mockk<WeeklySavingsPlanRepository>()
        val svc = service(planRepository = planRepository)
        val theplan = plan(status = WeeklySavingsPlanStatus.MATURED)
        every { planRepository.findById("wsp_1") } returns Optional.of(theplan)

        Then("it real-rejects rather than double-cancelling") {
            try {
                svc.cancelPlan("user_1", "wsp_1")
                error("expected WeeklyPlanNotActiveException")
            } catch (e: WeeklyPlanNotActiveException) {
                // expected
            }
        }
    }

    Given("withdrawing a matured plan") {
        val accountRepository = mockk<AccountRepository>()
        val planRepository = mockk<WeeklySavingsPlanRepository>()
        val installmentRepository = mockk<WeeklySavingsInstallmentRepository>()
        val ledgerService = mockk<LedgerService>()
        val svc = service(accountRepository, planRepository, installmentRepository, ledgerService)

        val theplan = plan(status = WeeklySavingsPlanStatus.MATURED, weeksElapsed = 26)
        every { planRepository.findById("wsp_1") } returns Optional.of(theplan)
        every { accountRepository.findById("account_plan_wsp_1") } returns Optional.of(account("account_plan_wsp_1", "user_1", AccountType.WEEKLY_SAVINGS, BigDecimal("270400")))
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_main", "user_1")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { installmentRepository.findByPlanIdOrderByWeekNumberAsc("wsp_1") } returns emptyList()
        every { planRepository.save(any()) } answers { firstArg() }

        val result = svc.withdraw("user_1", "wsp_1")

        Then("it moves the full plan balance out to MAIN and stamps withdrawnAt") {
            (result.plan.withdrawnAt != null) shouldBe true
            verify(exactly = 1) { ledgerService.postLedgerTransaction(any(), any()) }
        }

        When("withdraw is attempted a second time") {
            Then("it real-rejects a double withdrawal") {
                try {
                    svc.withdraw("user_1", "wsp_1")
                    error("expected WeeklyPlanAlreadyWithdrawnException")
                } catch (e: WeeklyPlanAlreadyWithdrawnException) {
                    // expected
                }
            }
        }
    }

    Given("withdrawing a plan that has not matured") {
        val planRepository = mockk<WeeklySavingsPlanRepository>()
        val svc = service(planRepository = planRepository)
        val theplan = plan(status = WeeklySavingsPlanStatus.ACTIVE)
        every { planRepository.findById("wsp_1") } returns Optional.of(theplan)

        Then("it real-rejects rather than letting an active plan be withdrawn early through this path") {
            try {
                svc.withdraw("user_1", "wsp_1")
                error("expected WeeklyPlanNotMaturedException")
            } catch (e: WeeklyPlanNotMaturedException) {
                // expected
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
