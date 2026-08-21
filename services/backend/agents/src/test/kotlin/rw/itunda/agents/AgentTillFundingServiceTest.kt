package rw.itunda.agents

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.bigdecimal.shouldBeEqualIgnoringScale
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.agents.AgentWithdrawalAuthorizationService
import rw.itunda.core.fraud.FraudRuleEngine
import rw.itunda.core.domain.Agent
import rw.itunda.core.domain.AgentStatus
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.*
import java.math.BigDecimal
import java.util.Optional

class AgentTillFundingServiceTest : BehaviorSpec({
    Given("an active store") {
        val agents = mockk<AgentRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = AgentService(
            agents, mockk<AgentOperatorRepository>(), mockk<AgentTillReconciliationRepository>(),
            mockk<AgentCashInRepository>(), mockk<AgentCashOutRepository>(), mockk<AccountRepository>(),
            mockk<LedgerAccountRepository>(), ledgerService, mockk<TransactionRepository>(), mockk<UserRepository>(),
            mockk<AgentWithdrawalAuthorizationService>(), mockk<NotificationRepository>(), mockk<PushNotificationService>(relaxed = true), mockk<FraudRuleEngine>(relaxed = true),
        )
        val agent = Agent("agent_1", "Kigali Central", "agent_cash_1", AgentStatus.ACTIVE, BigDecimal("100000"))
        every { agents.findByIdForUpdate(agent.id) } returns Optional.of(agent)
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_float", emptyList())

        When("operations fund its till") {
            service.fundTill(agent.id, BigDecimal("50000"), "VAULT-001")

            Then("the agent cash asset is debited and the central vault is credited") {
                val legs = slot<List<LedgerLeg>>()
                verify { ledgerService.postLedgerTransaction("RWF", capture(legs)) }
                legs.captured[0].accountType shouldBe LedgerAccountType.AGENT_CASH
                legs.captured[0].direction shouldBe LedgerDirection.DEBIT
                legs.captured[0].amount shouldBeEqualIgnoringScale BigDecimal("50000")
                legs.captured[1].accountId shouldBe "cash_vault"
                legs.captured[1].accountType shouldBe LedgerAccountType.CASH_VAULT
                legs.captured[1].direction shouldBe LedgerDirection.CREDIT
            }
        }
    }
})
