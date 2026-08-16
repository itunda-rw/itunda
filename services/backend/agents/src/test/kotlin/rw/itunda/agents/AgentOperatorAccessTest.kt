package rw.itunda.agents

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.core.agents.AgentWithdrawalAuthorizationService
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
import rw.itunda.core.repository.AgentTillReconciliationRepository
import rw.itunda.core.repository.LedgerAccountRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.util.Optional

class AgentOperatorAccessTest : BehaviorSpec({
    val agents = mockk<AgentRepository>()
    val operators = mockk<AgentOperatorRepository>()
    val service = AgentService(
        agents, operators, mockk<AgentTillReconciliationRepository>(), mockk<AgentCashInRepository>(),
        mockk<AgentCashOutRepository>(), mockk<WalletRepository>(), mockk<LedgerAccountRepository>(),
        mockk<LedgerService>(), mockk<TransactionRepository>(), mockk<UserRepository>(),
        mockk<AgentWithdrawalAuthorizationService>(), mockk<NotificationRepository>(), mockk<PushNotificationService>(relaxed = true), mockk<FraudRuleEngine>(relaxed = true),
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
})
