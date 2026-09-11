package rw.itunda.account

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MissingRequestHeaderException
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.agents.AgentWithdrawalAuthorizationService
import rw.itunda.core.agents.TooManyWithdrawalAuthorizationsException
import rw.itunda.core.agents.WithdrawalAuthorizationInvalidException
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.domain.SpendingBudget
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.AccountFrozenException
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.provider.ProviderDeclinedException
import rw.itunda.core.security.CurrentUser
import java.math.BigDecimal
import java.time.Instant

/**
 * First test coverage for AccountController -- the core account/transfer/spending/
 * budget/agent-withdrawal-auth surface, despite every sibling controller
 * (IkiminaController/GroupAccountController/AutoTopUpController/
 * ForeignCurrencyController) already having one.
 */
class AccountControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    fun controller(
        accountService: AccountService = mockk(),
        spendingInsightService: SpendingInsightService = mockk(),
        idempotencyService: IdempotencyService = mockk(),
        agentWithdrawalAuthorizationService: AgentWithdrawalAuthorizationService = mockk(),
        subscriptionDetectionService: SubscriptionDetectionService = mockk(),
    ) = AccountController(accountService, spendingInsightService, idempotencyService, agentWithdrawalAuthorizationService, subscriptionDetectionService)

    fun account(id: String, userId: String) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal("100000"), availableBalance = BigDecimal("100000"),
    )

    Given("a real request for the caller's own accounts") {
        val accountService = mockk<AccountService>()
        val ctl = controller(accountService = accountService)
        every { accountService.getAccounts("user_1") } returns listOf(account("account_1", "user_1"))

        When("fetching them") {
            ctl.getAccounts(currentUser)
            Then("it queries only by the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { accountService.getAccounts("user_1") }
            }
        }
    }

    Given("a real request for one of the caller's own accounts") {
        val accountService = mockk<AccountService>()
        val ctl = controller(accountService = accountService)
        every { accountService.getAccountById("account_1", "user_1") } returns account("account_1", "user_1")

        When("fetching it") {
            ctl.getAccountById("account_1", currentUser)
            Then("it queries scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { accountService.getAccountById("account_1", "user_1") }
            }
        }
    }

    Given("a real request to set the caller's own account nickname") {
        val accountService = mockk<AccountService>()
        val ctl = controller(accountService = accountService)
        val updated = account("account_1", "user_1").also { it.nickname = "My savings" }
        every { accountService.setNickname("user_1", "account_1", "My savings") } returns updated

        When("setting it") {
            val response = ctl.setNickname("account_1", SetAccountNicknameRequest("My savings"), currentUser)
            Then("it delegates scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { accountService.setNickname("user_1", "account_1", "My savings") }
                response.body?.get("account") shouldBe updated
            }
        }
    }

    Given("real transaction-history requests") {
        val accountService = mockk<AccountService>()
        val ctl = controller(accountService = accountService)
        every { accountService.getTransactionHistory("user_1") } returns emptyList()
        every { accountService.getAccountTransactionHistory("user_1", "account_1") } returns emptyList()
        every { accountService.getTransactionTimeline("user_1") } returns emptyList()

        When("fetching the caller's full history") {
            ctl.getTransactionHistory(currentUser)
            Then("it queries only by the caller's own userId") { verify(exactly = 1) { accountService.getTransactionHistory("user_1") } }
        }

        When("fetching one account's history") {
            ctl.getAccountTransactionHistory("account_1", currentUser)
            Then("it queries scoped to the caller's own userId") { verify(exactly = 1) { accountService.getAccountTransactionHistory("user_1", "account_1") } }
        }

        When("fetching the caller's timeline") {
            ctl.getTransactionTimeline(currentUser)
            Then("it queries only by the caller's own userId") { verify(exactly = 1) { accountService.getTransactionTimeline("user_1") } }
        }
    }

    Given("real spending/budget requests") {
        val spendingInsightService = mockk<SpendingInsightService>()
        val ctl = controller(spendingInsightService = spendingInsightService)
        val insight = mockk<SpendingInsightResult>(relaxed = true)
        val report = mockk<MonthlySpendingReport>(relaxed = true)
        every { spendingInsightService.getSpendingInsight("user_1") } returns insight
        every { spendingInsightService.getMonthlySpendingReport("user_1") } returns report
        every { spendingInsightService.getBusinessExpenseSummary("user_1", 3L) } returns insight
        every { spendingInsightService.getBudgets("user_1") } returns emptyList()
        val budget = mockk<SpendingBudget>(relaxed = true)
        every { spendingInsightService.setBudget("user_1", "food", BigDecimal("50000")) } returns budget

        When("fetching spending insight") {
            ctl.getSpendingInsight(currentUser)
            Then("it queries only by the caller's own userId") { verify(exactly = 1) { spendingInsightService.getSpendingInsight("user_1") } }
        }

        When("fetching the monthly report") {
            ctl.getMonthlySpendingReport(currentUser)
            Then("it queries only by the caller's own userId") { verify(exactly = 1) { spendingInsightService.getMonthlySpendingReport("user_1") } }
        }

        When("fetching the business expense summary") {
            ctl.getBusinessExpenseSummary(3L, currentUser)
            Then("it queries scoped to the caller's own userId") { verify(exactly = 1) { spendingInsightService.getBusinessExpenseSummary("user_1", 3L) } }
        }

        When("setting a budget") {
            ctl.setBudget(SetBudgetRequest("food", BigDecimal("50000")), currentUser)
            Then("it real-delegates scoped to the caller's own userId") { verify(exactly = 1) { spendingInsightService.setBudget("user_1", "food", BigDecimal("50000")) } }
        }

        When("fetching budgets") {
            ctl.getBudgets(currentUser)
            Then("it queries only by the caller's own userId") { verify(exactly = 1) { spendingInsightService.getBudgets("user_1") } }
        }
    }

    Given("a real subscriptions request") {
        val subscriptionDetectionService = mockk<SubscriptionDetectionService>()
        val ctl = controller(subscriptionDetectionService = subscriptionDetectionService)
        val result = mockk<SubscriptionSummary>(relaxed = true)
        every { subscriptionDetectionService.detectSubscriptions("user_1") } returns result

        When("fetching them") {
            ctl.getSubscriptions(currentUser)
            Then("it queries only by the caller's own userId") { verify(exactly = 1) { subscriptionDetectionService.detectSubscriptions("user_1") } }
        }
    }

    Given("real agent-withdrawal-authorization actions") {
        val idempotencyService = mockk<IdempotencyService>()
        val agentWithdrawalAuthorizationService = mockk<AgentWithdrawalAuthorizationService>()
        val ctl = controller(idempotencyService = idempotencyService, agentWithdrawalAuthorizationService = agentWithdrawalAuthorizationService)
        val authorization = mockk<rw.itunda.core.agents.AgentWithdrawalAuthorization>(relaxed = true)
        val createRequest = CreateAgentWithdrawalAuthorizationRequest(BigDecimal("50000"))
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every { agentWithdrawalAuthorizationService.create("user_1", BigDecimal("50000")) } returns authorization
        every {
            idempotencyService.replayOrExecute("POST /api/v1/account/agent-withdrawal-authorizations", "key-1", createRequest, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }
        every { agentWithdrawalAuthorizationService.cancel("user_1", "ABC123") } returns authorization
        every { agentWithdrawalAuthorizationService.list("user_1") } returns emptyList()

        When("creating one") {
            ctl.createAgentWithdrawalAuthorization(createRequest, "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/account/agent-withdrawal-authorizations", "key-1", createRequest, any()) }
                verify(exactly = 1) { agentWithdrawalAuthorizationService.create("user_1", BigDecimal("50000")) }
            }
        }

        When("cancelling one") {
            ctl.cancelAgentWithdrawalAuthorization(CancelAgentWithdrawalAuthorizationRequest("ABC123"), currentUser)
            Then("it real-delegates scoped to the caller's own userId") { verify(exactly = 1) { agentWithdrawalAuthorizationService.cancel("user_1", "ABC123") } }
        }

        When("listing them") {
            ctl.getAgentWithdrawalAuthorizations(currentUser)
            Then("it queries only by the caller's own userId") { verify(exactly = 1) { agentWithdrawalAuthorizationService.list("user_1") } }
        }
    }

    Given("a real transfer quote and confirmation") {
        val accountService = mockk<AccountService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(accountService = accountService, idempotencyService = idempotencyService)
        val quote = mockk<rw.itunda.account.TransferQuote>(relaxed = true)
        every { accountService.quoteTransfer("user_1", null, "+250788000001", BigDecimal("10000")) } returns quote
        val transaction = Transaction(
            id = "txn_1", referenceNumber = "REF1", senderId = "user_1", recipientId = "user_2",
            fromAccountId = "account_1", toAccountId = "account_2", amount = BigDecimal("10000"), fee = BigDecimal.ZERO,
            currency = "RWF", type = TransactionType.TRANSFER, status = TransactionStatus.COMPLETED,
            description = "Transfer", channel = "TRANSFER", completedAt = Instant.now(),
        )
        val confirmRequest = ConfirmTransferRequest("quote_1")
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every { accountService.confirmTransfer("quote_1", "user_1") } returns (transaction to BigDecimal("90000"))
        every {
            idempotencyService.replayOrExecute("POST /api/v1/account/transfer/confirm", "key-1", confirmRequest, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("quoting a transfer") {
            ctl.quoteTransfer(QuoteTransferRequest(BigDecimal("10000"), "+250788000001"), currentUser)
            Then("it real-delegates scoped to the caller's own userId") {
                verify(exactly = 1) { accountService.quoteTransfer("user_1", null, "+250788000001", BigDecimal("10000")) }
            }
        }

        When("confirming a transfer") {
            val response = ctl.confirmTransfer(confirmRequest, "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/account/transfer/confirm", "key-1", confirmRequest, any()) }
                verify(exactly = 1) { accountService.confirmTransfer("quote_1", "user_1") }
                response.body?.get("newBalance") shouldBe BigDecimal("90000")
            }
        }
    }

    listOf(
        Triple(IdempotencyConflictException("Conflict") as RuntimeException, HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT"),
        Triple(IdempotencyInProgressException("Conflict"), HttpStatus.CONFLICT, "IDEMPOTENT_REQUEST_PROCESSING"),
        Triple(AccountNotFoundException("Not found"), HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"),
        Triple(QuoteNotFoundException("Not found"), HttpStatus.NOT_FOUND, "QUOTE_NOT_FOUND"),
        Triple(QuoteExpiredException("Conflict"), HttpStatus.CONFLICT, "QUOTE_EXPIRED"),
        Triple(QuoteAlreadyUsedException("Conflict"), HttpStatus.CONFLICT, "QUOTE_ALREADY_USED"),
        Triple(InsufficientFundsException("Insufficient"), HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS"),
        Triple(AccountFrozenException("Frozen"), HttpStatus.FORBIDDEN, "ACCOUNT_FROZEN"),
        Triple(ProviderDeclinedException("Declined"), HttpStatus.BAD_GATEWAY, "PROVIDER_DECLINED"),
        Triple(WithdrawalAuthorizationInvalidException("Invalid"), HttpStatus.UNPROCESSABLE_ENTITY, "WITHDRAWAL_AUTHORIZATION_INVALID"),
        Triple(TooManyWithdrawalAuthorizationsException("Too many"), HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_WITHDRAWAL_AUTHORIZATIONS"),
        Triple(IllegalArgumentException("Bad request"), HttpStatus.BAD_REQUEST, "INVALID_REQUEST"),
        Triple(RateLimitExceededException("Too many requests"), HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED"),
    ).forEach { (exception, expectedStatus, expectedCode) ->
        Given("a real ${exception::class.simpleName}") {
            val ctl = controller()

            When("its exception handler maps it to a real HTTP response") {
                val response = when (exception) {
                    is IdempotencyConflictException -> ctl.handleIdempotencyConflict(exception)
                    is IdempotencyInProgressException -> ctl.handleIdempotencyInProgress(exception)
                    is AccountNotFoundException -> ctl.handleNotFound(exception)
                    is QuoteNotFoundException -> ctl.handleQuoteNotFound(exception)
                    is QuoteExpiredException -> ctl.handleQuoteExpired(exception)
                    is QuoteAlreadyUsedException -> ctl.handleQuoteAlreadyUsed(exception)
                    is InsufficientFundsException -> ctl.handleInsufficientFunds(exception)
                    is AccountFrozenException -> ctl.handleAccountFrozen(exception)
                    is ProviderDeclinedException -> ctl.handleProviderDeclined(exception)
                    is WithdrawalAuthorizationInvalidException -> ctl.handleWithdrawalAuthorization(exception)
                    is TooManyWithdrawalAuthorizationsException -> ctl.handleTooManyWithdrawalAuthorizations(exception)
                    is IllegalArgumentException -> ctl.handleBadRequest(exception)
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
