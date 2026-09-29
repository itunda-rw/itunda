package rw.itunda.savings

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.core.domain.SavingsGoal
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.AccountFrozenException
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.security.CurrentUser
import java.math.BigDecimal
import org.springframework.web.bind.MissingRequestHeaderException

/**
 * First test coverage for SavingsController -- the highest-transaction-volume
 * controller in this domain (goal CRUD/deposit/withdraw/claim), despite every
 * sibling controller (IkiminaController/GroupAccountController/AutoTopUpController/
 * ForeignCurrencyController) already having one.
 */
class SavingsControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    fun controller(
        savingsService: SavingsService = mockk(),
        interestJarService: InterestJarService = mockk(),
        idempotencyService: IdempotencyService = mockk(),
        depositProtectionService: DepositProtectionService = mockk(),
        savingsMaturityReminderScheduler: SavingsMaturityReminderScheduler = mockk(),
    ) = SavingsController(savingsService, interestJarService, idempotencyService, depositProtectionService, savingsMaturityReminderScheduler)

    Given("a real request for the caller's own goals") {
        val savingsService = mockk<SavingsService>()
        val ctl = controller(savingsService = savingsService)
        every { savingsService.getGoals("user_1") } returns emptyList()

        When("fetching them") {
            ctl.getGoals(currentUser)
            Then("it queries only by the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { savingsService.getGoals("user_1") }
            }
        }
    }

    Given("a real goal creation") {
        val savingsService = mockk<SavingsService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(savingsService = savingsService, idempotencyService = idempotencyService)
        val goal = mockk<SavingsGoal>(relaxed = true)
        val request = CreateGoalRequest("Emergency Fund", BigDecimal("500000"))
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every { savingsService.createGoal("user_1", "Emergency Fund", BigDecimal("500000"), null, null, null) } returns goal
        every {
            idempotencyService.replayOrExecute("POST /api/v1/savings/goals", "key-1", request, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("creating") {
            val response = ctl.createGoal(request, "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/savings/goals", "key-1", request, any()) }
                verify(exactly = 1) { savingsService.createGoal("user_1", "Emergency Fund", BigDecimal("500000"), null, null, null) }
                response.statusCode shouldBe HttpStatus.CREATED
                response.body?.get("goal") shouldBe goal
            }
        }
    }

    Given("a real goal deposit") {
        val savingsService = mockk<SavingsService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(savingsService = savingsService, idempotencyService = idempotencyService)
        val goal = mockk<SavingsGoal>(relaxed = true)
        every { goal.name } returns "Emergency Fund"
        val request = DepositRequest("sg_1", BigDecimal("10000"))
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every { savingsService.depositToGoal("user_1", "sg_1", BigDecimal("10000"), null) } returns goal
        every {
            idempotencyService.replayOrExecute("POST /api/v1/savings/deposit", "key-1", request, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("depositing") {
            ctl.deposit(request, "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/savings/deposit", "key-1", request, any()) }
                verify(exactly = 1) { savingsService.depositToGoal("user_1", "sg_1", BigDecimal("10000"), null) }
            }
        }
    }

    Given("a real goal withdrawal") {
        val savingsService = mockk<SavingsService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(savingsService = savingsService, idempotencyService = idempotencyService)
        val goal = mockk<SavingsGoal>(relaxed = true)
        every { goal.name } returns "Emergency Fund"
        val request = WithdrawRequest("sg_1", BigDecimal("5000"))
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every { savingsService.withdrawFromGoal("user_1", "sg_1", BigDecimal("5000"), null) } returns goal
        every {
            idempotencyService.replayOrExecute("POST /api/v1/savings/withdraw", "key-1", request, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("withdrawing") {
            ctl.withdraw(request, "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/savings/withdraw", "key-1", request, any()) }
                verify(exactly = 1) { savingsService.withdrawFromGoal("user_1", "sg_1", BigDecimal("5000"), null) }
            }
        }
    }

    Given("a real request for a goal's own transactions") {
        val savingsService = mockk<SavingsService>()
        val ctl = controller(savingsService = savingsService)
        every { savingsService.getGoalTransactions("user_1", "sg_1") } returns emptyList()

        When("fetching them") {
            ctl.getGoalTransactions("sg_1", currentUser)
            Then("it queries scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { savingsService.getGoalTransactions("user_1", "sg_1") }
            }
        }
    }

    Given("a real interest jar claim") {
        val interestJarService = mockk<InterestJarService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(interestJarService = interestJarService, idempotencyService = idempotencyService)
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every { interestJarService.claimInterest("user_1") } returns mapOf("claimed" to BigDecimal("500"))
        every {
            idempotencyService.replayOrExecute("POST /api/v1/savings/interest-jar/claim", "key-1", emptyMap<String, Any>(), capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("claiming") {
            ctl.claimInterest("key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/savings/interest-jar/claim", "key-1", emptyMap<String, Any>(), any()) }
                verify(exactly = 1) { interestJarService.claimInterest("user_1") }
            }
        }
    }

    Given("a real ADMIN-only manual trigger for maturity reminders") {
        val scheduler = mockk<SavingsMaturityReminderScheduler>()
        val ctl = controller(savingsMaturityReminderScheduler = scheduler)
        every { scheduler.processDue() } returns 4

        When("triggering it") {
            val response = ctl.processMaturityReminders(currentUser)
            Then("it real-delegates and reports the real processed count") {
                verify(exactly = 1) { scheduler.processDue() }
                response.body?.get("processed") shouldBe 4
            }
        }
    }

    listOf(
        Triple(GoalNotFoundException("Not found") as RuntimeException, HttpStatus.NOT_FOUND, "GOAL_NOT_FOUND"),
        Triple(NoAccountException("Not found"), HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"),
        Triple(NoInterestJarException("Not found"), HttpStatus.NOT_FOUND, "INTEREST_JAR_NOT_FOUND"),
        Triple(NoInterestAvailableException("Conflict"), HttpStatus.CONFLICT, "NO_INTEREST_AVAILABLE"),
        Triple(GoalAlreadyCompletedException("Conflict"), HttpStatus.CONFLICT, "GOAL_ALREADY_COMPLETED"),
        Triple(InsufficientFundsException("Insufficient"), HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS"),
        Triple(InsufficientGoalBalanceException("Insufficient"), HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_GOAL_BALANCE"),
        Triple(AccountFrozenException("Frozen"), HttpStatus.FORBIDDEN, "ACCOUNT_FROZEN"),
        Triple(IdempotencyConflictException("Conflict"), HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT"),
        Triple(IdempotencyInProgressException("Conflict"), HttpStatus.CONFLICT, "IDEMPOTENT_REQUEST_PROCESSING"),
        Triple(RateLimitExceededException("Too many requests"), HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED"),
    ).forEach { (exception, expectedStatus, expectedCode) ->
        Given("a real ${exception::class.simpleName}") {
            val ctl = controller()

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is GoalNotFoundException -> ctl.handleGoalNotFound(exception)
                    is NoAccountException -> ctl.handleNoAccount(exception)
                    is NoInterestJarException -> ctl.handleNoJar(exception)
                    is NoInterestAvailableException -> ctl.handleNoInterest(exception)
                    is GoalAlreadyCompletedException -> ctl.handleGoalAlreadyCompleted(exception)
                    is InsufficientFundsException -> ctl.handleInsufficientFunds(exception)
                    is InsufficientGoalBalanceException -> ctl.handleInsufficientGoalBalance(exception)
                    is AccountFrozenException -> ctl.handleAccountFrozen(exception)
                    is IdempotencyConflictException -> ctl.handleConflict(exception)
                    is IdempotencyInProgressException -> ctl.handleInProgress(exception)
                    is RateLimitExceededException -> ctl.handleRateLimit(exception)
                    else -> error("unexpected exception type")
                }

                Then("it maps to $expectedStatus with code $expectedCode, not a generic 500") {
                    response.statusCode shouldBe expectedStatus
                    response.body?.code shouldBe expectedCode
                }
            }
        }
    }

    Given("a real MissingRequestHeaderException") {
        val ctl = controller()

        When("its exception handler maps it to a real HTTP response") {
            val response = ctl.handleMissingHeader(mockk(relaxed = true))

            Then("it maps to 400 with code IDEMPOTENCY_KEY_REQUIRED, not a generic 500") {
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
                response.body?.code shouldBe "IDEMPOTENCY_KEY_REQUIRED"
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
