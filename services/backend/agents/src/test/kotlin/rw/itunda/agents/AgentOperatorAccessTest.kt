package rw.itunda.agents

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.core.agents.AgentWithdrawalAuthorizationService
import rw.itunda.core.domain.Agent
import rw.itunda.core.domain.AgentOperator
import rw.itunda.core.domain.AgentStatus
import rw.itunda.core.ledger.LedgerService
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
        mockk<AgentWithdrawalAuthorizationService>(), mockk<NotificationRepository>(),
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
})
