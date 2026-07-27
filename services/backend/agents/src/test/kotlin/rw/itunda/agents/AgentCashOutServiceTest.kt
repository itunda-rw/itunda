package rw.itunda.agents

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.bigdecimal.shouldBeEqualIgnoringScale
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.Agent
import rw.itunda.core.domain.AgentStatus
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerAccount
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.repository.AgentCashInRepository
import rw.itunda.core.repository.AgentCashOutRepository
import rw.itunda.core.repository.AgentRepository
import rw.itunda.core.repository.AgentOperatorRepository
import rw.itunda.core.repository.AgentTillReconciliationRepository
import rw.itunda.core.repository.LedgerAccountRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.agents.AgentWithdrawalAuthorizationService
import java.math.BigDecimal
import java.util.Optional

class AgentCashOutServiceTest : BehaviorSpec({
    Given("an active agent and a funded Itunda main account") {
        val agentRepository = mockk<AgentRepository>()
        val cashInRepository = mockk<AgentCashInRepository>()
        val cashOutRepository = mockk<AgentCashOutRepository>()
        val operatorRepository = mockk<AgentOperatorRepository>()
        val tillReconciliationRepository = mockk<AgentTillReconciliationRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerAccountRepository = mockk<LedgerAccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>()
        val userRepository = mockk<UserRepository>()
        val withdrawalAuthorizationService = mockk<AgentWithdrawalAuthorizationService>()
        val notificationRepository = mockk<NotificationRepository>()
        val service = AgentService(agentRepository, operatorRepository, tillReconciliationRepository, cashInRepository, cashOutRepository, walletRepository, ledgerAccountRepository, ledgerService, transactionRepository, userRepository, withdrawalAuthorizationService, notificationRepository)
        val agent = Agent("agent_1", "Kigali Central", "agent_cash_1", AgentStatus.ACTIVE, BigDecimal("100000"), BigDecimal("80000"))
        val wallet = Wallet("wallet_1", "user_1", "2024100001", "Jean Main", WalletType.MAIN, BigDecimal("50000"), BigDecimal("50000"))
        every { cashOutRepository.existsByReceiptNumber("KGL-W-001") } returns false
        every { cashInRepository.existsByReceiptNumber("KGL-W-001") } returns false
        every { agentRepository.findByIdForUpdate(agent.id) } returns Optional.of(agent)
        every { cashOutRepository.sumAmountByAgentIdBetween(any(), any(), any()) } returns BigDecimal.ZERO
        every { walletRepository.findByAccountNumber(wallet.accountNumber) } returns wallet
        // Real agent commission (2026-07-27) -- see AgentCommissionSchedule's own doc
        // comment. This existing test's own operator ("admin_1") has no real wallet
        // stubbed here, so commission is honestly skipped -- the pre-existing 2-leg
        // assertion below stays correct unchanged.
        every { walletRepository.findByUserIdAndType("admin_1", WalletType.MAIN) } returns null
        every { withdrawalAuthorizationService.consume("AUTH001", wallet.id, BigDecimal("25000")) } returns mockk()
        every { ledgerAccountRepository.findByIdForUpdate(agent.cashAccountId) } returns Optional.of(LedgerAccount(agent.cashAccountId, "Agent cash", BigDecimal("-30000")))
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { cashOutRepository.save(any()) } answers { firstArg() }
        every { transactionRepository.save(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }

        When("the operator pays out cash") {
            service.cashOut(agent.id, wallet.accountNumber, BigDecimal("25000"), "KGL-W-001", "AUTH001", "admin_1")

            Then("the wallet is debited and that agent's cash account is credited") {
                val legs = slot<List<LedgerLeg>>()
                verify(exactly = 1) { ledgerService.postLedgerTransaction("RWF", capture(legs)) }
                legs.captured.size shouldBe 2
                legs.captured[0].accountId shouldBe wallet.id
                legs.captured[0].accountType shouldBe LedgerAccountType.WALLET
                legs.captured[0].direction shouldBe LedgerDirection.DEBIT
                legs.captured[0].amount shouldBeEqualIgnoringScale BigDecimal("25000")
                legs.captured[1].accountId shouldBe agent.cashAccountId
                legs.captured[1].accountType shouldBe LedgerAccountType.AGENT_CASH
                legs.captured[1].direction shouldBe LedgerDirection.CREDIT
                legs.captured[1].amount shouldBeEqualIgnoringScale BigDecimal("25000")
            }
        }
    }
})
