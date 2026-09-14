package rw.itunda.agents

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.core.agents.AgentWithdrawalAuthorizationService
import rw.itunda.auth.RateLimiter
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.domain.Agent
import rw.itunda.core.domain.AgentOperator
import rw.itunda.core.domain.AgentStatus
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.AgentCashInRepository
import rw.itunda.core.repository.AgentCashOutRepository
import rw.itunda.core.repository.AgentOperatorRepository
import rw.itunda.core.repository.AgentRepository
import rw.itunda.core.repository.AgentTillFundingRepository
import rw.itunda.core.repository.AgentTillReconciliationRepository
import rw.itunda.core.repository.LedgerAccountRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.util.Optional

class AgentOperatorAccessTest : BehaviorSpec({
    val agents = mockk<AgentRepository>()
    val operators = mockk<AgentOperatorRepository>()
    val service = AgentService(
        agents, operators, mockk<AgentTillReconciliationRepository>(), mockk<AgentTillFundingRepository>(), mockk<AgentCashInRepository>(),
        mockk<AgentCashOutRepository>(), mockk<AccountRepository>(), mockk<LedgerAccountRepository>(),
        mockk<LedgerService>(), mockk<TransactionRepository>(), mockk<UserRepository>(),
        mockk<AgentWithdrawalAuthorizationService>(), mockk<NotificationRepository>(), mockk<PushNotificationService>(relaxed = true), mockk<FraudRuleEngine>(relaxed = true), mockk<RateLimiter>(relaxed = true),
    )
    val operator = AgentOperator("operator_1", "agent_1", "user_1")

    Given("an active operator assigned to a suspended cash location") {
        every { operators.findByUserId("user_1") } returns operator
        every { agents.findById("agent_1") } returns Optional.of(
            Agent("agent_1", "Kigali Store", "cash_1", AgentStatus.SUSPENDED, BigDecimal("100000")),
        )

        Then("the operator session is blocked before any store operation") {
            shouldThrow<AgentSuspendedException> { service.getMyOperator("user_1") }
        }
    }

    Given("an active operator assigned to an active cash location") {
        every { operators.findByUserId("user_1") } returns operator
        every { agents.findById("agent_1") } returns Optional.of(
            Agent("agent_1", "Kigali Store", "cash_1", AgentStatus.ACTIVE, BigDecimal("100000")),
        )

        Then("the operator retains access") {
            service.getMyOperator("user_1") shouldBe operator
        }
    }

    // Real gap found live (uncalled-endpoint sweep, 2026-08-16) -- see
    // AgentService.setLocationForOperator's own doc comment: the only prior way to set
    // Agent.latitude/longitude lived on a deprecated admin controller with zero caller,
    // while a real customer-facing "nearby agents" feature already depends on it.
    Given("a real operator reporting their store's real location for the first time") {
        every { operators.findByUserId("user_1") } returns operator
        every { agents.findById("agent_1") } returns Optional.of(
            Agent("agent_1", "Kigali Store", "cash_1", AgentStatus.ACTIVE, BigDecimal("100000")),
        )
        every { agents.save(any()) } answers { firstArg() }

        Then("it real-updates the agent's own real coordinates, resolved from the caller's JWT, not a caller-supplied agent id") {
            val updated = service.setLocationForOperator("user_1", -1.9536, 30.0605)
            updated.latitude shouldBe -1.9536
            updated.longitude shouldBe 30.0605
        }
    }

    Given("a real operator reporting a location outside Rwanda") {
        every { operators.findByUserId("user_1") } returns operator
        every { agents.findById("agent_1") } returns Optional.of(
            Agent("agent_1", "Kigali Store", "cash_1", AgentStatus.ACTIVE, BigDecimal("100000")),
        )

        Then("it throws IllegalArgumentException before ever saving") {
            shouldThrow<IllegalArgumentException> { service.setLocationForOperator("user_1", 51.5072, -0.1276) }
        }
    }

    // Real zero-test-coverage gap found live (2026-09-14, test-coverage sweep): these
    // are the actual live production entry points AgentOperatorController's real
    // cash-in/cash-out routes call -- unlike setLocationForOperator above (already
    // tested), cashInForOperator/cashOutForOperator had never been exercised at all,
    // even though the operator-resolution step they add is exactly the kind of "resolve
    // identity from the JWT, not a caller-supplied id" logic this file's own existing
    // tests already treat as worth its own coverage.
    Given("an operator whose cash location is suspended, trying to accept cash-in/out") {
        every { operators.findByUserId("user_1") } returns operator
        every { agents.findById("agent_1") } returns Optional.of(
            Agent("agent_1", "Kigali Store", "cash_1", AgentStatus.SUSPENDED, BigDecimal("100000")),
        )

        Then("cashInForOperator is blocked before ever touching cash-movement logic") {
            shouldThrow<AgentSuspendedException> { service.cashInForOperator("user_1", "1000000001", BigDecimal("5000"), "RCPT-1") }
        }
        Then("cashOutForOperator is blocked before ever touching cash-movement logic") {
            shouldThrow<AgentSuspendedException> { service.cashOutForOperator("user_1", "1000000001", BigDecimal("5000"), "RCPT-2", "AUTH-1") }
        }
    }

    Given("a real active operator accepting cash-in through the real operator-facing route") {
        val agentRepository = mockk<AgentRepository>()
        val agentOperatorRepository = mockk<AgentOperatorRepository>()
        val agentCashInRepository = mockk<AgentCashInRepository>(relaxed = true)
        val agentCashOutRepository = mockk<AgentCashOutRepository>(relaxed = true)
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val service2 = AgentService(
            agentRepository, agentOperatorRepository, mockk<AgentTillReconciliationRepository>(), mockk<AgentTillFundingRepository>(),
            agentCashInRepository, agentCashOutRepository, accountRepository, mockk<LedgerAccountRepository>(),
            ledgerService, transactionRepository, mockk<UserRepository>(), mockk<AgentWithdrawalAuthorizationService>(),
            notificationRepository, mockk<PushNotificationService>(relaxed = true), mockk<FraudRuleEngine>(relaxed = true), mockk<RateLimiter>(relaxed = true),
        )
        val op = AgentOperator("operator_2", "agent_2", "user_2")
        every { agentOperatorRepository.findByUserId("user_2") } returns op
        val agent2 = Agent("agent_2", "Nyamirambo Branch", "agent_cash_2", AgentStatus.ACTIVE, BigDecimal("100000"))
        every { agentRepository.findById("agent_2") } returns Optional.of(agent2)
        every { agentRepository.findByIdForUpdate("agent_2") } returns Optional.of(agent2)
        every { agentCashInRepository.existsByReceiptNumber("RCPT-3") } returns false
        every { agentCashOutRepository.existsByReceiptNumber("RCPT-3") } returns false
        every { agentCashInRepository.sumAmountByAgentIdBetween(any(), any(), any()) } returns BigDecimal.ZERO
        every { agentCashInRepository.save(any()) } answers { firstArg() }
        every { agentRepository.save(any()) } answers { firstArg() }
        every { transactionRepository.save(any()) } answers { firstArg() }
        val customerAccount = rw.itunda.core.domain.Account(
            id = "account_customer", userId = "user_customer", accountNumber = "1000000009", accountName = "Customer",
            type = rw.itunda.core.domain.AccountType.MAIN, balance = BigDecimal("1000"), availableBalance = BigDecimal("1000"),
        )
        every { accountRepository.findByAccountNumber("1000000009") } returns customerAccount
        every { accountRepository.findByUserIdAndType("user_2", rw.itunda.core.domain.AccountType.MAIN) } returns null
        every { ledgerService.postLedgerTransaction(any(), any()) } returns rw.itunda.core.ledger.LedgerPostResult("ledgertxn_op_cashin", emptyList())

        When("they accept a real 5,000 RWF cash-in") {
            val result = service2.cashInForOperator("user_2", "1000000009", BigDecimal("5000"), "RCPT-3")

            Then("it real-resolves to the operator's OWN agent, not a caller-supplied one -- the ledger transaction actually posts") {
                io.mockk.verify(exactly = 1) { ledgerService.postLedgerTransaction(any(), any()) }
                result["operatorCommission"] shouldBe BigDecimal.ZERO
            }
        }
    }
})
